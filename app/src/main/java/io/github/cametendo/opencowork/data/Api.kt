package io.github.cametendo.opencowork.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(message: String, val status: Int = 0) : Exception(message)

@OptIn(ExperimentalSerializationApi::class)
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/** A file to upload: name, size (or -1) and a way to open its bytes. */
class UploadSource(val name: String, val size: Long, val mime: String?, val open: () -> InputStream)

/**
 * Thin client for the Open Cowork REST API (the same endpoints the web UI uses).
 * [baseUrl] and [token] are swapped at runtime when the user signs in or out.
 */
class Api(baseClient: OkHttpClient = OkHttpClient()) {
    @Volatile var baseUrl: String? = null
    @Volatile var token: String? = null
    @Volatile var onUnauthorized: (() -> Unit)? = null

    private val client = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** For SSE: models can think for a long time before the first token arrives. */
    private val streamClient = client.newBuilder()
        .readTimeout(15, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------------ plumbing

    fun url(path: String, query: Map<String, Any?> = emptyMap(), base: String? = baseUrl): HttpUrl {
        val root = base ?: throw ApiException("No server configured")
        val parsed = (root.trimEnd('/') + path).toHttpUrlOrNull() ?: throw ApiException("Invalid server address: $root")
        val b = parsed.newBuilder()
        for ((k, v) in query) {
            if (v == null || v == "" || v == false) continue
            b.addQueryParameter(k, v.toString())
        }
        return b.build()
    }

    private fun request(method: String, url: HttpUrl, body: RequestBody? = null, auth: Boolean = true): Request {
        val b = Request.Builder().url(url).header("Accept", "application/json")
        if (auth) token?.let { b.header("Authorization", "Bearer $it") }
        val reqBody = body ?: if (method in setOf("POST", "PUT", "PATCH")) "{}".toRequestBody(jsonType) else null
        return b.method(method, reqBody).build()
    }

    private fun jsonBody(body: JsonElement?): RequestBody? = body?.toString()?.toRequestBody(jsonType)

    internal fun errorFrom(resp: Response): ApiException {
        val text = runCatching { resp.body?.string() }.getOrNull().orEmpty()
        var message = "${resp.code} ${resp.message}".trim()
        runCatching {
            val detail = ApiJson.parseToJsonElement(text).jsonObject["detail"]
            when (detail) {
                is JsonPrimitive -> message = detail.content
                is JsonArray -> message = detail.joinToString("; ") { d ->
                    (d as? JsonObject)?.str("msg") ?: d.toString()
                }
                else -> {}
            }
        }
        if (resp.code == 401 && token != null) onUnauthorized?.invoke()
        if (resp.code == 0 || message.isBlank()) message = "Request failed"
        return ApiException(message, resp.code)
    }

    suspend fun raw(method: String, path: String, body: JsonElement? = null, query: Map<String, Any?> = emptyMap(), auth: Boolean = true, base: String? = baseUrl): JsonElement {
        val req = request(method, url(path, query, base), jsonBody(body), auth)
        val resp = try {
            client.newCall(req).await()
        } catch (e: IOException) {
            throw ApiException(networkMessage(e))
        }
        resp.use {
            if (!it.isSuccessful) throw errorFrom(it)
            val text = it.body?.string().orEmpty()
            return if (text.isBlank()) JsonObject(emptyMap()) else ApiJson.parseToJsonElement(text)
        }
    }

    suspend inline fun <reified T> call(method: String, path: String, body: JsonElement? = null, query: Map<String, Any?> = emptyMap()): T =
        ApiJson.decodeFromJsonElement(raw(method, path, body, query))

    private fun networkMessage(e: IOException): String = when (e) {
        is java.net.UnknownHostException -> "Server not found. Check the address."
        is java.net.ConnectException -> "Can't connect to the server. Is it running and reachable?"
        is java.net.SocketTimeoutException -> "The server took too long to respond."
        is javax.net.ssl.SSLException -> "Secure connection failed: ${e.message ?: "TLS error"}. Try turning HTTPS off for plain-HTTP servers."
        else -> e.message ?: "Network error"
    }

    /**
     * Stream a server-sent-events endpoint, calling [onEvent] for every JSON `data:` payload.
     * Cancelling the coroutine closes the connection.
     */
    suspend fun stream(method: String, path: String, body: JsonElement? = null, query: Map<String, Any?> = emptyMap(), onEvent: suspend (JsonObject) -> Unit) =
        withContext(Dispatchers.IO) {
            val req = request(method, url(path, query), jsonBody(body)).newBuilder().header("Accept", "text/event-stream").build()
            val call = streamClient.newCall(req)
            // Cancelling this coroutine must unblock the read below, so a sibling closes the call.
            val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
            try {
                val resp = try {
                    call.execute()
                } catch (e: IOException) {
                    ensureActive()
                    throw ApiException(networkMessage(e))
                }
                resp.use {
                    if (!it.isSuccessful) throw errorFrom(it)
                    val source = it.body?.source() ?: return@use
                    val parser = SseParser()
                    while (true) {
                        ensureActive()
                        val line = try {
                            source.readUtf8Line()
                        } catch (e: IOException) {
                            ensureActive()
                            throw ApiException(networkMessage(e))
                        } ?: break
                        val data = parser.feed(line) ?: continue
                        val obj = runCatching { ApiJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                        onEvent(obj)
                    }
                    parser.flush()?.let { data ->
                        runCatching { ApiJson.parseToJsonElement(data).jsonObject }.getOrNull()?.let { obj -> onEvent(obj) }
                    }
                }
            } finally {
                watcher.cancel()
            }
        }

    // ------------------------------------------------------------------ public / auth

    suspend fun config(base: String? = baseUrl): PublicConfig = ApiJson.decodeFromJsonElement(raw("GET", "/api/config", auth = false, base = base))

    suspend fun signin(base: String, email: String, password: String): AuthSession = ApiJson.decodeFromJsonElement(
        raw("POST", "/api/auths/signin", buildJsonObject { put("email", email); put("password", password) }, auth = false, base = base),
    )

    suspend fun signup(base: String, name: String, email: String, password: String): AuthSession = ApiJson.decodeFromJsonElement(
        raw("POST", "/api/auths/signup", buildJsonObject { put("name", name); put("email", email); put("password", password) }, auth = false, base = base),
    )

    suspend fun me(): User = call("GET", "/api/auths/me")
    suspend fun updateProfile(name: String): User = call("PUT", "/api/auths/me/profile", buildJsonObject { put("name", name) })
    suspend fun updatePassword(current: String, new: String) {
        raw("PUT", "/api/auths/me/password", buildJsonObject { put("password", current); put("new_password", new) })
    }
    suspend fun updateSettings(patch: JsonObject): UserSettings = call("PUT", "/api/auths/me/settings", patch)

    // ------------------------------------------------------------------ admin

    suspend fun users(): List<User> = call("GET", "/api/users")
    suspend fun createUser(name: String, email: String, password: String, role: String): User =
        call("POST", "/api/users", buildJsonObject { put("name", name); put("email", email); put("password", password); put("role", role) })
    suspend fun updateUser(id: String, patch: JsonObject): User = call("PATCH", "/api/users/$id", patch)
    suspend fun deleteUser(id: String) { raw("DELETE", "/api/users/$id") }
    suspend fun adminConfig(): AdminConfig = call("GET", "/api/admin/config")
    suspend fun updateAdminConfig(patch: JsonObject): AdminConfig = call("PUT", "/api/admin/config", patch)
    suspend fun connections(): List<Connection> = call("GET", "/api/admin/connections")
    suspend fun createConnection(body: JsonObject): Connection = call("POST", "/api/admin/connections", body)
    suspend fun updateConnection(id: String, body: JsonObject): Connection = call("PATCH", "/api/admin/connections/$id", body)
    suspend fun deleteConnection(id: String) { raw("DELETE", "/api/admin/connections/$id") }
    suspend fun verifyConnection(type: String, baseUrl: String, apiKey: String?, connectionId: String?): VerifyResult =
        call("POST", "/api/admin/connections/verify", buildJsonObject {
            put("type", type); put("base_url", baseUrl)
            if (!apiKey.isNullOrBlank()) put("api_key", apiKey)
            if (connectionId != null) put("connection_id", connectionId)
        })

    // ------------------------------------------------------------------ models & chats

    suspend fun models(refresh: Boolean = false): List<Model> = call<ModelList>("GET", "/api/models", query = mapOf("refresh" to refresh)).data
    suspend fun chats(search: String? = null): List<ChatSummary> = call("GET", "/api/chats", query = mapOf("q" to search))
    suspend fun chat(id: String): Chat = call("GET", "/api/chats/$id")
    suspend fun createChat(model: String?, params: ChatParams? = null): Chat = call("POST", "/api/chats", buildJsonObject {
        if (model != null) put("model", model)
        if (params != null) put("params", ApiJson.encodeToJsonElement(ChatParams.serializer(), params))
    })
    suspend fun updateChat(id: String, patch: JsonObject): Chat = call("PATCH", "/api/chats/$id", patch)
    suspend fun deleteChat(id: String) { raw("DELETE", "/api/chats/$id") }
    suspend fun deleteAllChats() { raw("DELETE", "/api/chats") }

    // ------------------------------------------------------------------ cowork

    suspend fun coworkSessions(): List<CoworkSummary> = call("GET", "/api/cowork/sessions")
    suspend fun coworkSession(id: String): CoworkSession = call("GET", "/api/cowork/sessions/$id")
    suspend fun createCoworkSession(prompt: String, model: String, approvalMode: String, repoId: String?, createBranch: Boolean): CoworkSession =
        call("POST", "/api/cowork/sessions", buildJsonObject {
            put("prompt", prompt); put("model", model); put("approval_mode", approvalMode)
            put("workspace", buildJsonObject {
                if (repoId != null) {
                    put("type", "repo"); put("repo_id", repoId); put("create_branch", createBranch)
                } else put("type", "scratch")
            })
        })
    suspend fun updateCoworkSession(id: String, patch: JsonObject): CoworkSummary = call("PATCH", "/api/cowork/sessions/$id", patch)
    suspend fun deleteCoworkSession(id: String, deleteFiles: Boolean) {
        raw("DELETE", "/api/cowork/sessions/$id", query = mapOf("delete_files" to deleteFiles))
    }
    suspend fun coworkMessage(id: String, content: String, model: String?): CoworkPostResult =
        call("POST", "/api/cowork/sessions/$id/messages", buildJsonObject { put("content", content); if (model != null) put("model", model) })
    suspend fun coworkCancel(id: String) { raw("POST", "/api/cowork/sessions/$id/cancel") }
    suspend fun coworkApprove(id: String, toolCallId: String, approved: Boolean, always: Boolean) {
        raw("POST", "/api/cowork/sessions/$id/approve", buildJsonObject {
            put("tool_call_id", toolCallId); put("approved", approved); put("always", always)
        })
    }
    suspend fun coworkFiles(id: String, path: String = "."): DirListing = call("GET", "/api/cowork/sessions/$id/files", query = mapOf("path" to path))
    suspend fun coworkFile(id: String, path: String): FilePreview = call("GET", "/api/cowork/sessions/$id/file", query = mapOf("path" to path))

    /** Download a workspace file and write it to [sink]. */
    suspend fun coworkDownload(id: String, path: String, sink: (InputStream) -> Unit) = withContext(Dispatchers.IO) {
        val req = request("GET", url("/api/cowork/sessions/$id/download", mapOf("path" to path)))
        val resp = try { client.newCall(req).await() } catch (e: IOException) { throw ApiException(networkMessage(e)) }
        resp.use {
            if (!it.isSuccessful) throw errorFrom(it)
            it.body?.byteStream()?.use(sink)
        }
    }

    suspend fun coworkUpload(id: String, files: List<UploadSource>, path: String = "."): UploadResult = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
        for (f in files) {
            val type = (f.mime ?: "application/octet-stream").toMediaType()
            val body = object : RequestBody() {
                override fun contentType() = type
                override fun contentLength() = f.size
                override fun writeTo(sink: BufferedSink) { f.open().source().use { sink.writeAll(it) } }
            }
            form.addFormDataPart("files", f.name, body)
        }
        form.addFormDataPart("path", path)
        val req = request("POST", url("/api/cowork/sessions/$id/upload"), form.build())
        val resp = try { client.newCall(req).await() } catch (e: IOException) { throw ApiException(networkMessage(e)) }
        resp.use {
            if (!it.isSuccessful) throw errorFrom(it)
            ApiJson.decodeFromString(UploadResult.serializer(), it.body?.string().orEmpty().ifBlank { "{}" })
        }
    }

    // ------------------------------------------------------------------ github & repos

    suspend fun githubStatus(): GitHubStatus = call("GET", "/api/github/status")
    suspend fun githubConnect(token: String): GitHubStatus = call("PUT", "/api/github/token", buildJsonObject { put("token", token) })
    suspend fun githubDisconnect() { raw("DELETE", "/api/github/token") }
    suspend fun githubRepos(search: String? = null): List<RemoteRepo> = call("GET", "/api/github/repos", query = mapOf("q" to search))
    suspend fun repos(): List<LocalRepo> = call("GET", "/api/repos")
    suspend fun cloneRepo(repo: String, branch: String? = null): LocalRepo =
        call("POST", "/api/repos/clone", buildJsonObject { put("repo", repo); if (!branch.isNullOrBlank()) put("branch", branch) })
    suspend fun removeRepo(id: String, deleteFiles: Boolean) { raw("DELETE", "/api/repos/$id", query = mapOf("delete_files" to deleteFiles)) }
    suspend fun repoStatus(id: String): GitStatus = call("GET", "/api/repos/$id/status")
    suspend fun repoChanges(id: String): GitChanges = call("GET", "/api/repos/$id/changes")
    suspend fun repoBranches(id: String): Branches = call("GET", "/api/repos/$id/branches")
    suspend fun repoCheckout(id: String, branch: String, create: Boolean): GitStatus =
        call("POST", "/api/repos/$id/checkout", buildJsonObject { put("branch", branch); put("create", create) })
    suspend fun repoPull(id: String): GitMessageResult = call("POST", "/api/repos/$id/pull")
    suspend fun repoCommit(id: String, message: String): CommitResult = call("POST", "/api/repos/$id/commit", buildJsonObject { put("message", message) })
    suspend fun repoPush(id: String): GitMessageResult = call("POST", "/api/repos/$id/push")
    suspend fun repoPullRequest(id: String): PullRequestResult = call("GET", "/api/repos/$id/pull-request")
    suspend fun createPullRequest(id: String, title: String, body: String, base: String?, draft: Boolean): PullRequestResult =
        call("POST", "/api/repos/$id/pull-request", buildJsonObject {
            put("title", title); put("body", body); if (!base.isNullOrBlank()) put("base", base); put("draft", draft)
        })
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isCancelled) response.close() else cont.resume(response)
        }
    })
}

/** True when the throwable just means the coroutine was cancelled (user pressed stop, screen closed…). */
fun Throwable.isCancellation() = this is CancellationException
