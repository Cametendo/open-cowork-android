package io.github.cametendo.opencowork

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.background.Notifier
import io.github.cametendo.opencowork.ui.AppRoot
import io.github.cametendo.opencowork.ui.NavRequest
import io.github.cametendo.opencowork.ui.theme.OpenCoworkTheme
import io.github.cametendo.opencowork.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val navRequests = MutableStateFlow<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as OpenCoworkApp).container
        handle(intent)
        setContent {
            val prefs by container.prefs.collectAsStateWithLifecycle()
            val dark = isDarkTheme(prefs)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            OpenCoworkTheme(prefs) {
                AppRoot(container, navRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifier.EXTRA_OPEN_CHAT)?.let { navRequests.value = NavRequest.Chat(it) }
        intent.getStringExtra(Notifier.EXTRA_OPEN_COWORK)?.let { navRequests.value = NavRequest.Cowork(it) }
    }
}
