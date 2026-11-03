package io.github.cametendo.opencowork.ui.chat

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.ChatSummary
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.CenteredLoading
import io.github.cametendo.opencowork.ui.components.ConfirmDialog
import io.github.cametendo.opencowork.ui.components.EmptyState
import io.github.cametendo.opencowork.ui.components.ListPadding
import io.github.cametendo.opencowork.ui.components.TextInputDialog
import io.github.cametendo.opencowork.ui.components.dateBucket
import io.github.cametendo.opencowork.ui.components.modelLabel
import io.github.cametendo.opencowork.ui.components.relativeTime
import io.github.cametendo.opencowork.ui.components.rememberActions
import kotlinx.coroutines.launch

@Composable
fun ChatListScreen(onOpen: (String) -> Unit, onNew: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.chats
    val list by repo.list.collectAsStateWithLifecycle()
    val loaded by repo.listLoaded.collectAsStateWithLifecycle()
    val streaming by repo.streaming.collectAsStateWithLifecycle()
    val models by container.models.models.collectAsStateWithLifecycle()
    val actions = rememberActions()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatSummary?>(null) }
    var deleting by remember { mutableStateOf<ChatSummary?>(null) }
    var deletingAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { actions.launch { repo.loadList() } }

    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Search chats") },
                            singleLine = true,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        )
                    } else Text("Chats")
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, "Search")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete all chats") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                                onClick = { menu = false; deletingAll = true },
                            )
                        }
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New chat") })
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    runCatching { repo.loadList() }
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            val filtered = list.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            when {
                !loaded && list.isEmpty() -> CenteredLoading()
                filtered.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        if (query.isNotBlank()) EmptyState(Icons.Outlined.Search, "No chats match “$query”")
                        else EmptyState(Icons.Outlined.ChatBubbleOutline, "No chats yet", "Start a conversation with any of your models.")
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                    val pinned = filtered.filter { it.pinned }
                    val rest = filtered.filter { !it.pinned }
                    if (pinned.isNotEmpty()) {
                        item(key = "h-pinned") { GroupHeader("Pinned") }
                        items(pinned, key = { "p-" + it.id }) { c ->
                            ChatRow(c, c.id in streaming, modelLabel(models, c.model), onOpen, { renaming = c }, { actions.launch { repo.setPinned(c.id, false) } }, { deleting = c })
                        }
                    }
                    rest.groupBy { dateBucket(it.updatedAt) }.forEach { (bucket, chats) ->
                        item(key = "h-$bucket") { GroupHeader(bucket) }
                        items(chats, key = { it.id }) { c ->
                            ChatRow(c, c.id in streaming, modelLabel(models, c.model), onOpen, { renaming = c }, { actions.launch { repo.setPinned(c.id, true) } }, { deleting = c })
                        }
                    }
                }
            }
        }
    }

    renaming?.let { c ->
        TextInputDialog("Rename chat", c.title, label = "Title", onDismiss = { renaming = null }) { t -> actions.launch { repo.rename(c.id, t.trim()) } }
    }
    deleting?.let { c ->
        ConfirmDialog("Delete chat?", "“${c.title}” will be permanently deleted.", onDismiss = { deleting = null }) { actions.launch { repo.delete(c.id) } }
    }
    if (deletingAll) {
        ConfirmDialog("Delete all chats?", "Every chat in your account will be permanently deleted. This can't be undone.", confirmLabel = "Delete all", onDismiss = { deletingAll = false }) {
            actions.launch("All chats deleted") { repo.deleteAll() }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChatRow(
    c: ChatSummary,
    streaming: Boolean,
    model: String,
    onOpen: (String) -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onOpen(c.id) }, onLongClick = { menu = true })
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(c.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOf(if (c.model != null) model else null, relativeTime(c.updatedAt)).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (streaming) CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(18.dp), strokeWidth = 2.dp)
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Chat options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(if (c.pinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) }, onClick = { menu = false; onTogglePin() })
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}
