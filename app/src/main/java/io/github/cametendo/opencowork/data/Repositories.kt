package io.github.cametendo.opencowork.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.UUID
import kotlin.math.min

private fun now() = System.currentTimeMillis() / 1000.0
private fun localId() = "local-" + UUID.randomUUID().toString()

/** Hooks the repositories use to reach the platform (notifications, app visibility). */
interface Platform {
    val inForeground: Boolean
    fun notifyReplyReady(chatId: String, title: String, preview: String)
    fun notifyApproval(sessionId: String, title: String, call: ToolCall?)
    fun cancelApproval(sessionId: String)
    fun notifyTaskEnded(sessionId: String, title: String, status: String, error: String?)
}

// =============================================================================== auth

sealed interface AuthState {
    data object Loading : AuthState
    data class SignedOut(val message: String? = null) : AuthState
    data class SignedIn(val user: User, val config: PublicConfig?, val baseUrl: String) : AuthState
}

class AuthRepository(private val api: Api, private val store: SessionStore, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val user: User? get() = (state.value as? AuthState.SignedIn)?.user

    init {
        api.onUnauthorized = { scope.launch { signOut("Your session has expired. Please sign in again.") } }
    }

    suspend fun restore() {
        val s = store.current()
        if (!s.signedIn) {
            _state.value = AuthState.SignedOut()
            return
        }
        api.baseUrl = s.baseUrl
        api.token = s.token
        val cached = s.user
        if (cached != null) _state.value = AuthState.SignedIn(cached, null, s.baseUrl!!)
        refresh(initial = cached == null)
    }

    /** Reload the user (role changes, settings) and the server's public config. */
    suspend fun refresh(initial: Boolean = false) {
        val base = api.baseUrl ?: return
        try {
            val me = api.me()
            val config = runCatching { api.config() }.getOrNull()
            store.saveUser(me)
            _state.value = AuthState.SignedIn(me, config ?: (state.value as? AuthState.SignedIn)?.config, base)
        } catch (e: ApiException) {
            if (e.status == 401) signOut("Your session has expired. Please sign in again.")
            else if (initial) _state.value = AuthState.SignedOut(e.message)
        }
    }

    suspend fun login(form: ServerForm, email: String, password: String, name: String?, signup: Boolean): String? {
        val base = try {
            ServerAddress.build(form.mode, form.host, form.port, form.https)
        } catch (e: ServerAddress.InvalidAddress) {
            return e.message
        }
        return try {
            val config = api.config(base)
            val session = if (signup) api.signup(base, name.orEmpty().trim(), email.trim(), password)
            else api.signin(base, email.trim(), password)
            store.saveLogin(base, session.token, email.trim(), form, session.user)
            api.baseUrl = base
            api.token = session.token
            _state.value = AuthState.SignedIn(session.user, config, base)
            null
        } catch (e: ApiException) {
            if (e.status == 404) "No Open Cowork server found at $base" else e.message
        } catch (e: Exception) {
            e.message ?: "Sign-in failed"
        }
    }

    suspend fun probe(form: ServerForm): Result<Pair<String, PublicConfig>> = runCatching {
        val base = ServerAddress.build(form.mode, form.host, form.port, form.https)
        base to api.config(base)
    }

    suspend fun signOut(message: String? = null) {
        store.clearToken()
        api.token = null
        _state.value = AuthState.SignedOut(message)
    }

    fun updateUser(transform: (User) -> User) {
        val cur = state.value as? AuthState.SignedIn ?: return
        val next = transform(cur.user)
        _state.value = cur.copy(user = next)
        scope.launch { store.saveUser(next) }
    }

    suspend fun saveSettings(patch: JsonObject) {
        val merged = api.updateSettings(patch)
        updateUser { it.copy(settings = merged) }
    }
}

// =============================================================================== models

class ModelRepository(private val api: Api) {
    private val _models = MutableStateFlow<List<Model>>(emptyList())
    val models: StateFlow<List<Model>> = _models.asStateFlow()
    val loaded = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    suspend fun load(refresh: Boolean = false) {
        try {
            _models.value = api.models(refresh)
            error.value = null
        } catch (e: ApiException) {
            error.value = e.message
        } finally {
            loaded.value = true
        }
    }

    fun clear() {
        _models.value = emptyList()
        loaded.value = false
    }
}

// =============================================================================== chats

class ChatRepository(private val api: Api, private val scope: CoroutineScope, private val platform: Platform) {
    val list = MutableStateFlow<List<ChatSummary>>(emptyList())
    val listLoaded = MutableStateFlow(false)
    val chats = MutableStateFlow<Map<String, Chat>>(emptyMap())
    val streaming = MutableStateFlow<Set<String>>(emptySet())
    private val jobs = HashMap<String, Job>()

