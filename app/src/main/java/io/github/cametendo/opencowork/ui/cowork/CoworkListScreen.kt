package io.github.cametendo.opencowork.ui.cowork

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.CoworkSummary
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.CenteredLoading
import io.github.cametendo.opencowork.ui.components.ConfirmDialog
import io.github.cametendo.opencowork.ui.components.EmptyState
import io.github.cametendo.opencowork.ui.components.ListPadding
import io.github.cametendo.opencowork.ui.components.Pill
import io.github.cametendo.opencowork.ui.components.TextInputDialog
import io.github.cametendo.opencowork.ui.components.relativeTime
import io.github.cametendo.opencowork.ui.components.rememberActions
import kotlinx.coroutines.launch

@Composable
fun CoworkListScreen(onOpen: (String) -> Unit, onNew: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.cowork
    val sessions by repo.sessions.collectAsStateWithLifecycle()
    val loaded by repo.sessionsLoaded.collectAsStateWithLifecycle()
    val live by repo.live.collectAsStateWithLifecycle()
    val actions = rememberActions()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CoworkSummary?>(null) }
    var deleting by remember { mutableStateOf<CoworkSummary?>(null) }

    LaunchedEffect(Unit) { actions.launch { repo.loadSessions() } }

    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Cowork") },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New task") })
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; runCatching { repo.loadSessions() }; refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            when {
                !loaded && sessions.isEmpty() -> CenteredLoading()
                sessions.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            Icons.Outlined.SmartToy, "No tasks yet",
                            "Give Cowork a task. It plans the work, then reads and edits files and runs commands in a workspace while you watch every step.",
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                    items(sessions, key = { it.id }) { s ->
                        val l = live[s.id]
                        val running = l?.running ?: (s.status == "running")
                        val waiting = (l?.approvals?.isNotEmpty() == true)
                        TaskRow(s, running, waiting, onOpen, { renaming = s }, { deleting = s })
                    }
                }
            }
        }
    }

    renaming?.let { s ->
        TextInputDialog("Rename task", s.title, label = "Title", onDismiss = { renaming = null }) { t -> actions.launch { repo.rename(s.id, t.trim()) } }
    }
    deleting?.let { s ->
        ConfirmDialog(
            "Delete task?",
            "“${s.title}” and its transcript will be deleted." + if (s.workspaceType == "repo") " The repository and its branch are kept." else "",
            checkboxLabel = if (s.workspaceType == "scratch") "Also delete the workspace folder" else null,
            onDismiss = { deleting = null },
        ) { files -> actions.launch { repo.delete(s.id, files) } }
    }
}

@Composable
private fun TaskRow(s: CoworkSummary, running: Boolean, waiting: Boolean, onOpen: (String) -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(s.id) }, onLongClick = { menu = true })
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (s.status == "error") scheme.errorContainer else scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        s.status == "error" -> Icons.Outlined.ErrorOutline
                        s.workspaceType == "repo" -> Icons.Outlined.FolderSpecial
                        else -> Icons.Outlined.CreateNewFolder
                    },
                    null,
                    tint = if (s.status == "error") scheme.onErrorContainer else scheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        waiting -> Pill("Needs approval", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                        running -> Pill("Running", scheme.primaryContainer, scheme.onPrimaryContainer)
                        s.status == "error" -> Pill("Error", scheme.errorContainer, scheme.onErrorContainer)
                    }
                    Text(
                        listOfNotNull(
                            s.repoName ?: s.workspacePath.substringAfterLast('/').takeIf { it.isNotBlank() },
                            s.branch,
                            relativeTime(s.updatedAt),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Task options", tint = scheme.onSurfaceVariant) }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(
                text = { Text("Delete", color = scheme.error) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = scheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}
