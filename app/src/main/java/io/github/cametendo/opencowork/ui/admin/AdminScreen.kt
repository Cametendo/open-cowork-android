package io.github.cametendo.opencowork.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.AdminConfig
import io.github.cametendo.opencowork.data.AuthState
import io.github.cametendo.opencowork.data.Connection
import io.github.cametendo.opencowork.data.User
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.CenteredLoading
import io.github.cametendo.opencowork.ui.components.ConfirmDialog
import io.github.cametendo.opencowork.ui.components.LetterAvatar
import io.github.cametendo.opencowork.ui.components.LocalToaster
import io.github.cametendo.opencowork.ui.components.Pill
import io.github.cametendo.opencowork.ui.components.SettingRow
import io.github.cametendo.opencowork.ui.components.SettingsCard
import io.github.cametendo.opencowork.ui.components.SwitchRow
import io.github.cametendo.opencowork.ui.components.relativeTime
import io.github.cametendo.opencowork.ui.components.rememberActions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val Tabs = listOf("Users", "Connections", "General", "Cowork & GitHub", "Web search")

@Composable
fun AdminScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var addRequest by remember { mutableStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                    title = { Text("Admin") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 12.dp) {
                    Tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
            }
        },
        floatingActionButton = {
            if (tab <= 1) ExtendedFloatingActionButton(
                onClick = { addRequest++ },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(if (tab == 0) "Add user" else "Add connection") },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> UsersTab(addRequest)
                1 -> ConnectionsTab(addRequest)
                2 -> ConfigTab(general = true)
                3 -> ConfigTab(general = false)
                else -> WebSearchTab()
            }
        }
    }
}

// ============================================================================ users

@Composable
private fun UsersTab(addRequest: Int) {
    val container = LocalContainer.current
    val api = container.api
    val actions = rememberActions()
    val me = (container.auth.state.collectAsStateWithLifecycle().value as? AuthState.SignedIn)?.user
    var users by remember { mutableStateOf<List<User>?>(null) }
    var editing by remember { mutableStateOf<User?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<User?>(null) }
    var lastAdd by remember { mutableStateOf(addRequest) }
    LaunchedEffect(addRequest) { if (addRequest != lastAdd) { lastAdd = addRequest; creating = true } }

    suspend fun load() { users = api.users() }
    LaunchedEffect(Unit) { actions.launch { load() } }

    val list = users
    if (list == null) { CenteredLoading(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        val pending = list.count { it.role == "pending" }
        if (pending > 0) item {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "$pending account${if (pending > 1) "s are" else " is"} waiting for activation. Change the role to User to let them in.",
                    color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(12.dp),
                )
            }
        }
        items(list, key = { it.id }) { u ->
            var menu by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.fillMaxWidth().clickable { editing = u }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LetterAvatar(u.name, 40)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(u.name + if (u.id == me?.id) " (you)" else "", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                            RolePill(u.role)
                        }
                        Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Active ${relativeTime(u.lastActiveAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "User options") }
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                    listOf("admin" to "Make admin", "user" to "Make user", "pending" to "Set pending").filter { it.first != u.role }.forEach { (role, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menu = false
                            actions.launch("Role updated") { api.updateUser(u.id, buildJsonObject { put("role", role) }); load() }
                        })
                    }
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; editing = u })
                    if (u.id != me?.id) DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; deleting = u },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        UserDialog(editing, onDismiss = { creating = false; editing = null }) { name, email, password, role ->
            val target = editing
            actions.launch(if (target == null) "User added" else "User updated") {
                if (target == null) api.createUser(name, email, password, role)
                else api.updateUser(target.id, buildJsonObject {
                    put("name", name); put("email", email); put("role", role)
                    if (password.isNotEmpty()) put("password", password)
                })
                load()
            }
        }
    }
    deleting?.let { u ->
        ConfirmDialog("Delete ${u.name}?", "Their chats and Cowork tasks are deleted too.", onDismiss = { deleting = null }) {
            actions.launch("User deleted") { api.deleteUser(u.id); load() }
        }
    }
}

@Composable
private fun RolePill(role: String) {
    val s = MaterialTheme.colorScheme
    when (role) {
        "admin" -> Pill("Admin", s.primary, s.onPrimary)
        "user" -> Pill("User", s.secondaryContainer, s.onSecondaryContainer)
        else -> Pill("Pending", s.tertiaryContainer, s.onTertiaryContainer)
    }
}

@Composable
private fun UserDialog(user: User?, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(user?.name.orEmpty()) }
    var email by remember { mutableStateOf(user?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(user?.role ?: "user") }
    val valid = name.isNotBlank() && email.contains("@") && (if (user == null) password.length >= 8 else password.isEmpty() || password.length >= 8)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) "Add user" else "Edit user") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Password") },
                    supportingText = { Text(if (user != null) "Leave empty to keep the current password" else "At least 8 characters") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Role", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("admin" to "Admin", "user" to "User", "pending" to "Pending").forEachIndexed { i, (v, l) ->
                        SegmentedButton(selected = role == v, onClick = { role = v }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), email, password, role); onDismiss() }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ============================================================================ connections