    suspend fun loadList() {
        list.value = api.chats()
        listLoaded.value = true
    }

    suspend fun load(id: String): Chat {
        val chat = api.chat(id)
        if (id !in streaming.value) chats.update { it + (id to chat) }
        return chats.value[id] ?: chat
    }

    suspend fun create(model: String?, params: ChatParams? = null): Chat {
        val chat = api.createChat(model, params)
        chats.update { it + (chat.id to chat) }
        upsertSummary(chat.summary())
        return chat
    }

    private fun update(id: String, fn: (Chat) -> Chat) = chats.update { m -> m[id]?.let { m + (id to fn(it)) } ?: m }

    private fun updateMessage(id: String, msgId: String, fn: (ChatMessage) -> ChatMessage) =
        update(id) { c -> c.copy(messages = c.messages.map { if (it.id == msgId) fn(it) else it }) }

    fun upsertSummary(s: ChatSummary) = list.update { l ->
        val rest = l.filter { it.id != s.id }
        (listOf(s) + rest).sortedByDescending { it.updatedAt }
    }

    private fun patchSummary(id: String, fn: (ChatSummary) -> ChatSummary) =
        list.update { l -> l.map { if (it.id == id) fn(it) else it }.sortedByDescending { it.updatedAt } }

    fun isStreaming(id: String) = id in streaming.value

