package io.github.cametendo.opencowork.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Property names are camelCase; the JSON is snake_case (see Api.json naming strategy).

// ---------------------------------------------------------------- auth & config

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: String = "pending",
    val createdAt: Double = 0.0,
    val lastActiveAt: Double = 0.0,
    val githubLogin: String? = null,
    val settings: UserSettings? = null,
) {
    val isAdmin get() = role == "admin"
    val isPending get() = role == "pending"
}

@Serializable
data class UserSettings(
    val theme: String? = null,
    val systemPrompt: String? = null,
    val defaultModel: String? = null,
    val coworkDefaultModel: String? = null,
    val coworkInstructions: String? = null,
    val coworkApprovalMode: String? = null,
)

@Serializable
data class AuthSession(val token: String, val tokenType: String = "Bearer", val user: User)

@Serializable
data class Features(val enableSignup: Boolean = false, val cowork: Boolean = true, val github: Boolean = true)

@Serializable
data class PublicConfig(
    val name: String = "Open Cowork",
    val version: String = "",
    val onboarding: Boolean = false,
    val features: Features = Features(),
)

@Serializable
data class AdminConfig(
    val webuiName: String = "Open Cowork",
    val enableSignup: Boolean = true,
    val defaultUserRole: String = "pending",
    val enableTitleGeneration: Boolean = true,
    val coworkEnabled: Boolean = true,
    val coworkAllowShell: Boolean = true,
    val coworkCommandTimeout: Int = 120,
    val coworkMaxSteps: Int = 40,
    val coworkOllamaNumCtx: Int = 16384,
    val githubEnabled: Boolean = true,
)

// ---------------------------------------------------------------- models & connections

@Serializable
data class Model(
    val id: String,
    val name: String = id,
    val provider: String = "",
    val connectionId: String = "",
    val connectionName: String = "",
    val details: JsonObject? = null,
)

@Serializable
data class ModelList(val data: List<Model> = emptyList())

@Serializable
data class Connection(
    val id: String,
    val type: String,
    val name: String,
    val baseUrl: String,
    val hasApiKey: Boolean = false,
    val enabled: Boolean = true,
    val prefixId: String? = null,
    val modelIds: List<String> = emptyList(),
    val createdAt: Double = 0.0,
)

@Serializable
data class VerifyResult(val ok: Boolean = false, val models: List<String> = emptyList())

// ---------------------------------------------------------------- chats

@Serializable
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String = "",
    val model: String? = null,
    val reasoning: String? = null,
    val images: List<String>? = null,
    val error: String? = null,
    val stopped: Boolean? = null,
    val usage: JsonObject? = null,
    val createdAt: Double = 0.0,
) {
    val completionTokens: Int?
        get() = (usage?.get("completion_tokens") as? JsonPrimitive)?.intOrNull
}

@Serializable
data class ChatSummary(
    val id: String,
    val title: String = "New Chat",
    val model: String? = null,
    val pinned: Boolean = false,
    val createdAt: Double = 0.0,
    val updatedAt: Double = 0.0,
)

@Serializable
data class ChatParams(
    val system: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val numCtx: Int? = null,
)

@Serializable
data class Chat(
    val id: String,
    val title: String = "New Chat",
    val model: String? = null,
    val pinned: Boolean = false,
    val createdAt: Double = 0.0,
    val updatedAt: Double = 0.0,
    val messages: List<ChatMessage> = emptyList(),
    val params: ChatParams = ChatParams(),
) {
    fun summary() = ChatSummary(id, title, model, pinned, createdAt, updatedAt)
}

// ---------------------------------------------------------------- cowork

@Serializable
data class ToolCall(val id: String, val name: String, val arguments: JsonObject = JsonObject(emptyMap())) {
    fun arg(key: String): String = arguments[key].asText()
}

@Serializable
data class CoworkMessage(
    val id: String,
    val role: String,
    val content: String = "",
    val model: String? = null,
    val reasoning: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null,
    val isError: Boolean? = null,
    val meta: JsonObject? = null,
    val notice: Boolean? = null,
    val stopped: Boolean? = null,
    val createdAt: Double = 0.0,
) {
    fun metaString(key: String): String? = (meta?.get(key) as? JsonPrimitive)?.contentOrNull
    fun metaInt(key: String): Int? = (meta?.get(key) as? JsonPrimitive)?.intOrNull
    fun metaBool(key: String): Boolean? = (meta?.get(key) as? JsonPrimitive)?.booleanOrNull
    val denied get() = metaBool("denied") == true
}

@Serializable
data class PlanItem(val title: String, val status: String = "pending")

@Serializable
data class RunInfo(val id: String, val done: Boolean = false)