private data class Preset(val name: String, val type: String, val url: String)

private val Presets = listOf(
    Preset("Ollama", "ollama", "http://localhost:11434"),
    Preset("OpenAI", "openai", "https://api.openai.com/v1"),
    Preset("LM Studio", "openai", "http://localhost:1234/v1"),
    Preset("vLLM", "openai", "http://localhost:8000/v1"),
    Preset("llama.cpp", "openai", "http://localhost:8080/v1"),
    Preset("OpenRouter", "openai", "https://openrouter.ai/api/v1"),
)

@Composable
private fun ConnectionsTab(addRequest: Int) {
    val container = LocalContainer.current
    val api = container.api
    val actions = rememberActions()
    val toaster = LocalToaster.current
    var conns by remember { mutableStateOf<List<Connection>?>(null) }
    var editing by remember { mutableStateOf<Connection?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Connection?>(null) }
    var testing by remember { mutableStateOf<String?>(null) }
    var lastAdd by remember { mutableStateOf(addRequest) }
    LaunchedEffect(addRequest) { if (addRequest != lastAdd) { lastAdd = addRequest; creating = true } }

    suspend fun load() {
        conns = api.connections()
        container.models.load(true)
    }
    LaunchedEffect(Unit) { actions.launch { conns = api.connections() } }

    val list = conns
    if (list == null) { CenteredLoading(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp)) {
        if (list.isEmpty()) item {
            Text("No connections yet. Add Ollama or an OpenAI-compatible API to get models.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(list, key = { it.id }) { c ->
            var menu by remember { mutableStateOf(false) }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            Pill(if (c.type == "ollama") "Ollama" else "OpenAI", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Text(c.baseUrl, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val extra = listOfNotNull(
                            if (c.hasApiKey) "API key set" else null,
                            c.prefixId?.let { "prefix: $it" },
                            if (c.modelIds.isNotEmpty()) "${c.modelIds.size} allowed models" else null,
                        )
                        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (testing == c.id) CircularProgressIndicator(Modifier.padding(8.dp).size(18.dp), strokeWidth = 2.dp)
                    Switch(c.enabled, { on -> actions.launch { api.updateConnection(c.id, buildJsonObject { put("enabled", on) }); load() } })
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Connection options") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Test connection") }, leadingIcon = { Icon(Icons.Outlined.NetworkCheck, null) }, onClick = {
                                menu = false
                                testing = c.id
                                actions.launch(onError = { testing = null }) {
                                    val r = api.verifyConnection(c.type, c.baseUrl, null, c.id)
                                    testing = null
                                    toaster.show("Connected · ${r.models.size} model${if (r.models.size == 1) "" else "s"}")
                                }
                            })
                            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; editing = c })
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; deleting = c },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        ConnectionDialog(editing, onDismiss = { creating = false; editing = null }) { body ->
            val target = editing
            actions.launch(if (target == null) "Connection added" else "Connection saved") {
                if (target == null) api.createConnection(body) else api.updateConnection(target.id, body)
                load()
            }
        }
    }
    deleting?.let { c ->
        ConfirmDialog("Delete ${c.name}?", "Its models disappear from the model list.", onDismiss = { deleting = null }) {
            actions.launch("Connection deleted") { api.deleteConnection(c.id); load() }
        }
    }
}

