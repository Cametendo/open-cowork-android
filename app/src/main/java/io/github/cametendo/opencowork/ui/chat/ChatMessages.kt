package io.github.cametendo.opencowork.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.cametendo.opencowork.data.ChatMessage
import io.github.cametendo.opencowork.data.ChatParams
import io.github.cametendo.opencowork.ui.AppLogo
import io.github.cametendo.opencowork.ui.components.AssistantContent
import io.github.cametendo.opencowork.ui.components.LocalToaster
import io.github.cametendo.opencowork.ui.theme.LocalChatColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BubbleShapeUser = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
private val BubbleShapeAssistant = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp)

@Composable
fun UserBubble(m: ChatMessage, canEdit: Boolean, onEdit: () -> Unit) {
    val colors = LocalChatColors.current
    val clipboard = LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        m.images?.takeIf { it.isNotEmpty() }?.let { imgs ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), modifier = Modifier.padding(bottom = 6.dp)) {
                imgs.forEach { b64 -> Base64Image(b64) }
            }
        }
        if (m.content.isNotBlank()) Box {
            Surface(
                color = colors.userBubble,
                contentColor = colors.onUserBubble,
                shape = BubbleShapeUser,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(BubbleShapeUser)
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                Text(m.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {
                    menu = false; clipScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", m.content))) }; toaster.show("Copied")
                })
                if (canEdit) DropdownMenuItem(text = { Text("Edit & resend") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
            }
        }
    }
}

@Composable
private fun Base64Image(b64: String) {
    val img by produceState<ImageBitmap?>(null, b64) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp))) }
}

@Composable
fun AssistantBubble(m: ChatMessage, modelName: String?, streaming: Boolean, canRegenerate: Boolean, onRegenerate: () -> Unit) {
    val colors = LocalChatColors.current
    val clipboard = LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        Surface(color = colors.assistantBubble, contentColor = colors.onAssistantBubble, shape = BubbleShapeAssistant) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (modelName != null) {
                    Text(modelName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                AssistantContent(m.content, m.reasoning, streaming, colors.onAssistantBubble)
                if (m.error != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(m.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (m.stopped == true) Text("Stopped", style = MaterialTheme.typography.labelSmall, color = colors.onAssistantBubble.copy(alpha = 0.6f))
            }
        }
        if (!streaming && (m.content.isNotEmpty() || m.error != null)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { clipScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", m.content))) }; toaster.show("Copied") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canRegenerate) IconButton(onClick = onRegenerate, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Refresh, "Regenerate", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                m.completionTokens?.let {
                    Text("$it tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private val Suggestions = listOf(
    "Explain a concept simply",
    "Help me write an email",
    "Review this code for bugs",
    "Plan a weekend trip",
)

@Composable
fun Welcome(name: String?, model: String, onSuggestion: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(56)
        Text(
            if (name.isNullOrBlank()) "How can I help?" else "Hi ${name.substringBefore(' ')}, how can I help?",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text("Chatting with $model", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Suggestions.forEach { s -> AssistChip(onClick = { onSuggestion(s) }, label = { Text(s) }) }
        }
    }
}

/** System prompt, temperature, max tokens and context length for one chat. */
@Composable
fun ChatControlsSheet(initial: ChatParams, onDismiss: () -> Unit, onSave: (ChatParams) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var system by remember { mutableStateOf(initial.system.orEmpty()) }
    var useTemp by remember { mutableStateOf(initial.temperature != null) }
    var temp by remember { mutableStateOf((initial.temperature ?: 0.7).toFloat()) }
    var maxTokens by remember { mutableStateOf(initial.maxTokens?.toString().orEmpty()) }
    var numCtx by remember { mutableStateOf(initial.numCtx?.toString().orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Chat controls", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = system, onValueChange = { system = it },
                label = { Text("System prompt") },
                placeholder = { Text("Overrides your default system prompt for this chat") },
                minLines = 3, maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Temperature", style = MaterialTheme.typography.bodyLarge)
                    Text(if (useTemp) "%.2f".format(temp) else "Model default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(useTemp, { useTemp = it })
            }
            if (useTemp) Slider(temp, { temp = (it * 20).toInt() / 20f }, valueRange = 0f..2f)
            OutlinedTextField(
                value = maxTokens, onValueChange = { maxTokens = it.filter(Char::isDigit) },
                label = { Text("Max tokens") }, placeholder = { Text("Model default") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = numCtx, onValueChange = { numCtx = it.filter(Char::isDigit) },
                label = { Text("Context length (Ollama num_ctx)") }, placeholder = { Text("Model default") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = {
                    system = ""; useTemp = false; maxTokens = ""; numCtx = ""
                }, modifier = Modifier.weight(1f)) { Text("Reset") }
                Button(onClick = {
                    onSave(
                        ChatParams(
                            system = system.trim().ifEmpty { null },
                            temperature = if (useTemp) temp.toDouble() else null,
                            maxTokens = maxTokens.toIntOrNull(),
                            numCtx = numCtx.toIntOrNull(),
                        ),
                    )
                    onDismiss()
                }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

@Suppress("unused")
private fun Modifier.noRipple(onClick: () -> Unit) = clickable(onClick = onClick)
