package io.github.cametendo.opencowork.ui.cowork

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.CoworkMessage
import io.github.cametendo.opencowork.data.LiveSession
import io.github.cametendo.opencowork.data.ToolCall
import io.github.cametendo.opencowork.data.WallpaperTarget
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.AssistantContent
import io.github.cametendo.opencowork.ui.components.CenteredLoading
import io.github.cametendo.opencowork.ui.components.Composer
import io.github.cametendo.opencowork.ui.components.ScrollToBottomButton
import io.github.cametendo.opencowork.ui.components.ConfirmDialog
import io.github.cametendo.opencowork.ui.components.ErrorBox
import io.github.cametendo.opencowork.ui.components.LocalToaster
import io.github.cametendo.opencowork.ui.components.ModelChip
import io.github.cametendo.opencowork.ui.components.TextInputDialog
import io.github.cametendo.opencowork.ui.components.WallpaperBackground
import io.github.cametendo.opencowork.ui.components.pulse
import io.github.cametendo.opencowork.ui.components.rememberActions
import io.github.cametendo.opencowork.ui.components.rememberWallpaper
import io.github.cametendo.opencowork.ui.theme.LocalChatColors
import kotlinx.coroutines.launch

/** One line of the transcript, flattened so it can live in a LazyColumn. */
private sealed class Item(val key: String) {
    class Event(val m: CoworkMessage) : Item("e-${m.id}")
    class User(val m: CoworkMessage) : Item("u-${m.id}")
    class Text(val m: CoworkMessage, val streaming: Boolean) : Item("a-${m.id}")
    class Tool(val call: ToolCall, val result: CoworkMessage?) : Item("t-${call.id}")
    class Queued(val m: CoworkMessage) : Item("q-${m.id}")
    data object Working : Item("working")
    class Error(val text: String) : Item("error")
}

private fun buildItems(live: LiveSession): List<Item> {
    val results = live.messages.filter { it.role == "tool" && it.toolCallId != null }.associateBy { it.toolCallId!! }
    val last = live.messages.lastOrNull()
    val streamingText = live.running && last?.role == "assistant" && last.toolCalls.isNullOrEmpty()
    val out = ArrayList<Item>()
    live.messages.forEachIndexed { idx, m ->
        when (m.role) {
            "tool" -> {}
            "event" -> out += Item.Event(m)
            "user" -> out += Item.User(m)
            else -> {
                val isStreaming = live.running && idx == live.messages.lastIndex
                if (m.content.isNotBlank() || !m.reasoning.isNullOrBlank() || (isStreaming && m.toolCalls.isNullOrEmpty())) out += Item.Text(m, isStreaming)
                m.toolCalls?.forEach { tc -> out += Item.Tool(tc, results[tc.id]) }
            }
        }
    }
    live.queued.forEach { out += Item.Queued(it) }
    if (live.running && live.approvals.isEmpty() && live.activeTools.isEmpty() && !streamingText) out += Item.Working
    if (!live.running && live.error != null) out += Item.Error(live.error)
    return out
}

