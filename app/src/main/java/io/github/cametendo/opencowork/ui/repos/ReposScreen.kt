package io.github.cametendo.opencowork.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cametendo.opencowork.data.GitHubStatus
import io.github.cametendo.opencowork.data.LocalRepo
import io.github.cametendo.opencowork.data.RemoteRepo
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.ConfirmDialog
import io.github.cametendo.opencowork.ui.components.EmptyState
import io.github.cametendo.opencowork.ui.components.LetterAvatar
import io.github.cametendo.opencowork.ui.components.Pill
import io.github.cametendo.opencowork.ui.components.SectionHeader
import io.github.cametendo.opencowork.ui.components.relativeTime
import io.github.cametendo.opencowork.ui.components.rememberActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReposScreen(onStartTask: (String) -> Unit, onGitHubSettings: () -> Unit) {
    val api = LocalContainer.current.api
    val actions = rememberActions()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val toaster = io.github.cametendo.opencowork.ui.components.LocalToaster.current
    var gh by remember { mutableStateOf<GitHubStatus?>(null) }
    var local by remember { mutableStateOf<List<LocalRepo>?>(null) }
    var remote by remember { mutableStateOf<List<RemoteRepo>>(emptyList()) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf("") }
    var cloning by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<LocalRepo?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf<String?>(null) }

    suspend fun loadLocal() { runCatching { api.repos() }.onSuccess { local = it }.onFailure { if (local == null) local = emptyList() } }
    LaunchedEffect(Unit) {
        launch { runCatching { api.githubStatus() }.onSuccess { gh = it } }
        loadLocal()
    }
    LaunchedEffect(query, gh?.connected) {
        if (gh?.connected != true) return@LaunchedEffect
        if (query.isNotBlank()) delay(350)
        runCatching { api.githubRepos(query.ifBlank { null }) }
            .onSuccess { remote = it; remoteError = null }
            .onFailure { remoteError = it.message }
    }

    fun clone(name: String) {
        if (cloning != null) return
        cloning = name
        actions.launch(onError = { cloning = null }) {
            val r = api.cloneRepo(name)
            cloning = null
            manual = ""
            loadLocal()
            remote = remote.map { if (it.fullName == r.fullName) it.copy(localId = r.id) else it }
            toaster.show(if (r.alreadyCloned == true) "${r.fullName} is already cloned" else "Cloned ${r.fullName}")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Repositories") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; loadLocal(); runCatching { gh = api.githubStatus() }; refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                // ------------------------------------------------ GitHub account
                item {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LetterAvatar(gh?.login ?: "GH", 40)
                            Column(Modifier.weight(1f)) {
                                Text(if (gh?.connected == true) "Connected as ${gh?.login}" else "GitHub not connected", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (gh?.connected == true) "Clone private repos, push and open pull requests" else "Public repositories can be cloned without a token",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (gh?.connected == true) OutlinedButton(onClick = onGitHubSettings) { Text("Manage") }
                            else Button(onClick = onGitHubSettings) { Text("Connect") }
                        }
                    }
                }

                // ------------------------------------------------ cloned repos
                item { SectionHeader("Cloned repositories") }
                gh?.repoDir?.takeIf { it.isNotBlank() }?.let { dir ->
                    item {
                        Text("Stored in $dir", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
                when {
                    local == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    local!!.isEmpty() -> item { EmptyState(Icons.Outlined.FolderSpecial, "No repositories yet", "Clone one below, then start a Cowork task in it.") }
                    else -> items(local!!, key = { it.id }) { r ->
                        LocalRepoRow(
                            r,
                            pulling = pulling == r.id,
                            onStart = { onStartTask(r.id) },
                            onPull = {
                                pulling = r.id
                                actions.launch(onError = { pulling = null }) {
                                    val res = api.repoPull(r.id)
                                    pulling = null
                                    loadLocal()
                                    toaster.show(res.message.lines().lastOrNull { it.isNotBlank() } ?: "Up to date")
                                }
                            },
                            onOpen = { r.htmlUrl?.let { runCatching { uri.openUri(it) } } },
                            onRemove = { removing = r },
                        )
                    }
                }

                // ------------------------------------------------ clone by name
                item { SectionHeader("Clone a repository") }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manual, onValueChange = { manual = it.trim() },
                            placeholder = { Text("owner/repo or GitHub URL") },
                            leadingIcon = { Icon(Icons.Outlined.Link, null) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = { clone(manual) }, enabled = manual.isNotBlank() && cloning == null) {
                            if (cloning == manual && manual.isNotBlank()) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Clone")
                        }
                    }
                }

                // ------------------------------------------------ GitHub repos
                if (gh?.connected == true) {
                    item { SectionHeader("Your GitHub repositories") }
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Search ${gh?.login}'s repositories") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    remoteError?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) } }
                    items(remote, key = { "r-" + it.fullName }) { r ->
                        RemoteRepoRow(r, cloning = cloning == r.fullName, onClone = { clone(r.fullName) }, onStart = { r.localId?.let(onStartTask) })
                    }
                }
                item { Box(Modifier.padding(bottom = 32.dp)) }
            }
        }
    }

    removing?.let { r ->
        ConfirmDialog(
            "Remove ${r.fullName}?",
            "The repository is removed from Open Cowork.",
            confirmLabel = "Remove",
            checkboxLabel = "Also delete the files in ${r.path}",
            onDismiss = { removing = null },
        ) { files -> actions.launch("Removed ${r.fullName}") { api.removeRepo(r.id, files); loadLocal() } }
    }
}

@Composable
private fun LocalRepoRow(r: LocalRepo, pulling: Boolean, onStart: () -> Unit, onPull: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.FolderSpecial, null, tint = scheme.primary)
                Text(r.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (r.isPrivate) Icon(Icons.Outlined.Lock, "Private", Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Repository options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (r.htmlUrl != null) DropdownMenuItem(text = { Text("Open on GitHub") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) }, onClick = { menu = false; onOpen() })
                        DropdownMenuItem(
                            text = { Text("Remove", color = scheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = scheme.error) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountTree, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
                Text(r.currentBranch ?: r.defaultBranch, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (r.busy == true) Pill("Task running", scheme.primaryContainer, scheme.onPrimaryContainer)
                if (r.exists == false) Pill("Missing on disk", scheme.errorContainer, scheme.onErrorContainer)
                if (r.source == "local") Pill("Local", scheme.secondaryContainer, scheme.onSecondaryContainer)
                r.lastPulledAt?.let { Text("· pulled ${relativeTime(it)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, end = 12.dp)) {
                Button(onClick = onStart, enabled = r.exists != false) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Text(" Start task") }
                OutlinedButton(onClick = onPull, enabled = !pulling && r.exists != false) {
                    if (pulling) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(18.dp)); Text(" Pull") }
                }
            }
        }
    }
}

@Composable
private fun RemoteRepoRow(r: RemoteRepo, cloning: Boolean, onClone: () -> Unit, onStart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.fullName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.isPrivate) Icon(Icons.Outlined.Lock, "Private", Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
            }
            r.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                r.language?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
                if (r.stargazersCount > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Star, null, Modifier.size(12.dp), tint = scheme.onSurfaceVariant)
                    Text(" ${r.stargazersCount}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        if (r.localId != null) FilledTonalButton(onClick = onStart) { Text("Start task") }
        else OutlinedButton(onClick = onClone, enabled = !cloning) {
            if (cloning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp)); Text(" Clone") }
        }
    }
}
