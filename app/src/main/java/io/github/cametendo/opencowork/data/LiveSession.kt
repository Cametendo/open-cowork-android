package io.github.cametendo.opencowork.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Live view of a Cowork task, built from the persisted transcript plus the run's
 * event stream (same model as the web UI's store).
 */
data class LiveSession(
    val session: CoworkSession,
    val messages: List<CoworkMessage>,
    val plan: List<PlanItem>,
    val running: Boolean,
    val approvals: List<String> = emptyList(),
    val activeTools: List<String> = emptyList(),
    val toolOutput: Map<String, String> = emptyMap(),
    val queued: List<CoworkMessage> = emptyList(),
    val error: String? = null,
    val lastSeq: Int = -1,
    val connected: Boolean = false,
) {
    companion object {
        fun from(s: CoworkSession) = LiveSession(
            session = s,
            messages = s.messages,
            plan = s.plan,
            running = s.run != null && !s.run.done,
            error = if (s.status == "error") s.lastError else null,
        )
    }

    fun toolCall(id: String): ToolCall? =
        messages.asSequence().mapNotNull { it.toolCalls }.flatten().firstOrNull { it.id == id }
}

private fun JsonObject.message(): CoworkMessage? =
    obj("message")?.let { runCatching { ApiJson.decodeFromJsonElement<CoworkMessage>(it) }.getOrNull() }

private fun List<CoworkMessage>.upsert(msg: CoworkMessage): List<CoworkMessage> {
    val i = indexOfFirst { it.id == msg.id }
    if (i == -1) return this + msg
    return toMutableList().also { it[i] = msg }
}

private fun nowSeconds() = System.currentTimeMillis() / 1000.0

/** Apply one event from `/api/cowork/sessions/{id}/stream`. */
fun LiveSession.apply(ev: JsonObject): LiveSession = when (ev.str("type")) {
    "run_start" -> copy(running = true, error = null)
    "user_message" -> ev.message()?.let { m -> copy(messages = messages.upsert(m), queued = queued.filter { it.id != m.id }) } ?: this
    "input_queued" -> ev.message()?.let { m -> if (queued.any { it.id == m.id }) this else copy(queued = queued + m) } ?: this
    "assistant_start" -> {
        val id = ev.str("id")
        if (id == null || messages.any { it.id == id }) this
        else copy(messages = messages + CoworkMessage(id = id, role = "assistant", model = ev.str("model"), createdAt = nowSeconds()))
    }
    "delta", "reasoning" -> {
        val id = ev.str("id")
        val text = ev.str("text").orEmpty()
        val isContent = ev.str("type") == "delta"
        if (id == null) this else {
            var found = false
            val next = messages.map { m ->
                if (m.id != id) m else {
                    found = true
                    if (isContent) m.copy(content = m.content + text) else m.copy(reasoning = (m.reasoning ?: "") + text)
                }
            }
            if (found) copy(messages = next)
            else copy(
                messages = messages + CoworkMessage(
                    id = id, role = "assistant",
                    content = if (isContent) text else "",
                    reasoning = if (isContent) null else text,
                    createdAt = nowSeconds(),
                ),
            )
        }
    }
    "assistant_end" -> ev.message()?.let { copy(messages = messages.upsert(it)) } ?: this
    "approval_required" -> ev.str("tool_call_id")?.let { tc -> copy(approvals = approvals.filter { it != tc } + tc) } ?: this
    "approval_resolved" -> ev.str("tool_call_id")?.let { tc -> copy(approvals = approvals.filter { it != tc }) } ?: this
    "tool_start" -> ev.str("tool_call_id")?.let { tc -> copy(activeTools = activeTools + tc) } ?: this
    "tool_output" -> ev.str("tool_call_id")?.let { tc ->
        copy(toolOutput = toolOutput + (tc to (toolOutput[tc].orEmpty() + ev.str("text").orEmpty())))
    } ?: this
    "tool_result" -> ev.message()?.let { m ->
        val tc = m.toolCallId
        copy(
            messages = messages.upsert(m),
            activeTools = activeTools.filter { it != tc },
            toolOutput = if (tc == null) toolOutput else toolOutput - tc,
        )
    } ?: this
    "plan" -> ev.arr("plan")?.let { arr ->
        copy(plan = arr.mapNotNull { runCatching { ApiJson.decodeFromJsonElement<PlanItem>(it) }.getOrNull() })
    } ?: this
    "session_update" -> ev.str("approval_mode")?.let { copy(session = session.copy(approvalMode = it)) } ?: this
    "run_end" -> copy(
        running = false, approvals = emptyList(), activeTools = emptyList(), queued = emptyList(),
        error = ev.str("error"),
    )
    else -> this
}

/** Files written or edited by the agent, with summed line stats. */
data class TouchedFile(val path: String, val added: Int, val removed: Int, val created: Boolean)

fun touchedFiles(messages: List<CoworkMessage>): List<TouchedFile> {
    val map = LinkedHashMap<String, TouchedFile>()
    for (m in messages) {
        if (m.role != "tool" || m.isError == true || m.name !in setOf("write_file", "edit_file")) continue
        val path = m.metaString("path") ?: continue
        val prev = map[path] ?: TouchedFile(path, 0, 0, false)
        map[path] = prev.copy(
            added = prev.added + (m.metaInt("added") ?: 0),
            removed = prev.removed + (m.metaInt("removed") ?: 0),
            created = prev.created || m.metaBool("created") == true,
        )
    }
    return map.values.toList()
}

/** Split an inline `<think>…</think>` block off the start of a reply. */
data class ThinkingSplit(val thinking: String?, val answer: String, val open: Boolean)

fun splitThinking(content: String): ThinkingSplit {
    val m = Regex("^\\s*<think>([\\s\\S]*?)(</think>|$)").find(content) ?: return ThinkingSplit(null, content, false)
    val open = m.groupValues[2] != "</think>"
    return ThinkingSplit(m.groupValues[1].trim(), content.substring(m.value.length).trimStart(), open)
}
