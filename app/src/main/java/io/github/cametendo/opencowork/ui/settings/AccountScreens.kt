package io.github.cametendo.opencowork.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.BuildConfig
import io.github.cametendo.opencowork.data.AuthState
import io.github.cametendo.opencowork.data.GitHubStatus
import io.github.cametendo.opencowork.ui.AppLogo
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.ConfirmDialog
import io.github.cametendo.opencowork.ui.components.LetterAvatar
import io.github.cametendo.opencowork.ui.components.ModelChip
import io.github.cametendo.opencowork.ui.components.rememberActions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
internal fun SubScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun FieldLabel(title: String, hint: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================================ chat defaults

@Composable
fun ChatDefaultsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val actions = rememberActions()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val models by container.models.models.collectAsStateWithLifecycle()
    val settings = (auth as? AuthState.SignedIn)?.user?.settings
    var system by rememberSaveable { mutableStateOf(settings?.systemPrompt.orEmpty()) }

    SubScreen("Chat", onBack) {
        FieldLabel("Default model", "Used for new chats when available.")
        ModelChip(models, settings?.defaultModel, { m ->
            actions.launch("Default model saved") { container.auth.saveSettings(buildJsonObject { put("default_model", m) }) }
        }, onRefresh = { container.models.load(true) })
        FieldLabel("System prompt", "Sent at the start of every chat (unless a chat overrides it).")
        OutlinedTextField(
            system, { system = it },
            placeholder = { Text("e.g. Answer concisely. I'm a software engineer.") },
            minLines = 5, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { actions.launch("System prompt saved") { container.auth.saveSettings(buildJsonObject { put("system_prompt", system) }) } },
            enabled = system != settings?.systemPrompt.orEmpty(),
            modifier = Modifier.align(Alignment.End),
        ) { Text("Save") }
    }
}

// ============================================================================ cowork defaults

@Composable
fun CoworkDefaultsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val actions = rememberActions()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val models by container.models.models.collectAsStateWithLifecycle()
    val settings = (auth as? AuthState.SignedIn)?.user?.settings
    var instructions by rememberSaveable { mutableStateOf(settings?.coworkInstructions.orEmpty()) }
    val mode = settings?.coworkApprovalMode ?: "ask"

    SubScreen("Cowork", onBack) {
        FieldLabel("Default Cowork model", "Cowork needs a model that supports tool calling (e.g. qwen3, llama3.1, gpt-oss).")
        ModelChip(models, settings?.coworkDefaultModel, { m ->
            actions.launch("Default Cowork model saved") { container.auth.saveSettings(buildJsonObject { put("cowork_default_model", m) }) }
        }, onRefresh = { container.models.load(true) })

        FieldLabel("Default approval mode", "Whether new tasks ask before editing files or running commands.")
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                listOf(
                    Triple("ask", "Ask before acting", "Approve each file edit and command"),
                    Triple("auto", "Run automatically", "Edits and commands run without asking"),
                ).forEach { (v, title, desc) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (v == "auto") Icons.Outlined.Bolt else Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(title)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(mode == v, onClick = {
                            actions.launch { container.auth.saveSettings(buildJsonObject { put("cowork_approval_mode", v) }) }
                        })
                    }
                }
            }
        }

        FieldLabel("Custom instructions", "Added to every Cowork task — your preferences, conventions, context about you.")
        OutlinedTextField(
            instructions, { instructions = it },
            placeholder = { Text("e.g. Use TypeScript and pnpm. Write tests for new code. Keep commit messages short.") },
            minLines = 5, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { actions.launch("Instructions saved") { container.auth.saveSettings(buildJsonObject { put("cowork_instructions", instructions) }) } },
            enabled = instructions != settings?.coworkInstructions.orEmpty(),
            modifier = Modifier.align(Alignment.End),
        ) { Text("Save") }
    }
}

// ============================================================================ GitHub

