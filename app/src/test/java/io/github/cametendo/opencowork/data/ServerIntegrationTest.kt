package io.github.cametendo.opencowork.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Collections

/**
 * End-to-end checks against a real Open Cowork server (with the bundled mock LLM):
 *
 *   python backend/tests/mock_llm.py --port 11555
 *   OLLAMA_BASE_URL=http://127.0.0.1:11555 python -m open_cowork --port 3001
 *   OPEN_COWORK_TEST_URL=http://127.0.0.1:3001 ./gradlew testDebugUnitTest
 *
 * Skipped when OPEN_COWORK_TEST_URL isn't set.
 */
class ServerIntegrationTest {
    private val base = System.getenv("OPEN_COWORK_TEST_URL")
    private lateinit var api: Api
    private lateinit var scope: CoroutineScope
    private val events = Collections.synchronizedList(mutableListOf<String>())

    private val platform = object : Platform {
        override var inForeground = true
        override fun notifyReplyReady(chatId: String, title: String, preview: String) { events += "reply:$chatId" }
        override fun notifyApproval(sessionId: String, title: String, call: ToolCall?) { events += "approval:${call?.name}" }
        override fun cancelApproval(sessionId: String) { events += "cancel-approval" }
        override fun notifyTaskEnded(sessionId: String, title: String, status: String, error: String?) { events += "ended:$status" }
    }

    @Before fun setUp() = runBlocking {
        assumeTrue("OPEN_COWORK_TEST_URL not set", !base.isNullOrBlank())
        api = Api()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cfg = api.config(base)
        val session = if (cfg.onboarding) api.signup(base!!, "Test Admin", EMAIL, PASSWORD) else api.signin(base!!, EMAIL, PASSWORD)
        assertEquals("admin", session.user.role)
        api.baseUrl = base
        api.token = session.token
    }

    @After fun tearDown() { if (::scope.isInitialized) scope.cancel() }

    private suspend fun model(): String = api.models(refresh = true).first { it.id.startsWith("mock-llama") }.id

    @Test fun signInErrorsAreReadable() = runBlocking {
        val e = runCatching { api.signin(base!!, EMAIL, "wrong-password") }.exceptionOrNull()
        assertTrue(e is ApiException)
        assertEquals("Incorrect email or password", e!!.message)
    }

    @Test fun chatStreamsEditsRegeneratesAndStops() = runBlocking {
        val m = model()
        val repo = ChatRepository(api, scope, platform)
        val chat = repo.create(m)
        repo.send(chat.id, m, content = "hello there")
        waitIdle(repo, chat.id)
        var c = repo.chats.value[chat.id]!!
        assertEquals(listOf("user", "assistant"), c.messages.map { it.role })
        assertEquals("Echo: hello there", c.messages[1].content)
        assertFalse("local ids are replaced by server ids", c.messages.any { it.id.startsWith("local-") })

        // Server persisted it, and the automatic title arrived.
        val fromServer = api.chat(chat.id)
        assertEquals("Echo: hello there", fromServer.messages.last().content)
        delay(300)
        assertEquals("Mock Conversation Title", api.chat(chat.id).title)

        // Edit the user message
        repo.send(chat.id, m, content = "edited", editMessageId = c.messages[0].id)
        waitIdle(repo, chat.id)
        c = repo.chats.value[chat.id]!!
        assertEquals("Echo: edited", c.messages.last().content)
        assertEquals(2, c.messages.size)

        // Regenerate
        repo.send(chat.id, m, regenerate = true)
        waitIdle(repo, chat.id)
        assertEquals(2, repo.chats.value[chat.id]!!.messages.size)

        // Stop mid-stream: the partial reply is kept and marked stopped on the server too.
        repo.send(chat.id, m, content = (1..400).joinToString(" ") { "word$it" })
        delay(600)
        repo.stop(chat.id)
        waitIdle(repo, chat.id)
        val stopped = repo.chats.value[chat.id]!!.messages.last()
        assertEquals(true, stopped.stopped)
        delay(500)
        val persisted = api.chat(chat.id).messages.last()
        assertEquals(true, persisted.stopped)
        assertTrue(persisted.content.startsWith("Echo: word1"))
        assertTrue(persisted.content.length < 400 * 7)

        // Params, rename, pin, delete
        repo.setParams(chat.id, ChatParams(system = "Be brief", temperature = 0.3, maxTokens = 200, numCtx = 4096))
        assertEquals(ChatParams("Be brief", 0.3, 200, 4096), api.chat(chat.id).params)
        repo.rename(chat.id, "Renamed")
        repo.setPinned(chat.id, true)
        api.chat(chat.id).let { assertEquals("Renamed", it.title); assertTrue(it.pinned) }
        repo.loadList()
        assertTrue(repo.list.value.any { it.id == chat.id && it.pinned })
        repo.delete(chat.id)
        assertFalse(api.chats().any { it.id == chat.id })
    }

