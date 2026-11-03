package io.github.cametendo.opencowork.ui.cowork

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.DirListing
import io.github.cametendo.opencowork.data.FilePreview
import io.github.cametendo.opencowork.data.GitFile
import io.github.cametendo.opencowork.data.GitStatus
import io.github.cametendo.opencowork.data.LiveSession
import io.github.cametendo.opencowork.data.PullRequest
import io.github.cametendo.opencowork.data.UploadSource
import io.github.cametendo.opencowork.data.touchedFiles
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.CenteredLoading
import io.github.cametendo.opencowork.ui.components.DiffStat
import io.github.cametendo.opencowork.ui.components.DiffView
import io.github.cametendo.opencowork.ui.components.EmptyState
import io.github.cametendo.opencowork.ui.components.ErrorBox
import io.github.cametendo.opencowork.ui.components.LocalToaster
import io.github.cametendo.opencowork.ui.components.formatBytes
import io.github.cametendo.opencowork.ui.components.rememberActions
import io.github.cametendo.opencowork.ui.theme.LocalChatColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WorkspaceScreen(id: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val liveMap by container.cowork.live.collectAsStateWithLifecycle()
    val live = liveMap[id]
    LaunchedEffect(id) { if (container.cowork.live.value[id] == null) runCatching { container.cowork.open(id) } }
    val isRepo = live?.session?.workspaceType == "repo" && live.session.repoId != null
    val tabs = buildList { add("Progress"); add("Files"); if (isRepo) add("Git") }
    var tab by rememberSaveable { mutableStateOf(0) }
    // Bumps whenever a tool finishes, so the file list and git status refresh.
    val version = live?.let { l -> l.messages.count { it.role == "tool" } + if (l.running) 0 else 1000 } ?: 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                    title = {
                        Column {
                            Text("Workspace", style = MaterialTheme.typography.titleMedium)
                            Text(live?.session?.title.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                PrimaryTabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex), containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEachIndexed { i, t ->
                        Tab(
                            selected = tab == i, onClick = { tab = i },
                            text = { Text(t) },
                            icon = {
                                Icon(
                                    when (t) { "Progress" -> Icons.Outlined.Checklist; "Files" -> Icons.Outlined.Folder; else -> Icons.Outlined.AccountTree },
                                    null,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (live == null) CenteredLoading()
            else when (tabs.getOrNull(tab)) {
                "Progress" -> ProgressTab(live)
                "Files" -> FilesTab(id, version)
                "Git" -> GitTab(live.session.repoId!!, live.session.title, live.session.baseBranch, version, live.running)
            }
        }
    }
}

// ============================================================================ progress

@Composable
private fun ProgressTab(live: LiveSession) {
    val s = live.session
    val files = remember(live.messages) { touchedFiles(live.messages) }
    val commands = live.messages.count { it.role == "tool" && it.name == "run_command" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Section("Plan") {
            if (live.plan.isEmpty()) Muted(if (live.running) "No plan yet — Cowork creates one for multi-step tasks." else "No plan for this task.")
            else {
                PlanProgress(live.plan)
                PlanList(live.plan)
            }
        }
        Section("Changed files") {
            if (files.isEmpty()) Muted("No files changed yet.")
            files.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Difference, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(f.path, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (f.created) Text("new", color = LocalChatColors.current.success, style = MaterialTheme.typography.labelSmall)
                    DiffStat(f.added, f.removed)
                }
            }
        }
        Section("Workspace") {
            s.repoName?.let { InfoRow("Repo", it) }
            s.branch?.let { InfoRow("Branch", it, mono = true) }
            s.baseBranch?.let { InfoRow("Base", it, mono = true) }
            InfoRow("Folder", s.workspacePath, mono = true)
            InfoRow("Activity", "${files.size} file${if (files.size == 1) "" else "s"} changed · $commands command${if (commands == 1) "" else "s"} run")
            InfoRow("Approval", if (s.approvalMode == "auto") "Run automatically" else "Ask before acting")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
        Text(value, style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium)
    }
}

// ============================================================================ files

@Composable
private fun FilesTab(sessionId: String, version: Int) {
    val api = LocalContainer.current.api
    val context = LocalContext.current
    val actions = rememberActions()
    val toaster = LocalToaster.current
    var path by rememberSaveable { mutableStateOf(".") }
    var listing by remember { mutableStateOf<DirListing?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var uploading by remember { mutableStateOf(false) }

    LaunchedEffect(path, version, reload) {
        try {
            listing = api.coworkFiles(sessionId, path)
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uploading = true
        actions.launch(onError = { uploading = false }) {
            val sources = withContext(Dispatchers.IO) { uris.map { uploadSource(context, it) } }
            val r = api.coworkUpload(sessionId, sources, path)
            uploading = false
            reload++
            toaster.show("Uploaded ${r.saved.size} file${if (r.saved.size == 1) "" else "s"}")
        }
    }

    preview?.let { p ->
        FilePreviewView(sessionId, p, onBack = { preview = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                val parts = if (path == "." || path.isBlank()) emptyList() else path.split("/")
                TextButton(onClick = { path = "." }) { Text("root") }
                parts.forEachIndexed { i, part ->
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { path = parts.take(i + 1).joinToString("/") }) { Text(part, maxLines = 1) }
                }
            }
            if (uploading) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { upload.launch(arrayOf("*/*")) }) { Icon(Icons.Outlined.FileUpload, "Upload files here") }
            IconButton(onClick = { reload++ }) { Icon(Icons.Outlined.Refresh, "Refresh") }
        }
        listing?.root?.let {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
        when {
            error != null -> ErrorBox(error!!, Modifier.padding(16.dp))
            listing == null -> CenteredLoading()
            listing!!.entries.isEmpty() -> EmptyState(Icons.Outlined.Folder, "This folder is empty", "Upload files or let Cowork create some.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(listing!!.entries, key = { it.path }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (e.isDir) path = e.path else preview = e.path }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            when { e.isDir && e.ignored -> Icons.Outlined.FolderOff; e.isDir -> Icons.Outlined.Folder; else -> Icons.AutoMirrored.Outlined.InsertDriveFile },
                            null,
                            tint = if (e.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(e.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (e.ignored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        if (!e.isDir) Text(formatBytes(e.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { Box(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

private fun uploadSource(context: android.content.Context, uri: Uri): UploadSource {
    val resolver = context.contentResolver
    var name = "upload"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    return UploadSource(name, size, resolver.getType(uri)) { resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(name) }
}

@Composable
private fun FilePreviewView(sessionId: String, path: String, onBack: () -> Unit) {
    val api = LocalContainer.current.api
    val context = LocalContext.current
    val actions = rememberActions()
    var file by remember { mutableStateOf<FilePreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        try { file = api.coworkFile(sessionId, path) } catch (e: Exception) { error = e.message }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) actions.launch("Downloaded ${path.substringAfterLast('/')}") {
            api.coworkDownload(sessionId, path) { input ->
                context.contentResolver.openOutputStream(uri)?.use { out -> input.copyTo(out) } ?: throw java.io.IOException("Couldn't write the file")
            }
        }
    }
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to files") }
            Column(Modifier.weight(1f)) {
                Text(path, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                file?.let { Text("${formatBytes(it.size)} · ${it.mime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = { save.launch(path.substringAfterLast('/')) }) { Icon(Icons.Outlined.CloudDownload, "Download") }
        }
        HorizontalDivider()
        when {
            error != null -> ErrorBox(error!!, Modifier.padding(16.dp))
            file == null -> CenteredLoading()
            file!!.binary || file!!.content == null -> EmptyState(Icons.AutoMirrored.Outlined.InsertDriveFile, "Binary file", "Download it to open it on your device.")
            else -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        file!!.content!! + if (file!!.truncated) "\n\n… (preview truncated)" else "",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, softWrap = false,
                        modifier = Modifier.padding(12.dp).navigationBarsPadding(),
                    )
                }
            }
        }
    }
}

// ============================================================================ git

@Composable
fun GitTab(repoId: String, taskTitle: String, baseBranch: String?, version: Int, running: Boolean) {
    val api = LocalContainer.current.api
    val actions = rememberActions()
    val toaster = LocalToaster.current
    val uri = LocalUriHandler.current
    var status by remember { mutableStateOf<GitStatus?>(null) }
    var files by remember { mutableStateOf<List<GitFile>>(emptyList()) }
    var pr by remember { mutableStateOf<PullRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var prOpen by remember { mutableStateOf(false) }

    LaunchedEffect(repoId, version, reload) {
        try {
            status = api.repoStatus(repoId)
            files = api.repoChanges(repoId).files
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(repoId, status?.branch) { runCatching { pr = api.repoPullRequest(repoId).pullRequest } }

    fun act(name: String, ok: String?, block: suspend () -> Unit) {
        busy = name
        actions.launch(ok, onError = { busy = null }) { block(); busy = null; reload++ }
    }

    val st = status
    when {
        error != null -> { ErrorBox(error!!, Modifier.padding(16.dp)); return }
        st == null -> { CenteredLoading(); return }
    }
    st!!
    val repo = st.repo
    val isGitHub = repo?.source == "github"
    val base = baseBranch ?: repo?.defaultBranch ?: "main"
    val onBase = st.branch == base

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AccountTree, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(st.branch ?: "detached HEAD", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { reload++ }) { Icon(Icons.Outlined.Refresh, "Refresh") }
            }
            Text(
                listOfNotNull(
                    st.upstream?.let { "↔ $it" } ?: "Not pushed yet",
                    if (st.ahead > 0) "${st.ahead} ahead" else null,
                    if (st.behind > 0) "${st.behind} behind" else null,
                    if (!onBase) "base: $base" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pr?.let { p ->
                Surface(onClick = { runCatching { uri.openUri(p.htmlUrl) } }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("#${p.number} ${p.title}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open pull request", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Text(
                    if (files.isEmpty()) "No uncommitted changes" else "${files.size} changed file${if (files.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                )
            }
            items(files, key = { it.path }) { f -> GitFileRow(f) }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (files.isNotEmpty()) {
                    OutlinedTextField(message, { message = it }, placeholder = { Text("Commit message") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { act("commit", "Committed") { api.repoCommit(repoId, message.trim()); message = "" } },
                        enabled = message.isNotBlank() && !running && busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy == "commit") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.Commit, null); Text("  Commit all changes") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { act("pull", "Up to date") { api.repoPull(repoId) } }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                        if (busy == "pull") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.ArrowDownward, null); Text(" Pull") }
                    }
                    FilledTonalButton(
                        onClick = { act("push", "Pushed") { api.repoPush(repoId) } },
                        enabled = busy == null && st.branch != null && !(st.upstream != null && st.ahead == 0),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (busy == "push") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.ArrowUpward, null); Text(" Push") }
                    }
                }
                if (isGitHub && pr == null && !onBase) {
                    Button(onClick = { prOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Create pull request") }
                }
                if (running) Text("Cowork is working — commit when it's done.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    if (prOpen) PullRequestDialog(taskTitle, base, onDismiss = { prOpen = false }) { title, body, b, draft ->
        actions.launch {
            val r = api.createPullRequest(repoId, title, body, b, draft)
            pr = r.pullRequest
            r.pullRequest?.let { p -> toaster.show(if (r.existing) "Pull request #${p.number} already exists" else "Opened pull request #${p.number}") }
        }
    }
}

@Composable
private fun GitFileRow(f: GitFile) {
    val chat = LocalChatColors.current
    var open by remember { mutableStateOf(false) }
    val color = when (f.status) {
        "M" -> chat.warning
        "A", "?" -> chat.success
        "D", "U" -> chat.diffRemoved
        else -> MaterialTheme.colorScheme.primary
    }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (f.status == "?") "U" else f.status, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(f.path, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            DiffStat(f.added, f.removed)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
        }
        if (open) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                when {
                    f.binary == true -> Text("Binary file", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    f.note != null -> Text(f.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    else -> DiffView(f.diff.orEmpty())
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun PullRequestDialog(defaultTitle: String, base: String, onDismiss: () -> Unit, onCreate: (String, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf(defaultTitle) }
    var body by remember { mutableStateOf("") }
    var baseBranch by remember { mutableStateOf(base) }
    var draft by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open a pull request") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Description") }, placeholder = { Text("What changed and why") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(baseBranch, { baseBranch = it }, label = { Text("Base branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Draft", modifier = Modifier.weight(1f))
                    Switch(draft, { draft = it })
                }
                Text("Your branch is pushed to GitHub first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(title.trim(), body, baseBranch.trim(), draft); onDismiss() }, enabled = title.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
