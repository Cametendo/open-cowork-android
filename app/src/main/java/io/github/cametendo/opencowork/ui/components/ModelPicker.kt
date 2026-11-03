package io.github.cametendo.opencowork.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cametendo.opencowork.data.Model
import io.github.cametendo.opencowork.data.str
import kotlinx.coroutines.launch

fun modelLabel(models: List<Model>, id: String?): String =
    if (id == null) "Select a model" else models.firstOrNull { it.id == id }?.name ?: id

/** Compact chip showing the chosen model; tapping opens a searchable picker. */
@Composable
fun ModelChip(
    models: List<Model>,
    selected: String?,
    onSelect: (String) -> Unit,
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Memory, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                modelLabel(models, selected),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
    }
    if (open) ModelPickerSheet(models, selected, onSelect = { onSelect(it); open = false }, onRefresh = onRefresh, onDismiss = { open = false })
}

@Composable
fun ModelPickerSheet(
    models: List<Model>,
    selected: String?,
    onSelect: (String) -> Unit,
    onRefresh: suspend () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Choose a model", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else IconButton(onClick = {
                    scope.launch { refreshing = true; runCatching { onRefresh() }; refreshing = false }
                }) { Icon(Icons.Outlined.Refresh, "Refresh models") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search models") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val filtered = models.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) }
            if (models.isEmpty()) {
                EmptyState(Icons.Outlined.Memory, "No models available", "Check the connections in Admin › Connections, then refresh.")
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                val groups = filtered.groupBy { it.connectionName.ifBlank { it.provider } }
                groups.forEach { (conn, list) ->
                    item(key = "h-$conn") {
                        Text(
                            conn.ifBlank { "Models" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { "m-${it.id}" }) { m ->
                        val details = listOfNotNull(
                            m.details?.str("parameter_size"),
                            m.details?.str("quantization_level"),
                            m.details?.str("family"),
                        ).joinToString(" · ")
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(m.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (m.id == selected) FontWeight.SemiBold else null)
                                Text(
                                    listOf(if (m.provider == "ollama") "Ollama" else "OpenAI-compatible", details).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (m.id == selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { VSpace(16) }
            }
        }
    }
}
