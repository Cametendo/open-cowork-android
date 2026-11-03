package io.github.cametendo.opencowork.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.cametendo.opencowork.ui.theme.LocalChatColors

/** An image picked for a vision model: base64 for the API plus a thumbnail. */
class Attachment(val base64: String, val thumbnail: Bitmap)

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    sendWhileBusy: Boolean = false,
    enabled: Boolean = true,
    onStop: (() -> Unit)? = null,
    attachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (Int) -> Unit = {},
    onAttach: (() -> Unit)? = null,
    toolbar: @Composable RowScope.() -> Unit = {},
) {
    val canSend = enabled && (value.isNotBlank() || attachments.isNotEmpty()) && (!busy || sendWhileBusy)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (LocalChatColors.current.isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
            if (attachments.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEachIndexed { i, a ->
                        Box {
                            Image(
                                a.thumbnail.asImageBitmap(), null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                            )
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(onClick = { onRemoveAttachment(i) }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Outlined.Close, "Remove image", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                                }
                            }
                        }
                    }
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        }
                        inner()
                    }
                },
            )
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onAttach != null) {
                    IconButton(onClick = onAttach, enabled = enabled) { Icon(Icons.Outlined.AddPhotoAlternate, "Attach images") }
                }
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = toolbar,
                )
                if (busy && onStop != null && !(sendWhileBusy && value.isNotBlank())) {
                    FilledIconButton(
                        onClick = onStop,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        ),
                    ) { Icon(Icons.Filled.Stop, "Stop") }
                } else {
                    FilledIconButton(onClick = onSend, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
