package io.github.cametendo.opencowork.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.cametendo.opencowork.data.PublicConfig
import io.github.cametendo.opencowork.data.ServerAddress
import io.github.cametendo.opencowork.data.ServerForm
import io.github.cametendo.opencowork.ui.AppLogo
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.ErrorBox
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(message: String?) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(ServerAddress.Mode.IP) }
    var host by rememberSaveable { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(ServerAddress.DEFAULT_PORT) }
    var https by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var signup by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(message) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var probe by rememberSaveable { mutableStateOf<String?>(null) }
    var probing by rememberSaveable { mutableStateOf(false) }
    var serverConfig by rememberSaveable { mutableStateOf<String?>(null) } // "name|version|onboarding|signup"

    // Pre-fill with what was used last time
    LaunchedEffect(Unit) {
        if (prefilled) return@LaunchedEffect
        val s = container.sessionStore.current()
        mode = s.form.mode
        if (s.form.mode == ServerAddress.Mode.IP) host = s.form.host else domain = s.form.host
        port = s.form.port
        https = if (s.form.host.isEmpty()) false else s.form.https
        email = s.email
        prefilled = true
    }

    val currentHost = if (mode == ServerAddress.Mode.IP) host else domain
    val form = ServerForm(mode, currentHost, port, https)
    val preview = runCatching { ServerAddress.build(mode, currentHost, port, https) }.getOrNull()
    val onboarding = serverConfig?.split("|")?.getOrNull(2) == "true"
    val signupAllowed = serverConfig?.split("|")?.getOrNull(3) != "false"

    fun describe(cfg: PublicConfig) = "${cfg.name}|${cfg.version}|${cfg.onboarding}|${cfg.features.enableSignup}"

    fun submit() {
        if (busy) return
        error = null
        busy = true
        scope.launch {
            error = container.auth.login(form, email, password, name, signup || onboarding)
            busy = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), MaterialTheme.colorScheme.background)),
        ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppLogo(72)
            Text("Open Cowork", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in to your self-hosted server to chat with your models and run Cowork tasks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            // ------------------------------------------------------------ server
            Card("Server", Icons.Outlined.Dns) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == ServerAddress.Mode.IP,
                        onClick = { if (mode != ServerAddress.Mode.IP) { mode = ServerAddress.Mode.IP; https = false; probe = null; serverConfig = null } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("IP + Port") }
                    SegmentedButton(
                        selected = mode == ServerAddress.Mode.DOMAIN,
                        onClick = { if (mode != ServerAddress.Mode.DOMAIN) { mode = ServerAddress.Mode.DOMAIN; https = true; probe = null; serverConfig = null } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Domain") }
                }
                if (mode == ServerAddress.Mode.IP) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = host, onValueChange = { host = it.trim(); probe = null },
                            label = { Text("IP address") }, placeholder = { Text("192.168.1.20") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port, onValueChange = { v -> port = v.filter(Char::isDigit).take(5); probe = null },
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.width(104.dp),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = domain, onValueChange = { domain = it.trim(); probe = null },
                        label = { Text("Domain") }, placeholder = { Text("cowork.example.com") },
                        leadingIcon = { Icon(Icons.Outlined.Language, null) },
                        singleLine = true,
                        supportingText = { Text("No port needed") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use HTTPS", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            preview?.let { "Connects to $it" } ?: "Enter the server address",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = https, onCheckedChange = { https = it; probe = null })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = preview != null && !probing,
                        onClick = {
                            probing = true
                            scope.launch {
                                container.auth.probe(form)
                                    .onSuccess { (_, cfg) ->
                                        serverConfig = describe(cfg)
                                        probe = "Connected to ${cfg.name}" + (if (cfg.version.isNotBlank()) " ${cfg.version}" else "")
                                        if (cfg.onboarding) signup = true
                                    }
                                    .onFailure { probe = null; error = it.message }
                                probing = false
                            }
                        },
                    ) {
                        if (probing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test connection")
                    }
                    probe?.let {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ------------------------------------------------------------ account
            Card(if (onboarding) "Create the admin account" else "Account", Icons.Outlined.Person) {
                if (onboarding) {
                    Text(
                        "This server has no accounts yet. The first account becomes the administrator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (signupAllowed) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !signup, onClick = { signup = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Sign in") }
                        SegmentedButton(selected = signup, onClick = { signup = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Create account") }
                    }
                }
                AnimatedVisibility(signup || onboarding) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Name") },
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim() },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) "Hide password" else "Show password")
                        }
                    },
                    supportingText = if (signup || onboarding) ({ Text("At least 8 characters") }) else null,
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let { ErrorBox(it) }

            val ready = preview != null && email.isNotBlank() && password.isNotEmpty() &&
                (!(signup || onboarding) || (name.isNotBlank() && password.length >= 8))
            Button(
                onClick = { submit() },
                enabled = ready && !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (signup || onboarding) "Create account" else "Sign in", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Card(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 1.dp, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