    @Test fun replyFinishesWhileAppIsInBackground() = runBlocking {
        val m = model()
        val repo = ChatRepository(api, scope, platform)
        val chat = repo.create(m)
        platform.inForeground = false
        repo.send(chat.id, m, content = (1..60).joinToString(" ") { "w$it" })
        waitIdle(repo, chat.id)
        platform.inForeground = true
        assertTrue(api.chat(chat.id).messages.last().content.endsWith("w60"))
        assertTrue("a 'reply ready' notification is posted", "reply:${chat.id}" in events)
    }

    @Test fun coworkAskModeApprovalFilesAndDelete() = runBlocking {
        val m = model()
        val repo = CoworkRepository(api, scope, platform) { true }
        platform.inForeground = false // so the approval alert path runs too
        val s = repo.create(
            """Create a file. [[tool:write_file {"path": "hello.txt", "content": "hi from android"}]]""",
            m, "ask", repoId = null, createBranch = false,
        )
        // Wait for the approval request
        val pending = withTimeout(20_000) { repo.live.first { it[s.id]?.approvals?.isNotEmpty() == true } }[s.id]!!
        val tc = pending.approvals.single()
        assertEquals("write_file", pending.toolCall(tc)?.name)
        delay(1500)
        assertTrue("approval notification posted", "approval:write_file" in events)

        repo.approve(s.id, tc, approved = true, always = false)
        val done = withTimeout(20_000) { repo.live.first { it[s.id]?.running == false } }[s.id]!!
        withTimeout(5_000) { while ("ended:completed" !in events) delay(50) }
        platform.inForeground = true
        assertEquals(null, done.error)
        val result = done.messages.first { it.role == "tool" && it.toolCallId == tc }
        assertEquals(false, result.isError)
        assertEquals("hello.txt", result.metaString("path"))
        assertTrue(done.messages.last { it.role == "assistant" }.content.startsWith("All done"))

        // Files: list, preview, download, upload
        val listing = api.coworkFiles(s.id)
        assertTrue(listing.entries.any { it.name == "hello.txt" && !it.isDir })
        assertEquals("hi from android", api.coworkFile(s.id, "hello.txt").content)
        val out = ByteArrayOutputStream()
        api.coworkDownload(s.id, "hello.txt") { it.copyTo(out) }
        assertEquals("hi from android", out.toString(Charsets.UTF_8))
        val bytes = "uploaded!".toByteArray()
        val up = api.coworkUpload(s.id, listOf(UploadSource("up.txt", bytes.size.toLong(), "text/plain") { bytes.inputStream() }))
        assertEquals(listOf("up.txt"), up.saved)
        assertEquals("uploaded!", api.coworkFile(s.id, "up.txt").content)

        // Follow-up in auto mode runs without approvals
        repo.setApprovalMode(s.id, "auto")
        repo.send(s.id, """Again [[tool:write_file {"path": "two.txt", "content": "2"}]]""", null)
        val second = withTimeout(20_000) {
            delay(300)
            repo.live.first { it[s.id]?.running == false && it[s.id]!!.messages.count { m2 -> m2.role == "tool" } >= 2 }
        }[s.id]!!
        assertTrue(second.approvals.isEmpty())
        assertEquals("2", api.coworkFile(s.id, "two.txt").content)

        repo.rename(s.id, "Renamed task")
        repo.loadSessions()
        assertTrue(repo.sessions.value.any { it.id == s.id && it.title == "Renamed task" })
        repo.delete(s.id, deleteFiles = true)
        assertFalse(api.coworkSessions().any { it.id == s.id })
    }

