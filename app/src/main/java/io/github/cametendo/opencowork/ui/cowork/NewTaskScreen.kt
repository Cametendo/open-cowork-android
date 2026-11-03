package io.github.cametendo.opencowork.ui.cowork

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.AuthState
import io.github.cametendo.opencowork.data.LocalRepo
import io.github.cametendo.opencowork.data.WallpaperTarget
import io.github.cametendo.opencowork.ui.AppLogo
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.LocalFeatures
import io.github.cametendo.opencowork.ui.components.Composer
import io.github.cametendo.opencowork.ui.components.LocalToaster
import io.github.cametendo.opencowork.ui.components.ModelChip
import io.github.cametendo.opencowork.ui.components.SwitchRow
import io.github.cametendo.opencowork.ui.components.WallpaperBackground
import kotlinx.coroutines.launch

private data class Example(val icon: ImageVector, val title: String, val prompt: String)

private val Examples = listOf(
    Example(Icons.Outlined.Description, "Write a README", "Read through this project and write a clear README.md with setup and usage instructions."),
    Example(Icons.Outlined.Science, "Fix failing tests", "Run the test suite, find out why tests fail, fix the problems and re-run until everything passes."),
    Example(Icons.Outlined.Build, "Build a small app", "Build a single-page pomodoro timer in index.html with a clean, modern design and no external dependencies."),
    Example(Icons.Outlined.BarChart, "Analyze data", "Look at the CSV files in the workspace, summarize what's in them and write the key findings to report.md."),
)

@Composable
fun NewTaskScreen(initialRepo: String?, onBack: () -> Unit, onStarted: (String) -> Unit, onManageRepos: () -> Unit) {
    val container = LocalContainer.current
    val features = LocalFeatures.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val models by container.models.models.collectAsStateWithLifecycle()
    val prefs by container.prefs.collectAsStateWithLifecycle()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val settings = (auth as? AuthState.SignedIn)?.user?.settings

    var model by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(models) {
        if (model != null) return@LaunchedEffect
        val ids = models.map { it.id }
        model = listOf(settings?.coworkDefaultModel, prefs.lastCoworkModel, settings?.defaultModel).firstOrNull { it != null && it in ids } ?: ids.firstOrNull()
    }
    var mode by rememberSaveable { mutableStateOf(settings?.coworkApprovalMode ?: "ask") }
    var repoId by rememberSaveable { mutableStateOf(initialRepo) }
    var newBranch by rememberSaveable { mutableStateOf(true) }
    var repos by remember { mutableStateOf<List<LocalRepo>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(features.github) {
        if (features.github) runCatching { container.api.repos() }.onSuccess { repos = it }
    }
    val repo = repos.firstOrNull { it.id == repoId }

    fun start(prompt: String) {
        val m = model ?: return toaster.show("Select a model first")
        if (prompt.isBlank() || busy) return
        busy = true
        scope.launch {
            try {
                container.prefsStore.setLastCoworkModel(m)
                val s = container.cowork.create(prompt.trim(), m, mode, repoId, newBranch)
                onStarted(s.id)
            } catch (e: Exception) {
                toaster.show(e.message ?: "Couldn't start the task")
                busy = false
            }
        }
    }

    WallpaperBackground(WallpaperTarget.COWORK) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = { Text("New task") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)),
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLogo(52)
                    Text("What should we work on?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "Cowork plans the task, then reads and edits files and runs commands in a workspace folder. Watch every step, or let it run.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { picking = true }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(if (repo != null) Icons.Outlined.FolderSpecial else Icons.Outlined.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("Workspace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(repo?.fullName ?: "New scratch folder", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Outlined.ChevronRight, null)
                        }
                        if (repo != null) {
                            SwitchRow(
                                "Work on a new branch",
                                if (newBranch) "Pulls the latest ${repo.currentBranch ?: repo.defaultBranch} and creates a cowork/… branch"
                                else "Works directly on ${repo.currentBranch ?: "the current branch"}",
                                newBranch,
                            ) { newBranch = it }
                        }
                        ApprovalModeRow(mode, enabled = true) { mode = it }
                    }
                }

                Text("Try an example", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                Examples.forEach { ex ->
                    Surface(
                        onClick = { start(ex.prompt) },
                        enabled = !busy && model != null,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(ex.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text(ex.title, style = MaterialTheme.typography.bodyLarge)
                                Text(ex.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        " Works best with models that support tool calling, like qwen3, llama3.1 or gpt-oss.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Box(Modifier.navigationBarsPadding().imePadding().padding(10.dp)) {
                if (busy) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("  Preparing the workspace…")
                    }
                } else Composer(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = if (repo != null) "Describe a task for ${repo.fullName}…" else "Describe a task…",
                    onSend = { start(input) },
                    enabled = model != null,
                    toolbar = { ModelChip(models, model, { model = it }, onRefresh = { container.models.load(true) }) },
                )
            }
        }
    }

    if (picking) {
        val sheet = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = sheet) {
            Text("Choose a workspace", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp).navigationBarsPadding()) {
                item {
                    PickRow(Icons.Outlined.CreateNewFolder, "New scratch folder", "An empty workspace for this task", repoId == null) { repoId = null; picking = false }
                }
                if (repos.isNotEmpty()) item {
                    Text("Repositories", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                }
                items(repos, key = { it.id }) { r ->
                    PickRow(Icons.Outlined.FolderSpecial, r.fullName, r.currentBranch ?: r.defaultBranch, r.id == repoId) { repoId = r.id; picking = false }
                }
                if (features.github) item {
                    PickRow(Icons.Outlined.Add, "Clone a repository…", "Connect GitHub and clone a repo", false) { picking = false; onManageRepos() }
                }
            }
        }
    }
}

@Composable
private fun PickRow(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}
