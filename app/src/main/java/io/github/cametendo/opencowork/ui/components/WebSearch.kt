package io.github.cametendo.opencowork.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cametendo.opencowork.data.ChatMessage
import io.github.cametendo.opencowork.data.WebSource

/** "Web search" switch shown next to the message box when the server has it enabled. */
@Composable
fun WebSearchChip(on: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { onChange(!on) },
        shape = CircleShape,
        color = if (on) scheme.primaryContainer else scheme.surfaceContainerHighest,
        contentColor = if (on) scheme.onPrimaryContainer else scheme.onSurface,
        border = if (on) BorderStroke(1.dp, scheme.primary) else null,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Language, if (on) "Web search is on" else "Web search is off", Modifier.size(16.dp), tint = if (on) scheme.primary else scheme.onSurfaceVariant)
            Text("Web search", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Turn [1] citations into links to the matching source (the Markdown renderer opens them). */
fun linkCitations(content: String, sources: List<WebSource>?): String {
    if (sources.isNullOrEmpty()) return content
    return Regex("\\[(\\d{1,2})\\](?!\\()").replace(content) { m ->
        val src = sources.getOrNull(m.groupValues[1].toInt() - 1)
        if (src != null && src.url.startsWith("http")) "[${m.groupValues[1]}](${src.url})" else m.value
    }
}

/** "Searched the web · 5 sources" line above an answer; expands to the numbered source list. */
@Composable
fun SearchSummary(message: ChatMessage, streaming: Boolean, color: androidx.compose.ui.graphics.Color) {
    val query = message.searchQuery ?: return
    var open by rememberSaveable(message.id) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val sources = message.sources
    val searching = streaming && sources == null
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.then(if (!sources.isNullOrEmpty()) Modifier.androidClickable { open = !open } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Language, null, Modifier.size(16.dp), tint = color.copy(alpha = 0.65f))
            Text(
                when {
                    searching -> "Searching the web for “$query”…"
                    message.searchError != null -> "Web search failed"
                    !sources.isNullOrEmpty() -> "Searched the web · ${sources.size} source${if (sources.size == 1) "" else "s"}"
                    else -> "Searched the web · no results"
                },
                style = MaterialTheme.typography.labelLarge,
                color = color.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).then(if (searching) Modifier.pulse() else Modifier),
            )
            if (!sources.isNullOrEmpty()) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp).rotate(if (open) 180f else 0f), tint = color.copy(alpha = 0.6f))
        }
        message.searchError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
        }
        AnimatedVisibility(open && !sources.isNullOrEmpty()) {
            Column(Modifier.padding(start = 8.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.orEmpty().forEachIndexed { i, s ->
                    Column(Modifier.androidClickable { runCatching { uri.openUri(s.url) } }) {
                        Text("[${i + 1}] ${s.title.ifBlank { s.host }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(s.host, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

/** Numbered source chips under a finished answer. */
@Composable
fun SourceChips(sources: List<WebSource>, color: androidx.compose.ui.graphics.Color) {
    val uri = LocalUriHandler.current
    FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sources.forEachIndexed { i, s ->
            Surface(
                onClick = { runCatching { uri.openUri(s.url) } },
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
                border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(s.host, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                }
            }
        }
    }
}

private fun Modifier.androidClickable(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))
