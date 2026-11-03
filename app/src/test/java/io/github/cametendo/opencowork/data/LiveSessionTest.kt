package io.github.cametendo.opencowork.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionTest {
    private fun ev(json: String): JsonObject = ApiJson.parseToJsonElement(json).jsonObject

    private fun start() = LiveSession.from(
        ApiJson.decodeFromString(CoworkSession.serializer(), """{"id":"s1","title":"t","workspace_path":"/w","run":{"id":"r","done":false}}"""),
    )

    @Test fun fullRun() {
        var s = start()
        assertTrue(s.running)
        val events = listOf(
            """{"seq":0,"type":"run_start","run_id":"r"}""",
            """{"seq":1,"type":"user_message","message":{"id":"u1","role":"user","content":"do it","created_at":1}}""",
            """{"seq":2,"type":"assistant_start","id":"a1","model":"m"}""",
            """{"seq":3,"type":"delta","id":"a1","text":"Hel"}""",
            """{"seq":4,"type":"delta","id":"a1","text":"lo"}""",
            """{"seq":5,"type":"reasoning","id":"a1","text":"hmm"}""",
            """{"seq":6,"type":"assistant_end","message":{"id":"a1","role":"assistant","content":"Hello","tool_calls":[{"id":"c1","name":"run_command","arguments":{"command":"ls"}}],"created_at":2}}""",
            """{"seq":7,"type":"approval_required","tool_call_id":"c1"}""",
        )
        events.forEach { s = s.apply(ev(it)) }
        assertEquals(listOf("c1"), s.approvals)
        assertEquals("ls", s.toolCall("c1")?.arg("command"))
        assertEquals("Hello", s.messages.last().content)

        listOf(
            """{"seq":8,"type":"approval_resolved","tool_call_id":"c1","approved":true}""",
            """{"seq":9,"type":"tool_start","tool_call_id":"c1"}""",
            """{"seq":10,"type":"tool_output","tool_call_id":"c1","text":"a.txt\n"}""",
        ).forEach { s = s.apply(ev(it)) }
        assertTrue(s.approvals.isEmpty())
        assertEquals(listOf("c1"), s.activeTools)
        assertEquals("a.txt\n", s.toolOutput["c1"])

        listOf(
            """{"seq":11,"type":"tool_result","message":{"id":"t1","role":"tool","tool_call_id":"c1","name":"run_command","content":"a.txt","is_error":false,"meta":{"exit_code":0},"created_at":3}}""",
            """{"seq":12,"type":"plan","plan":[{"title":"Look","status":"completed"},{"title":"Write","status":"in_progress"}]}""",
            """{"seq":13,"type":"input_queued","message":{"id":"q1","role":"user","content":"also this","created_at":4}}""",
            """{"seq":14,"type":"session_update","approval_mode":"auto"}""",
        ).forEach { s = s.apply(ev(it)) }
        assertTrue(s.activeTools.isEmpty())
        assertNull(s.toolOutput["c1"])
        assertEquals(2, s.plan.size)
        assertEquals(1, s.queued.size)
        assertEquals("auto", s.session.approvalMode)
        assertEquals(0, s.messages.first { it.role == "tool" }.metaInt("exit_code"))

        s = s.apply(ev("""{"seq":15,"type":"user_message","message":{"id":"q1","role":"user","content":"also this","created_at":4}}"""))
        assertTrue(s.queued.isEmpty())
        s = s.apply(ev("""{"seq":16,"type":"run_end","status":"error","error":"boom"}"""))
        assertFalse(s.running)
        assertEquals("boom", s.error)
    }

    @Test fun deltaForUnknownMessageCreatesIt() {
        val s = start().apply(ev("""{"type":"delta","id":"zz","text":"hi"}"""))
        assertEquals("hi", s.messages.last { it.id == "zz" }.content)
    }

    @Test fun touchedFilesSumsStats() {
        val msgs = listOf(
            CoworkMessage("1", "tool", name = "write_file", meta = ev("""{"path":"a.txt","added":3,"removed":0,"created":true}""")),
            CoworkMessage("2", "tool", name = "edit_file", meta = ev("""{"path":"a.txt","added":1,"removed":2}""")),
            CoworkMessage("3", "tool", name = "read_file", meta = ev("""{"path":"b.txt"}""")),
        )
        assertEquals(listOf(TouchedFile("a.txt", 4, 2, true)), touchedFiles(msgs))
    }

    @Test fun thinkingSplit() {
        assertEquals(ThinkingSplit("plan", "Answer", false), splitThinking("<think>plan</think>\nAnswer"))
        assertEquals(ThinkingSplit("still", "", true), splitThinking("<think>still"))
        assertEquals(ThinkingSplit(null, "plain", false), splitThinking("plain"))
    }
}