    fun send(chatId: String, model: String, content: String? = null, images: List<String>? = null, regenerate: Boolean = false, editMessageId: String? = null) {
        val chat = chats.value[chatId] ?: return
        if (chatId in streaming.value) return
        val ts = now()
        var messages = chat.messages
        when {
            regenerate -> messages = messages.dropLastWhile { it.role == "assistant" }
            editMessageId != null -> {
                val idx = messages.indexOfFirst { it.id == editMessageId }
                if (idx >= 0) messages = messages.take(idx) + messages[idx].copy(content = content.orEmpty())
            }
            else -> messages = messages + ChatMessage(localId(), "user", content.orEmpty(), images = images, createdAt = ts)
        }
        var assistantId = localId()
        messages = messages + ChatMessage(assistantId, "assistant", "", model = model, createdAt = ts)
        update(chatId) { it.copy(messages = messages, model = model, updatedAt = ts) }
        streaming.update { it + chatId }
        if (list.value.none { it.id == chatId }) upsertSummary(chat.summary().copy(updatedAt = ts, model = model))
        else patchSummary(chatId) { it.copy(updatedAt = ts, model = model) }

        val body = buildJsonObject {
            put("model", model)
            if (content != null) put("content", content)
            if (!images.isNullOrEmpty()) putJsonArray("images") { images.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            put("regenerate", regenerate)
            if (editMessageId != null) put("edit_message_id", editMessageId)
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            var finished = false
            try {
                api.stream("POST", "/api/chats/$chatId/messages", body) { ev ->
                    when (ev.str("type")) {
                        "start" -> {
                            val serverId = ev.obj("message")?.str("id")
                            val userId = ev.obj("user_message")?.str("id")
                            val localAssistant = assistantId
                            update(chatId) { c ->
                                val msgs = c.messages.map { if (it.id == localAssistant && serverId != null) it.copy(id = serverId) else it }.toMutableList()
                                val i = msgs.indexOfLast { it.role == "user" }
                                if (i >= 0 && userId != null) msgs[i] = msgs[i].copy(id = userId)
                                c.copy(messages = msgs)
                            }
                            if (serverId != null) assistantId = serverId
                        }
                        "delta" -> updateMessage(chatId, assistantId) { it.copy(content = it.content + ev.str("text").orEmpty()) }
                        "reasoning" -> updateMessage(chatId, assistantId) { it.copy(reasoning = (it.reasoning ?: "") + ev.str("text").orEmpty()) }
                        "done", "error" -> {
                            ev.obj("message")?.let { m ->
                                val msg = runCatching { ApiJson.decodeFromJsonElement<ChatMessage>(m) }.getOrNull()
                                if (msg != null) updateMessage(chatId, assistantId) { msg }
                            }
                            finished = true
                        }
                        "title" -> ev.str("title")?.let { t ->
                            update(chatId) { it.copy(title = t) }
                            patchSummary(chatId) { it.copy(title = t) }
                        }
                    }
                }
                if (!finished) updateMessage(chatId, assistantId) { if (it.content.isEmpty() && it.error == null) it.copy(error = "The connection closed before the reply finished") else it }
            } catch (e: CancellationException) {
                updateMessage(chatId, assistantId) { it.copy(stopped = true) }
                throw e
            } catch (e: Exception) {
                updateMessage(chatId, assistantId) { it.copy(error = e.message ?: "Request failed") }
            } finally {
                streaming.update { it - chatId }
                jobs.remove(chatId)
                val done = chats.value[chatId]
                if (finished && done != null && !platform.inForeground) {
                    val reply = done.messages.lastOrNull { it.role == "assistant" }
                    platform.notifyReplyReady(chatId, done.title, reply?.error ?: splitThinking(reply?.content.orEmpty()).answer)
                }
            }
        }
        jobs[chatId] = job
        job.start()
    }

    fun stop(chatId: String) {
        jobs[chatId]?.cancel()
    }

    suspend fun rename(id: String, title: String) = applyPatch(id, buildJsonObject { put("title", title) })
    suspend fun setPinned(id: String, pinned: Boolean) = applyPatch(id, buildJsonObject { put("pinned", pinned) })
    suspend fun setModel(id: String, model: String) = applyPatch(id, buildJsonObject { put("model", model) })
    suspend fun setParams(id: String, params: ChatParams) =
        applyPatch(id, buildJsonObject { put("params", ApiJson.encodeToJsonElement(ChatParams.serializer(), params)) })

    private suspend fun applyPatch(id: String, patch: JsonObject) {
        val c = api.updateChat(id, patch)
        update(id) { it.copy(title = c.title, pinned = c.pinned, model = c.model, params = c.params) }
        patchSummary(id) { it.copy(title = c.title, pinned = c.pinned, model = c.model) }
    }

    suspend fun delete(id: String) {
        stop(id)
        api.deleteChat(id)
        chats.update { it - id }
        list.update { l -> l.filter { it.id != id } }
    }

    suspend fun deleteAll() {
        jobs.values.toList().forEach { it.cancel() }
        api.deleteAllChats()
        chats.value = emptyMap()
        list.value = emptyList()
    }

    fun clear() {
        jobs.values.toList().forEach { it.cancel() }
        chats.value = emptyMap()
        list.value = emptyList()
        listLoaded.value = false
    }
}

// =============================================================================== cowork

class CoworkRepository(
    private val api: Api,
    private val scope: CoroutineScope,
    private val platform: Platform,
    private val notificationsEnabled: () -> Boolean,
) {
    val sessions = MutableStateFlow<List<CoworkSummary>>(emptyList())
    val sessionsLoaded = MutableStateFlow(false)
    val live = MutableStateFlow<Map<String, LiveSession>>(emptyMap())

    /** Task ids with a live event subscription for a running task. */
    val watching = MutableStateFlow<Set<String>>(emptySet())
    private val subs = HashMap<String, Job>()

    suspend fun loadSessions() {
        sessions.value = api.coworkSessions()
        sessionsLoaded.value = true
        // Follow tasks that are running on the server, so we can alert about approvals and results.
        for (s in sessions.value) if (s.status == "running" && subs[s.id]?.isActive != true) {
            scope.launch { runCatching { open(s.id) } }
        }
    }

    fun upsertSummary(id: String, fn: (CoworkSummary?) -> CoworkSummary?) = sessions.update { l ->
        val cur = l.firstOrNull { it.id == id }
        val next = fn(cur) ?: return@update l
        (listOf(next) + l.filter { it.id != id }).sortedByDescending { it.updatedAt }
    }

    private fun updateLive(id: String, fn: (LiveSession) -> LiveSession) =
        live.update { m -> m[id]?.let { m + (id to fn(it)) } ?: m }

    suspend fun open(id: String) {
        if (live.value.containsKey(id) && subs[id]?.isActive == true) return
        init(api.coworkSession(id))
    }

    /** Reload a task from the server if nothing is streaming into it. */
    suspend fun refresh(id: String) {
        if (subs[id]?.isActive == true) return
        init(api.coworkSession(id))
    }

    fun init(s: CoworkSession) {
        live.update { it + (s.id to LiveSession.from(s)) }
        upsertSummary(s.id) { s.summary() }
        if (s.run != null) subscribe(s.id, -1)
    }

    private fun subscribe(id: String, after0: Int) {
        subs[id]?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var after = after0
            var ended = false
            var attempt = 0
            updateLive(id) { it.copy(connected = true) }
            watching.update { it + id }
            try {
                while (isActive && !ended) {
                    try {
                        api.stream("GET", "/api/cowork/sessions/$id/stream", query = mapOf("after" to after)) { ev ->
                            val type = ev.str("type")
                            if (type == "idle") {
                                ended = true
                                // The run finished while we were away and its events are gone: reload.
                                if (live.value[id]?.running == true) {
                                    runCatching { api.coworkSession(id) }.onSuccess { fresh ->
                                        updateLive(id) { it.copy(session = fresh, messages = fresh.messages, plan = fresh.plan, running = false, error = fresh.lastError) }
                                    }.onFailure { updateLive(id) { it.copy(running = false) } }
                                }
                                return@stream
                            }
                            ev.int("seq")?.let { after = it }
                            val seq = after
                            updateLive(id) { it.apply(ev).copy(lastSeq = seq) }
                            onEvent(id, type, ev)
                            if (type == "run_end") ended = true
                        }
                        if (!ended) delay(500)
                        attempt = 0
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ApiException) {
                        if (e.status in setOf(401, 403, 404)) break
                        attempt += 1
                        delay(min(8000L, 500L * (1L shl min(attempt, 5))))
                    }
                }
            } finally {
                updateLive(id) { it.copy(connected = false) }
                watching.update { it - id }
                if (subs[id] === coroutineContext[Job]) subs.remove(id)
            }
        }
        subs[id] = job
        job.start()
    }

