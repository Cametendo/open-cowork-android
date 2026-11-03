package io.github.cametendo.opencowork

import android.content.Context
import io.github.cametendo.opencowork.background.GenerationService
import io.github.cametendo.opencowork.background.Notifier
import io.github.cametendo.opencowork.data.Api
import io.github.cametendo.opencowork.data.AppPrefs
import io.github.cametendo.opencowork.data.AppPrefsStore
import io.github.cametendo.opencowork.data.AuthRepository
import io.github.cametendo.opencowork.data.AuthState
import io.github.cametendo.opencowork.data.ChatRepository
import io.github.cametendo.opencowork.data.CoworkRepository
import io.github.cametendo.opencowork.data.ModelRepository
import io.github.cametendo.opencowork.data.Platform
import io.github.cametendo.opencowork.data.SessionStore
import io.github.cametendo.opencowork.data.ToolCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide singletons. Lives as long as the process, independent of any screen. */
class AppContainer(private val context: Context) : Platform {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val api = Api()
    val sessionStore = SessionStore(context)
    val prefsStore = AppPrefsStore(context)
    val prefs: StateFlow<AppPrefs> = prefsStore.prefs.stateIn(scope, SharingStarted.Eagerly, AppPrefs())
    val notifier = Notifier(context)

    val auth = AuthRepository(api, sessionStore, scope)
    val models = ModelRepository(api)
    val chats = ChatRepository(api, scope, this)
    val cowork = CoworkRepository(api, scope, this) { prefs.value.coworkNotifications }

    @Volatile var foreground: Boolean = false

    /** Human-readable list of what is currently running (drives the foreground service). */
    val activeWork: StateFlow<List<String>> = combine(chats.streaming, chats.chats, cowork.watching, cowork.live) { streaming, chats, watching, live ->
        streaming.map { "Replying in “${chats[it]?.title ?: "chat"}”" } +
            watching.filter { live[it]?.running == true }.map { "Cowork · ${live[it]?.session?.title ?: "task"}" }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch { auth.restore() }
        scope.launch {
            activeWork.map { it.isNotEmpty() }.distinctUntilChanged().collect { busy ->
                if (busy) GenerationService.start(context)
            }
        }
        scope.launch {
            var lastUser: String? = null
            auth.state.collect { st ->
                when (st) {
                    is AuthState.SignedOut -> {
                        lastUser = null
                        chats.clear(); cowork.clear(); models.clear()
                    }
                    is AuthState.SignedIn -> if (st.user.id != lastUser) {
                        if (lastUser != null) { chats.clear(); cowork.clear(); models.clear() }
                        lastUser = st.user.id
                        if (!st.user.isPending) warmUp()
                    }
                    AuthState.Loading -> {}
                }
            }
        }
    }

    private fun warmUp() {
        scope.launch { models.load() }
        scope.launch { runCatching { chats.loadList() } }
        scope.launch { runCatching { cowork.loadSessions() } }
    }

    // ------------------------------------------------------------------ Platform

    override val inForeground: Boolean get() = foreground

    override fun notifyReplyReady(chatId: String, title: String, preview: String) {
        if (prefs.value.replyNotifications) notifier.replyReady(chatId, title, preview)
    }

    override fun notifyApproval(sessionId: String, title: String, call: ToolCall?) = notifier.approval(sessionId, title, call)
    override fun cancelApproval(sessionId: String) = notifier.cancelApproval(sessionId)
    override fun notifyTaskEnded(sessionId: String, title: String, status: String, error: String?) =
        notifier.taskEnded(sessionId, title, status, error)
}