@Composable
private fun ConnectionDialog(conn: Connection?, onDismiss: () -> Unit, onSave: (JsonObject) -> Unit) {
    val api = LocalContainer.current.api
    val actions = rememberActions()
    var type by remember { mutableStateOf(conn?.type ?: "ollama") }
    var name by remember { mutableStateOf(conn?.name ?: "Ollama") }
    var url by remember { mutableStateOf(conn?.baseUrl ?: "http://localhost:11434") }
    var key by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf(conn?.prefixId.orEmpty()) }
    var allow by remember { mutableStateOf(conn?.modelIds?.joinToString(", ").orEmpty()) }
    var enabled by remember { mutableStateOf(conn?.enabled ?: true) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (conn == null) "Add connection" else "Edit connection") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (conn == null) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Presets.forEach { p ->
                            FilterChip(selected = name == p.name && url == p.url, onClick = { name = p.name; type = p.type; url = p.url }, label = { Text(p.name) })
                        }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = type == "ollama", onClick = { type = "ollama" }, shape = SegmentedButtonDefaults.itemShape(0, 2), enabled = conn == null) { Text("Ollama") }
                    SegmentedButton(selected = type == "openai", onClick = { type = "openai" }, shape = SegmentedButtonDefaults.itemShape(1, 2), enabled = conn == null) { Text("OpenAI-compatible") }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it.trim() }, label = { Text("Base URL") }, singleLine = true,
                    supportingText = { Text(if (type == "openai") "Usually ends in /v1" else "In Docker, use http://host.docker.internal:11434") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    key, { key = it.trim() }, label = { Text("API key") }, singleLine = true,
                    supportingText = { Text(if (conn?.hasApiKey == true) "A key is stored. Leave empty to keep it." else "Optional for local servers.") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(prefix, { prefix = it.trim() }, label = { Text("Prefix (optional)") }, supportingText = { Text("Keeps model ids unique") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(allow, { allow = it }, label = { Text("Model allow-list") }, supportingText = { Text("Comma-separated; empty = all") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", modifier = Modifier.weight(1f))
                    Switch(enabled, { enabled = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !testing && url.isNotBlank(), onClick = {
                        testing = true
                        testResult = null
                        actions.launch(onError = { testing = false }) {
                            val r = api.verifyConnection(type, url, key.ifBlank { null }, conn?.id)
                            testing = false
                            testResult = "✓ ${r.models.size} model${if (r.models.size == 1) "" else "s"}" + if (r.models.isNotEmpty()) ": " + r.models.take(4).joinToString(", ") + if (r.models.size > 4) "…" else "" else ""
                        }
                    }) {
                        Icon(Icons.Outlined.Power, null, Modifier.size(18.dp)); Text(if (testing) "  Testing…" else "  Test connection")
                    }
                }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    val body = buildJsonObject {
                        if (conn == null) put("type", type)
                        put("name", name.trim())
                        put("base_url", url)
                        if (key.isNotBlank()) put("api_key", key)
                        put("prefix_id", prefix)
                        putJsonArray("model_ids") { allow.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } }
                        put("enabled", enabled)
                    }
                    onSave(body)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ============================================================================ config

@Composable
private fun ConfigTab(general: Boolean) {
    val container = LocalContainer.current
    val api = container.api
    val actions = rememberActions()
    var cfg by remember { mutableStateOf<AdminConfig?>(null) }
    LaunchedEffect(Unit) { actions.launch { cfg = api.adminConfig() } }
    val c = cfg
    if (c == null) { CenteredLoading(); return }

    fun save(patch: JsonObject, msg: String = "Saved") = actions.launch(msg) {
        cfg = api.updateAdminConfig(patch)
        if (patch.containsKey("cowork_enabled") || patch.containsKey("github_enabled") || patch.containsKey("webui_name")) container.auth.refresh()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (general) {
            var name by remember(c.webuiName) { mutableStateOf(c.webuiName) }
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Instance name", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(onClick = { save(buildJsonObject { put("webui_name", name) }) }, enabled = name != c.webuiName) { Text("Save") }
                    }
                }
            }
            SettingsCard {
                SwitchRow("Allow new sign-ups", "When off, only admins can create accounts. The very first account can always sign up.", c.enableSignup) {
                    save(buildJsonObject { put("enable_signup", it) })
                }
                SettingRow("Default role for new accounts", "Pending accounts must be activated by an admin before they can use the app.")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    listOf("pending" to "Pending", "user" to "User").forEachIndexed { i, (v, l) ->
                        SegmentedButton(selected = c.defaultUserRole == v, onClick = { save(buildJsonObject { put("default_user_role", v) }) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                    }
                }
                SwitchRow("Automatic titles", "Ask the model for a short title after the first reply.", c.enableTitleGeneration) {
                    save(buildJsonObject { put("enable_title_generation", it) })
                }
            }
        } else {
            SettingsCard {
                SwitchRow("Enable Cowork for users", "Admins always have access.", c.coworkEnabled) { save(buildJsonObject { put("cowork_enabled", it) }) }
                SwitchRow(
                    "Allow shell commands",
                    "Lets the agent run commands (tests, builds, git…) in the task's workspace with the server's permissions. Turn off to limit it to reading and editing files.",
                    c.coworkAllowShell,
                ) { save(buildJsonObject { put("cowork_allow_shell", it) }) }
            }
            SettingsCard {
                NumberSetting("Command timeout", "Seconds before a command is stopped.", c.coworkCommandTimeout, min = 1) { save(buildJsonObject { put("cowork_command_timeout", it) }) }
                NumberSetting("Max steps per run", "Model calls per run before the agent pauses and asks to continue.", c.coworkMaxSteps, min = 1) { save(buildJsonObject { put("cowork_max_steps", it) }) }
                NumberSetting("Ollama context length", "num_ctx for Cowork runs on Ollama. Agents need room for tool output; 0 uses the model default.", c.coworkOllamaNumCtx, min = 0) {
                    save(buildJsonObject { put("cowork_ollama_num_ctx", it) })
                }
            }
            SettingsCard {
                SwitchRow("GitHub integration", "Let users connect GitHub, clone repositories and open pull requests.", c.githubEnabled) { save(buildJsonObject { put("github_enabled", it) }) }
            }
        }
    }
}

@Composable
private fun NumberSetting(title: String, description: String, value: Int, min: Int, onSave: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text, { text = it.filter(Char::isDigit) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = parsed == null || parsed < min,
                modifier = Modifier.width(160.dp),
            )
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed >= min && parsed != value) { Text("Save") }
        }
    }
}

