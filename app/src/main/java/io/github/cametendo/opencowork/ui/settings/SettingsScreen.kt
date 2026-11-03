package io.github.cametendo.opencowork.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.AuthState
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.LocalFeatures
import io.github.cametendo.opencowork.ui.components.ConfirmDialog
import io.github.cametendo.opencowork.ui.components.LetterAvatar
import io.github.cametendo.opencowork.ui.components.SectionHeader
import io.github.cametendo.opencowork.ui.components.SettingRow
import io.github.cametendo.opencowork.ui.components.SettingsCard
import io.github.cametendo.opencowork.ui.components.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    val container = LocalContainer.current
    val features = LocalFeatures.current
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val prefs by container.prefs.collectAsStateWithLifecycle()
    val signedIn = auth as? AuthState.SignedIn
    val user = signedIn?.user
    val scope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Settings") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (user != null) {
                Surface(
                    onClick = { onNavigate("settings/account") },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LetterAvatar(user.name, 48)
                        Column(Modifier.weight(1f)) {
                            Text(user.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            Text(user.role.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            SectionHeader("App")
            SettingsCard {
                SettingRow("Appearance", "Theme, colors and chat wallpapers", Icons.Outlined.Palette, onClick = { onNavigate("settings/appearance") }) { Chevron() }
                Divider()
                SwitchRow("Reply notifications", "Tell me when a reply finishes while the app is closed", prefs.replyNotifications, icon = Icons.Outlined.NotificationsActive) {
                    scope.launch { container.prefsStore.setReplyNotifications(it) }
                }
                if (features.cowork) {
                    Divider()
                    SwitchRow("Cowork alerts", "Approvals needed and finished tasks, with Allow / Deny buttons", prefs.coworkNotifications, icon = Icons.Outlined.SmartToy) {
                        scope.launch { container.prefsStore.setCoworkNotifications(it) }
                    }
                }
            }

            SectionHeader("Your server settings")
            SettingsCard {
                SettingRow("Chat", "Default model and system prompt", Icons.Outlined.ChatBubbleOutline, onClick = { onNavigate("settings/chat") }) { Chevron() }
                if (features.cowork) {
                    Divider()
                    SettingRow("Cowork", "Default model, approval mode and custom instructions", Icons.Outlined.SmartToy, onClick = { onNavigate("settings/cowork") }) { Chevron() }
                }
                if (features.github) {
                    Divider()
                    SettingRow("GitHub", user?.githubLogin?.let { "Connected as $it" } ?: "Connect with a personal access token", Icons.Outlined.Code, onClick = { onNavigate("settings/github") }) { Chevron() }
                }
                Divider()
                SettingRow("Account", "Name and password", Icons.Outlined.Person, onClick = { onNavigate("settings/account") }) { Chevron() }
            }

            if (features.admin) {
                SectionHeader("Administration")
                SettingsCard {
                    SettingRow("Admin panel", "Users, connections, sign-ups and Cowork limits", Icons.Outlined.AdminPanelSettings, onClick = { onNavigate("admin") }) { Chevron() }
                }
            }

            SectionHeader("Server")
            SettingsCard {
                SettingRow(
                    signedIn?.config?.name ?: "Open Cowork",
                    listOfNotNull(signedIn?.baseUrl, signedIn?.config?.version?.takeIf { it.isNotBlank() }?.let { "v$it" }).joinToString(" · "),
                    Icons.Outlined.Dns,
                )
                Divider()
                SettingRow("About", null, Icons.Outlined.Info, onClick = { onNavigate("settings/about") }) { Chevron() }
            }

            OutlinedButton(
                onClick = { signingOut = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null)
                Text("  Sign out")
            }
        }
    }

    if (signingOut) {
        ConfirmDialog(
            "Sign out?",
            "Running Cowork tasks keep going on the server. Replies that are still generating on this phone will stop.",
            confirmLabel = "Sign out",
            onDismiss = { signingOut = false },
        ) { scope.launch { container.auth.signOut() } }
    }
}

@Composable
internal fun Chevron() = Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun Divider() = HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