@Composable
fun CoworkSessionScreen(id: String, onBack: () -> Unit, onWorkspace: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.cowork
    val toaster = LocalToaster.current
    val actions = rememberActions()
    val scope = rememberCoroutineScope()
    val liveMap by repo.live.collectAsStateWithLifecycle()
    val models by container.models.models.collectAsStateWithLifecycle()
    val live = liveMap[id]

    LaunchedEffect(id) {
        try {
            repo.open(id)
        } catch (e: Exception) {
            if (repo.live.value[id] == null) { toaster.show(e.message ?: "Couldn't open the task"); onBack() }
        }
    }

    var model by rememberSaveable(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(live?.session?.model) { if (model == null) model = live?.session?.model }
    var input by rememberSaveable(id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }

    val (wallpaper, _) = rememberWallpaper(WallpaperTarget.COWORK)
    val barColor = if (wallpaper != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.88f) else MaterialTheme.colorScheme.background

    WallpaperBackground(WallpaperTarget.COWORK) {
        if (live == null) {
            CenteredLoading()
            return@WallpaperBackground
        }
        val s = live.session
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                            if (live.running) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("Running", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).pulse())
                                }
                            }
                        }
                        Text(
                            listOfNotNull(s.repoName ?: s.workspacePath.substringAfterLast('/'), s.branch).joinToString(" · "),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onWorkspace) { Icon(Icons.Outlined.FolderOpen, "Workspace: plan, files and git") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; deleting = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
            )
            AnimatedVisibility(live.plan.isNotEmpty() && live.running) {
                Surface(color = barColor) {
                    PlanProgress(live.plan, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val items = remember(live) { buildItems(live).asReversed() }
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.key }) { item -> TranscriptItem(item, live, onApprove = { tc, ok, always ->
                        actions.launch { repo.approve(id, tc, ok, always) }
                    }) }
                }
                val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 } }
                ScrollToBottomButton(!atBottom, onClick = { scope.launch { listState.animateScrollToItem(0) } }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            }

            Composer(
                value = input,
                onValueChange = { input = it },
                placeholder = if (live.running) "Steer Cowork — it reads this after the current step" else "Reply or give a follow-up task",
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        input = ""
                        actions.launch(onError = { input = text }) { repo.send(id, text, model) }
                    }
                },
                busy = live.running,
                sendWhileBusy = true,
                onStop = { actions.launch { repo.cancel(id) } },
                toolbar = {
                    ModelChip(models, model, { m -> model = m; actions.launch { repo.setModel(id, m) } }, onRefresh = { container.models.load(true) }, enabled = !live.running)
                    Box {
                        Surface(
                            onClick = { modeMenu = true },
                            enabled = !live.running,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    if (s.approvalMode == "auto") Icons.Outlined.Bolt else Icons.Outlined.Shield, null, Modifier.size(16.dp),
                                    tint = if (s.approvalMode == "auto") LocalChatColors.current.warning else MaterialTheme.colorScheme.primary,
                                )
                                Text(if (s.approvalMode == "auto") "Auto" else "Ask first", style = MaterialTheme.typography.labelLarge)
                                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                            }
                        }
                        ApprovalMenu(modeMenu, s.approvalMode, onDismiss = { modeMenu = false }) { m -> actions.launch { repo.setApprovalMode(id, m) } }
                    }
                },
                modifier = Modifier.navigationBarsPadding().imePadding().padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 4.dp),
            )
        }
    }

    if (renaming && live != null) {
        TextInputDialog("Rename task", live.session.title, label = "Title", onDismiss = { renaming = false }) { t -> actions.launch { repo.rename(id, t.trim()) } }
    }
    if (deleting && live != null) {
        ConfirmDialog(
            "Delete task?",
            "“${live.session.title}” and its transcript will be deleted." + if (live.session.workspaceType == "repo") " The repository and its branch are kept." else "",
            checkboxLabel = if (live.session.workspaceType == "scratch") "Also delete the workspace folder" else null,
            onDismiss = { deleting = false },
        ) { files -> actions.launch { repo.delete(id, files); onBack() } }
    }
}

@Composable
private fun TranscriptItem(item: Item, live: LiveSession, onApprove: (String, Boolean, Boolean) -> Unit) {
    val chat = LocalChatColors.current
    val scheme = MaterialTheme.colorScheme
    when (item) {
        is Item.Event -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = scheme.surfaceContainerHigh.copy(alpha = 0.9f)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (item.m.content.contains("branch", true)) Icons.Outlined.AccountTree else Icons.Outlined.Info, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
                    Text(item.m.content, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        is Item.User -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Surface(color = chat.userBubble, contentColor = chat.onUserBubble, shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                SelectionContainer { Text(item.m.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
            }
        }
        is Item.Text -> Surface(
            color = if (item.m.notice == true) chat.warning.copy(alpha = 0.15f).compositeOverSafe(chat.assistantBubble) else chat.assistantBubble,
            contentColor = chat.onAssistantBubble,
            shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                AssistantContent(item.m.content, item.m.reasoning, item.streaming, chat.onAssistantBubble)
                if (item.m.stopped == true) Text("Stopped", style = MaterialTheme.typography.labelSmall, color = chat.onAssistantBubble.copy(alpha = 0.6f))
            }
        }
        is Item.Tool -> ToolStep(
            call = item.call,
            result = item.result,
            waitingApproval = item.call.id in live.approvals,
            active = item.call.id in live.activeTools,
            liveOutput = live.toolOutput[item.call.id],
            interrupted = !live.running,
            onApprove = { ok, always -> onApprove(item.call.id, ok, always) },
        )
        is Item.Queued -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = scheme.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, scheme.outline),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(item.m.content, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    Text("Queued — Cowork reads this after the current step", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        Item.Working -> Row(Modifier.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).pulse()) { Surface(shape = CircleShape, color = scheme.primary, modifier = Modifier.fillMaxSize()) {} }
            Text("Working…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.pulse())
        }
        is Item.Error -> ErrorBox(item.text)
    }
}

private fun androidx.compose.ui.graphics.Color.compositeOverSafe(under: androidx.compose.ui.graphics.Color) =
    androidx.compose.ui.graphics.Color(
        red = red * alpha + under.red * (1 - alpha),
        green = green * alpha + under.green * (1 - alpha),
        blue = blue * alpha + under.blue * (1 - alpha),
        alpha = 1f,
    )

@Suppress("unused")
private val MonoFont = FontFamily.Monospace

@Suppress("unused")
private val ErrorIcon = Icons.Outlined.ErrorOutline