@Serializable
data class CoworkSummary(
    val id: String,
    val title: String = "New task",
    val model: String? = null,
    val status: String = "idle",
    val workspaceType: String = "scratch",
    val workspacePath: String = "",
    val repoId: String? = null,
    val repoName: String? = null,
    val branch: String? = null,
    val baseBranch: String? = null,
    val approvalMode: String = "ask",
    val createdAt: Double = 0.0,
    val updatedAt: Double = 0.0,
)

@Serializable
data class CoworkSession(
    val id: String,
    val title: String = "New task",
    val model: String? = null,
    val status: String = "idle",
    val workspaceType: String = "scratch",
    val workspacePath: String = "",
    val repoId: String? = null,
    val repoName: String? = null,
    val branch: String? = null,
    val baseBranch: String? = null,
    val approvalMode: String = "ask",
    val createdAt: Double = 0.0,
    val updatedAt: Double = 0.0,
    val messages: List<CoworkMessage> = emptyList(),
    val plan: List<PlanItem> = emptyList(),
    val lastError: String? = null,
    val run: RunInfo? = null,
) {
    fun summary() = CoworkSummary(
        id, title, model, status, workspaceType, workspacePath, repoId, repoName, branch, baseBranch,
        approvalMode, createdAt, updatedAt,
    )
}

@Serializable
data class CoworkPostResult(val queued: Boolean = false)

@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val type: String,
    val size: Long? = null,
    val modified: Double = 0.0,
    val ignored: Boolean = false,
) {
    val isDir get() = type == "dir"
}

@Serializable
data class DirListing(val path: String = ".", val root: String = "", val entries: List<FileEntry> = emptyList())

@Serializable
data class FilePreview(
    val path: String,
    val size: Long = 0,
    val mime: String = "",
    val binary: Boolean = false,
    val truncated: Boolean = false,
    val content: String? = null,
)

@Serializable
data class UploadResult(val saved: List<String> = emptyList())

// ---------------------------------------------------------------- GitHub & git

@Serializable
data class GitHubStatus(
    val connected: Boolean = false,
    val login: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val htmlUrl: String? = null,
    val scopes: String? = null,
    val repoDir: String = "",
)

@Serializable
data class RemoteRepo(
    val fullName: String,
    val name: String = "",
    val owner: String = "",
    val description: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val defaultBranch: String = "main",
    val htmlUrl: String = "",
    val pushedAt: String? = null,
    val language: String? = null,
    val stargazersCount: Int = 0,
    val localId: String? = null,
)

@Serializable
data class LocalRepo(
    val id: String,
    val fullName: String,
    val path: String = "",
    val defaultBranch: String = "main",
    val htmlUrl: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val source: String = "github",
    val userId: String = "",
    val createdAt: Double = 0.0,
    val lastPulledAt: Double? = null,
    val exists: Boolean? = null,
    val currentBranch: String? = null,
    val busy: Boolean? = null,
    val alreadyCloned: Boolean? = null,
)

@Serializable
data class GitFile(
    val path: String,
    val status: String = "M",
    val staged: Boolean = false,
    val origPath: String? = null,
    val added: Int? = null,
    val removed: Int? = null,
    val diff: String? = null,
    val binary: Boolean? = null,
    val truncated: Boolean? = null,
    val note: String? = null,
)

@Serializable
data class GitStatus(
    val branch: String? = null,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val files: List<GitFile> = emptyList(),
    val clean: Boolean = true,
    val busy: Boolean? = null,
    val repo: LocalRepo? = null,
)

@Serializable
data class GitChanges(val files: List<GitFile> = emptyList())

@Serializable
data class Branches(val current: String? = null, val local: List<String> = emptyList(), val remote: List<String> = emptyList())

@Serializable
data class GitMessageResult(val message: String = "", val status: GitStatus? = null)

@Serializable
data class CommitResult(val sha: String = "", val status: GitStatus? = null)

@Serializable
data class PullRequest(
    val number: Int,
    val title: String = "",
    val htmlUrl: String = "",
    val state: String = "",
    val draft: Boolean = false,
    val head: String = "",
    val base: String = "",
)

@Serializable
data class PullRequestResult(val pullRequest: PullRequest? = null, val existing: Boolean = false)

// ---------------------------------------------------------------- helpers

/** Render a JSON value as plain text: strings unquoted, everything else as JSON. */
fun JsonElement?.asText(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString()
}

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.obj(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()

fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.arr(key: String) = runCatching { this[key]?.jsonArray }.getOrNull()

fun JsonElement.primitiveOrNull(): JsonPrimitive? = runCatching { jsonPrimitive }.getOrNull()