    @Test fun coworkDenyAndCancel() = runBlocking {
        val m = model()
        val repo = CoworkRepository(api, scope, platform) { false }
        val s = repo.create("""[[tool:run_command {"command": "echo hi"}]]""", m, "ask", null, false)
        val pending = withTimeout(20_000) { repo.live.first { it[s.id]?.approvals?.isNotEmpty() == true } }[s.id]!!
        repo.approve(s.id, pending.approvals.single(), approved = false, always = false)
        val done = withTimeout(20_000) { repo.live.first { it[s.id]?.running == false } }[s.id]!!
        assertEquals(true, done.messages.first { it.role == "tool" }.denied)

        // Start another run and cancel it while it waits for approval
        repo.send(s.id, """[[tool:run_command {"command": "echo again"}]]""", null)
        withTimeout(20_000) { repo.live.first { it[s.id]?.approvals?.isNotEmpty() == true } }
        repo.cancel(s.id)
        val cancelled = withTimeout(20_000) { repo.live.first { it[s.id]?.running == false } }[s.id]!!
        assertTrue(cancelled.approvals.isEmpty())
        repo.delete(s.id, deleteFiles = true)
    }

    @Test fun reopeningARunningTaskReplaysIt() = runBlocking {
        val m = model()
        val first = CoworkRepository(api, scope, platform) { false }
        val s = first.create("""[[tool:run_command {"command": "echo replay"}]]""", m, "ask", null, false)
        withTimeout(20_000) { first.live.first { it[s.id]?.approvals?.isNotEmpty() == true } }
        // A fresh repository (like the app after a restart) attaches to the same run
        val second = CoworkRepository(api, scope, platform) { false }
        second.open(s.id)
        val replayed = withTimeout(20_000) { second.live.first { it[s.id]?.approvals?.isNotEmpty() == true } }[s.id]!!
        assertTrue(replayed.running)
        assertEquals("echo replay", replayed.toolCall(replayed.approvals.single())?.arg("command"))
        second.approve(s.id, replayed.approvals.single(), approved = true, always = true)
        val done = withTimeout(20_000) { second.live.first { it[s.id]?.running == false } }[s.id]!!
        assertEquals("auto", done.session.approvalMode)
        assertTrue(done.messages.any { it.role == "tool" && it.content.contains("replay") })
        second.delete(s.id, deleteFiles = true)
    }

