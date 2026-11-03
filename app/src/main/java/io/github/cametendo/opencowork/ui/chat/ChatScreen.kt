package io.github.cametendo.opencowork.ui.chat

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.AuthState
import io.github.cametendo.opencowork.data.ChatMessage
import io.github.cametendo.opencowork.data.ChatParams
import io.github.cametendo.opencowork.data.WallpaperTarget
import io.github.cametendo.opencowork.data.decodeSampled
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.Attachment
import io.github.cametendo.opencowork.ui.components.CenteredLoading
import io.github.cametendo.opencowork.ui.components.Composer
import io.github.cametendo.opencowork.ui.components.ScrollToBottomButton
import io.github.cametendo.opencowork.ui.components.ConfirmDialog
import io.github.cametendo.opencowork.ui.components.LocalToaster
import io.github.cametendo.opencowork.ui.components.ModelChip
import io.github.cametendo.opencowork.ui.components.TextInputDialog
import io.github.cametendo.opencowork.ui.components.WallpaperBackground
import io.github.cametendo.opencowork.ui.components.modelLabel
import io.github.cametendo.opencowork.ui.components.rememberActions
import io.github.cametendo.opencowork.ui.components.rememberWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ChatScreen(chatId: String?, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val container = LocalContainer.current
    val repo = container.chats
    val toaster = LocalToaster.current
    val actions = rememberActions()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val chats by repo.chats.collectAsStateWithLifecycle()
    val streamingSet by repo.streaming.collectAsStateWithLifecycle()
    val models by container.models.models.collectAsStateWithLifecycle()
    val modelsLoaded by container.models.loaded.collectAsStateWithLifecycle()
    val prefs by container.prefs.collectAsStateWithLifecycle()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val user = (auth as? AuthState.SignedIn)?.user

    val chat = chatId?.let { chats[it] }
    val streaming = chatId != null && chatId in streamingSet
    var loading by remember(chatId) { mutableStateOf(chatId != null && chat == null) }

    LaunchedEffect(chatId) {
        if (chatId != null && chatId !in repo.streaming.value) {
            try {
                repo.load(chatId)
            } catch (e: Exception) {
                if (chats[chatId] == null) { toaster.show(e.message ?: "Couldn't open the chat"); onBack() }
            }
        }
        loading = false
    }

    var model by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    LaunchedEffect(chat?.model, models, modelsLoaded) {
        if (model != null) return@LaunchedEffect
        val ids = models.map { it.id }
        model = chat?.model ?: listOf(user?.settings?.defaultModel, prefs.lastChatModel).firstOrNull { it != null && it in ids } ?: ids.firstOrNull()
    }

    var newParams by remember { mutableStateOf(ChatParams()) }
    var input by rememberSaveable(chatId) { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Attachment>() }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val bmp = decodeSampled(context, uri, 1536) ?: return@runCatching null
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        val thumb = Bitmap.createScaledBitmap(bmp, 192, (192f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true)
                        Attachment(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), thumb)
                    }.getOrNull()
                }
            }
            attachments.addAll(picked)
        }
    }

    fun chooseModel(id: String) {
        model = id
        scope.launch { container.prefsStore.setLastChatModel(id) }
        if (chatId != null) actions.launch { repo.setModel(chatId, id) }
    }

    fun send() {
        val m = model ?: return toaster.show("Select a model first")
        val text = input.trim()
        val images = attachments.map { it.base64 }.ifEmpty { null }
        if (text.isEmpty() && images == null) return
        input = ""
        attachments.clear()
        scope.launch { container.prefsStore.setLastChatModel(m) }
        if (chatId == null) {
            actions.launch(onError = { input = text }) {
                val params = newParams.takeIf { it != ChatParams() }
                val c = repo.create(m, params)
                repo.send(c.id, m, content = text, images = images)
                onCreated(c.id)
            }
        } else {
            repo.send(chatId, m, content = text, images = images)
        }
    }

    val (wallpaper, _) = rememberWallpaper(WallpaperTarget.CHAT)
    val barColor = if (wallpaper != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.88f) else MaterialTheme.colorScheme.background

    WallpaperBackground(WallpaperTarget.CHAT) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(chat?.title ?: "New chat", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(modelLabel(models, model), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { showControls = true }) { Icon(Icons.Outlined.Tune, "Chat controls") }
                    if (chat != null) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text(if (chat.pinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                                onClick = { menu = false; actions.launch { repo.setPinned(chat.id, !chat.pinned) } },
                            )
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

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val messages = chat?.messages.orEmpty()
                when {
                    loading -> CenteredLoading()
                    messages.isEmpty() -> Welcome(user?.name, modelLabel(models, model), onSuggestion = { input = it })
                    else -> {
                        val listState = rememberLazyListState()
                        val reversed = remember(messages) { messages.asReversed() }
                        val lastAssistantId = messages.lastOrNull { it.role == "assistant" }?.id
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(reversed, key = { it.id }) { m ->
                                if (m.role == "user") {
                                    UserBubble(m, canEdit = !streaming && !m.id.startsWith("local-"), onEdit = { editing = m })
                                } else {
                                    val isLast = m.id == lastAssistantId
                                    AssistantBubble(
                                        m,
                                        modelName = m.model?.let { modelLabel(models, it) },
                                        streaming = streaming && isLast,
                                        canRegenerate = isLast && !streaming,
                                        onRegenerate = { model?.let { repo.send(chatId!!, it, regenerate = true) } },
                                    )
                                }
                            }
                        }
                        val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 } }
                        ScrollToBottomButton(!atBottom, onClick = { scope.launch { listState.animateScrollToItem(0) } }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
                    }
                }
            }

            Composer(
                value = input,
                onValueChange = { input = it },
                placeholder = "Message",
                onSend = ::send,
                busy = streaming,
                enabled = model != null || !modelsLoaded,
                onStop = { chatId?.let(repo::stop) },
                attachments = attachments,
                onRemoveAttachment = { attachments.removeAt(it) },
                onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                toolbar = { ModelChip(models, model, ::chooseModel, onRefresh = { container.models.load(true) }) },
                modifier = Modifier.navigationBarsPadding().imePadding().padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 4.dp),
            )
        }
    }

    if (showControls) {
        ChatControlsSheet(
            initial = chat?.params ?: newParams,
            onDismiss = { showControls = false },
            onSave = { p ->
                if (chatId == null) newParams = p else actions.launch("Chat controls saved") { repo.setParams(chatId, p) }
            },
        )
    }
    editing?.let { m ->
        TextInputDialog("Edit message", m.content, singleLine = false, confirmLabel = "Send", onDismiss = { editing = null }) { text ->
            val mm = model ?: return@TextInputDialog
            repo.send(chatId!!, mm, content = text, editMessageId = m.id)
        }
    }
    if (renaming && chat != null) {
        TextInputDialog("Rename chat", chat.title, label = "Title", onDismiss = { renaming = false }) { t -> actions.launch { repo.rename(chat.id, t.trim()) } }
    }
    if (deleting && chat != null) {
        ConfirmDialog("Delete chat?", "“${chat.title}” will be permanently deleted.", onDismiss = { deleting = false }) {
            actions.launch { repo.delete(chat.id); onBack() }
        }
    }
}