    private fun onEvent(id: String, type: String?, ev: JsonObject) {
        when (type) {
            "approval_required" -> {
                val tc = ev.str("tool_call_id") ?: return
                // Replayed backlog resolves old approvals right away, so wait a moment before alerting.
                scope.launch {
                    delay(1200)
                    val l = live.value[id] ?: return@launch
                    if (tc in l.approvals && !platform.inForeground && notificationsEnabled()) {
                        platform.notifyApproval(id, l.session.title, l.toolCall(tc))
                    }
                }
            }
            "approval_resolved" -> platform.cancelApproval(id)
            "session_update" -> ev.str("approval_mode")?.let { mode -> upsertSummary(id) { it?.copy(approvalMode = mode) } }
            "run_end" -> {
                val status = ev.str("status") ?: "completed"
                platform.cancelApproval(id)
                upsertSummary(id) { it?.copy(status = if (status == "error") "error" else "idle", updatedAt = now()) }
                val l = live.value[id]
                if (!platform.inForeground && notificationsEnabled() && l != null) {
                    platform.notifyTaskEnded(id, l.session.title, status, ev.str("error"))
                }
                // Titles are generated after the first run.
                scope.launch {
                    delay(2500)
                    runCatching { loadSessions() }
                    sessions.value.firstOrNull { it.id == id }?.let { s -> updateLive(id) { it.copy(session = it.session.copy(title = s.title)) } }
                }
            }
        }
    }

    suspend fun create(prompt: String, model: String, approvalMode: String, repoId: String?, createBranch: Boolean): CoworkSession {
        val s = api.createCoworkSession(prompt, model, approvalMode, repoId, createBranch)
        init(s)
        return s
    }

    suspend fun send(id: String, content: String, model: String?) {
        val res = api.coworkMessage(id, content, model)
        upsertSummary(id) { it?.copy(status = "running", updatedAt = now()) }
        if (!res.queued) {
            updateLive(id) { it.copy(running = true, error = null) }
            subscribe(id, -1)
        }
    }

    suspend fun cancel(id: String) = api.coworkCancel(id)

    suspend fun approve(id: String, toolCallId: String, approved: Boolean, always: Boolean) {
        updateLive(id) { it.copy(approvals = it.approvals.filter { a -> a != toolCallId }) }
        if (always && approved) updateLive(id) { it.copy(session = it.session.copy(approvalMode = "auto")) }
        platform.cancelApproval(id)
        api.coworkApprove(id, toolCallId, approved, always)
    }

    suspend fun setApprovalMode(id: String, mode: String) {
        api.updateCoworkSession(id, buildJsonObject { put("approval_mode", mode) })
        updateLive(id) { it.copy(session = it.session.copy(approvalMode = mode)) }
        upsertSummary(id) { it?.copy(approvalMode = mode) }
    }

    suspend fun setModel(id: String, model: String) {
        api.updateCoworkSession(id, buildJsonObject { put("model", model) })
        updateLive(id) { it.copy(session = it.session.copy(model = model)) }
        upsertSummary(id) { it?.copy(model = model) }
    }

    suspend fun rename(id: String, title: String) {
        val s = api.updateCoworkSession(id, buildJsonObject { put("title", title) })
        updateLive(id) { it.copy(session = it.session.copy(title = s.title)) }
        upsertSummary(id) { it?.copy(title = s.title) }
    }

    suspend fun delete(id: String, deleteFiles: Boolean) {
        api.deleteCoworkSession(id, deleteFiles)
        subs.remove(id)?.cancel()
        live.update { it - id }
        sessions.update { l -> l.filter { it.id != id } }
        platform.cancelApproval(id)
    }

    fun clear() {
        subs.values.toList().forEach { it.cancel() }
        subs.clear()
        live.value = emptyMap()
        sessions.value = emptyList()
        sessionsLoaded.value = false
    }
}