    @Test fun accountSettingsAndAdmin() = runBlocking {
        val merged = api.updateSettings(buildJsonObject { put("system_prompt", "From Android"); put("cowork_approval_mode", "auto") })
        assertEquals("From Android", merged.systemPrompt)
        assertEquals("auto", api.me().settings?.coworkApprovalMode)
        assertEquals("Test Admin", api.updateProfile("Test Admin").name)

        val cfg = api.updateAdminConfig(buildJsonObject { put("cowork_max_steps", 41); put("enable_signup", true) })
        assertEquals(41, cfg.coworkMaxSteps)
        assertEquals(41, api.adminConfig().coworkMaxSteps)

        val conns = api.connections()
        assertTrue(conns.isNotEmpty())
        val verify = api.verifyConnection(conns[0].type, conns[0].baseUrl, null, conns[0].id)
        assertTrue(verify.models.contains("mock-llama:latest"))
        val extra = api.createConnection(buildJsonObject {
            put("type", "openai"); put("name", "Mock OpenAI"); put("base_url", "http://127.0.0.1:11555/v1"); put("prefix_id", "oa")
        })
        assertTrue(api.models(refresh = true).any { it.id == "oa.mock-coder:7b" })
        api.updateConnection(extra.id, buildJsonObject { put("enabled", false) })
        api.deleteConnection(extra.id)

        val email = "pending${System.currentTimeMillis()}@example.com"
        val u = api.createUser("Pending Person", email, "password123", "pending")
        assertEquals("pending", u.role)
        assertEquals("user", api.updateUser(u.id, buildJsonObject { put("role", "user") }).role)
        assertTrue(api.users().any { it.email == email })
        api.deleteUser(u.id)

        val gh = api.githubStatus()
        assertNotNull(gh.repoDir)
        assertTrue(api.repos().isEmpty() || api.repos().all { it.id.isNotBlank() })
    }

    /** Needs OPEN_COWORK_TEST_REPO_DIR = the server's REPO_DIR (admins see repos placed there by hand). */
    @Test fun repoTaskBranchDiffAndCommit() = runBlocking {
        val repoDir = System.getenv("OPEN_COWORK_TEST_REPO_DIR")
        assumeTrue("OPEN_COWORK_TEST_REPO_DIR not set", !repoDir.isNullOrBlank())
        val name = "demo-${System.currentTimeMillis()}"
        val dir = java.io.File(repoDir, name).apply { mkdirs() }
        fun git(vararg args: String) {
            val p = ProcessBuilder("git", "-c", "user.name=Test", "-c", "user.email=t@example.com", *args).directory(dir).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "git ${args.joinToString(" ")}: $out" }
        }
        git("init", "-q", "-b", "main")
        java.io.File(dir, "README.md").writeText("# Demo\n")
        git("add", "-A"); git("commit", "-q", "-m", "init")

        val repo = api.repos().first { it.fullName.endsWith(name) }
        assertEquals("local", repo.source)
        assertEquals("main", repo.currentBranch)

        val m = model()
        val cowork = CoworkRepository(api, scope, platform) { false }
        val s = cowork.create("""Add a file [[tool:write_file {"path": "hello.txt", "content": "hello\nworld\n"}]]""", m, "auto", repo.id, createBranch = true)
        assertTrue(s.branch!!.startsWith("cowork/"))
        assertEquals("main", s.baseBranch)
        withTimeout(20_000) { delay(300); cowork.live.first { it[s.id]?.running == false } }

        val st = api.repoStatus(repo.id)
        assertEquals(s.branch, st.branch)
        assertFalse(st.clean)
        assertEquals(repo.id, st.repo?.id)
        val changes = api.repoChanges(repo.id).files
        val f = changes.single { it.path == "hello.txt" }
        assertEquals(2, f.added)
        assertTrue(f.diff.orEmpty().contains("+world"))

        val commit = api.repoCommit(repo.id, "Add hello.txt")
        assertTrue(commit.sha.isNotBlank())
        assertTrue(commit.status!!.clean)
        assertTrue(api.repoBranches(repo.id).local.contains(s.branch!!))
        assertEquals(null, api.repoPullRequest(repo.id).pullRequest)

        cowork.delete(s.id, deleteFiles = false)
        assertEquals("main", api.repoCheckout(repo.id, "main", create = false).branch)
        api.removeRepo(repo.id, deleteFiles = true)
        assertFalse(dir.exists())
    }

    private suspend fun waitIdle(repo: ChatRepository, id: String) = withTimeout(30_000) {
        delay(50)
        repo.streaming.first { id !in it }
    }

    companion object {
        const val EMAIL = "admin@example.com"
        const val PASSWORD = "password123"
    }
}