@Composable
fun GitHubScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val actions = rememberActions()
    val uri = LocalUriHandler.current
    var status by remember { mutableStateOf<GitHubStatus?>(null) }
    var token by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { actions.launch { status = container.api.githubStatus() } }

    SubScreen("GitHub", onBack) {
        val st = status
        if (st?.connected == true) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LetterAvatar(st.name ?: st.login ?: "?", 48)
                    Column(Modifier.weight(1f)) {
                        Text(st.name ?: st.login.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("@${st.login}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        st.scopes?.takeIf { it.isNotBlank() }?.let { Text("Scopes: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                st.htmlUrl?.let { url -> OutlinedButton(onClick = { runCatching { uri.openUri(url) } }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null); Text(" Profile") } }
                OutlinedButton(onClick = { disconnecting = true }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            }
            Text("Your token is stored encrypted on the server and only used for your own requests.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                "Connect GitHub to clone private repositories, push branches and open pull requests from Cowork. Public repositories can be cloned without a token.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FieldLabel("1. Create a token", "A classic token with the repo scope, or a fine-grained token with Contents and Pull requests set to read & write.")
            TextButton(onClick = { runCatching { uri.openUri("https://github.com/settings/tokens/new?scopes=repo&description=Open%20Cowork") } }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null); Text("  Create a token on GitHub")
            }
            FieldLabel("2. Paste it here")
            OutlinedTextField(
                token, { token = it.trim() },
                placeholder = { Text("ghp_… or github_pat_…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    busy = true
                    actions.launch("GitHub connected", onError = { busy = false }) {
                        status = container.api.githubConnect(token)
                        token = ""
                        busy = false
                        container.auth.refresh()
                    }
                },
                enabled = token.length >= 10 && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Connecting…" else "Connect") }
        }
    }

    if (disconnecting) {
        ConfirmDialog("Disconnect GitHub?", "The token is deleted from the server. Cloned repositories stay.", confirmLabel = "Disconnect", onDismiss = { disconnecting = false }) {
            actions.launch("GitHub disconnected") {
                container.api.githubDisconnect()
                status = container.api.githubStatus()
                container.auth.refresh()
            }
        }
    }
}

// ============================================================================ account

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val actions = rememberActions()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val user = (auth as? AuthState.SignedIn)?.user
    var name by rememberSaveable { mutableStateOf(user?.name.orEmpty()) }
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }

    SubScreen("Account", onBack) {
        FieldLabel("Name")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f))
            Button(
                onClick = { actions.launch("Profile updated") { val u = container.api.updateProfile(name.trim()); container.auth.updateUser { it.copy(name = u.name) } } },
                enabled = name.isNotBlank() && name.trim() != user?.name,
            ) { Text("Save") }
        }
        FieldLabel("Email")
        OutlinedTextField(user?.email.orEmpty(), {}, enabled = false, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Role")
        Text(user?.role?.replaceFirstChar { it.uppercase() }.orEmpty(), style = MaterialTheme.typography.bodyLarge)

        FieldLabel("Change password")
        OutlinedTextField(
            current, { current = it }, label = { Text("Current password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            next, { next = it }, label = { Text("New password (8+ characters)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { actions.launch("Password changed") { container.api.updatePassword(current, next); current = ""; next = "" } },
            enabled = current.isNotEmpty() && next.length >= 8,
            modifier = Modifier.align(Alignment.End),
        ) { Text("Change password") }
    }
}

// ============================================================================ about

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val signedIn = auth as? AuthState.SignedIn
    val uri = LocalUriHandler.current
    SubScreen("About", onBack) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLogo(72)
            Text("Open Cowork for Android", style = MaterialTheme.typography.titleLarge)
            Text("App version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            signedIn?.config?.let { Text("${it.name} server ${it.version}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            signedIn?.baseUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            "A self-hosted, Open WebUI–style workspace for local models (Ollama) and any OpenAI-compatible API, with Cowork: an agent that plans a task, then reads and edits files, runs commands and works in your GitHub repositories while you watch every step.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Replies and Cowork tasks keep running when you close the app: a small notification shows while something is working, and you get alerts when a task needs approval or finishes.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { runCatching { uri.openUri("https://github.com/Cametendo/open-cowork") } }) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null); Text("  Open Cowork on GitHub")
        }
    }
}