// ============================================================================ web search

private val Engines = listOf(
    Triple("duckduckgo", "DuckDuckGo", "No key needed, but DuckDuckGo may rate-limit automated searches"),
    Triple("searxng", "SearXNG", "Your own server. Enable the JSON format in its settings.yml"),
    Triple("brave", "Brave Search", "Needs an API key (has a free tier)"),
    Triple("tavily", "Tavily", "Needs an API key (has a free tier)"),
)

@Composable
private fun WebSearchTab() {
    val container = LocalContainer.current
    val api = container.api
    val actions = rememberActions()
    var cfg by remember { mutableStateOf<AdminConfig?>(null) }
    LaunchedEffect(Unit) { actions.launch { cfg = api.adminConfig() } }
    val c = cfg
    if (c == null) { CenteredLoading(); return }

    fun save(patch: JsonObject, msg: String = "Saved") = actions.launch(msg) {
        cfg = api.updateAdminConfig(patch)
        container.auth.refresh()
    }

    var url by remember(c.webSearchUrl) { mutableStateOf(c.webSearchUrl) }
    var key by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val needsKey = c.webSearchEngine == "brave" || c.webSearchEngine == "tavily"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard {
            SwitchRow(
                "Enable web search",
                "Off by default: search queries, and the pages Cowork reads, leave your server. When on, chats get a Web search switch and Cowork gets web_search and fetch_url.",
                c.webSearchEnabled,
            ) { save(buildJsonObject { put("web_search_enabled", it) }) }
        }
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Search engine", style = MaterialTheme.typography.titleSmall)
                Engines.forEach { (id, name, hint) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { save(buildJsonObject { put("web_search_engine", id) }) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(c.webSearchEngine == id, onClick = { save(buildJsonObject { put("web_search_engine", id) }) })
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (c.webSearchEngine == "searxng") {
                    OutlinedTextField(
                        url, { url = it.trim() }, label = { Text("SearXNG URL") }, singleLine = true,
                        placeholder = { Text("http://localhost:8888") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { save(buildJsonObject { put("web_search_url", url) }) }, enabled = url != c.webSearchUrl) { Text("Save URL") }
                }
                if (needsKey) {
                    OutlinedTextField(
                        key, { key = it.trim() }, label = { Text("API key") }, singleLine = true,
                        supportingText = { Text(if (c.webSearchHasKey) "A key is stored. Leave empty to keep it." else "Stored encrypted and never sent back.") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        TextButton(onClick = { save(buildJsonObject { put("web_search_api_key", key) }, "Key saved"); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
                        if (c.webSearchHasKey) TextButton(onClick = { save(buildJsonObject { put("web_search_api_key", "") }, "Key removed") }) {
                            Text("Remove key", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !testing, onClick = {
                        testing = true; result = null
                        actions.launch(onError = { testing = false }) {
                            val r = api.testWebSearch(c.webSearchEngine, url, key.ifBlank { null })
                            testing = false
                            result = "✓ ${r.count} result${if (r.count == 1) "" else "s"}" + r.results.firstOrNull()?.let { ": ${it.title.ifBlank { it.host }}" }.orEmpty()
                        }
                    }) {
                        Icon(Icons.Outlined.NetworkCheck, null, Modifier.size(18.dp)); Text(if (testing) "  Testing…" else "  Test search")
                    }
                }
                result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        SettingsCard {
            NumberSetting("Results per search", "How many results are used (1–10).", c.webSearchResultCount, min = 1) {
                if (it in 1..10) save(buildJsonObject { put("web_search_result_count", it) }) else actions.launch { error("Choose a number from 1 to 10") }
            }
            SwitchRow(
                "Allow fetching private addresses",
                "By default fetch_url only opens public internet pages, so the agent can't reach localhost, your LAN or Tailscale machines. Turn on only if Cowork should read internal pages.",
                c.webAllowPrivateHosts,
            ) { save(buildJsonObject { put("web_allow_private_hosts", it) }) }
        }
        Text(
            "Web pages are untrusted input. Models are told not to follow instructions found in them, but a page can still try to steer the agent, so keep Ask first on for tasks that read the web.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
