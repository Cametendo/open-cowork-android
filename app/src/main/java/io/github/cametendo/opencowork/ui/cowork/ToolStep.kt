package io.github.cametendo.opencowork.ui.cowork

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cametendo.opencowork.data.ApiJson
import io.github.cametendo.opencowork.data.CoworkMessage
import io.github.cametendo.opencowork.data.PlanItem
import io.github.cametendo.opencowork.data.ToolCall
import io.github.cametendo.opencowork.data.arr
import io.github.cametendo.opencowork.ui.components.CodeBlock
import io.github.cametendo.opencowork.ui.components.DiffStat
import io.github.cametendo.opencowork.ui.components.DiffView
import io.github.cametendo.opencowork.ui.components.pulse
import io.github.cametendo.opencowork.ui.theme.LocalChatColors
import kotlinx.serialization.json.decodeFromJsonElement

private fun iconFor(name: String): ImageVector = when (name) {
    "list_files" -> Icons.Outlined.FolderOpen
    "read_file" -> Icons.Outlined.Description
    "write_file" -> Icons.AutoMirrored.Outlined.NoteAdd
    "edit_file" -> Icons.Outlined.EditNote
    "search_files" -> Icons.Outlined.Search
    "run_command" -> Icons.Outlined.Terminal
    "update_plan" -> Icons.Outlined.Checklist
    else -> Icons.Outlined.Build
}

fun describe(tc: ToolCall, result: CoworkMessage?): Pair<String, String> = when (tc.name) {
    "list_files" -> "Listed" to tc.arg("path").ifEmpty { "." }
    "read_file" -> "Read" to tc.arg("path")
    "write_file" -> (if (result?.metaBool("created") == false) "Rewrote" else "Created") to tc.arg("path")
    "edit_file" -> "Edited" to tc.arg("path")
    "search_files" -> "Searched" to ("/${tc.arg("pattern")}/" + tc.arg("glob").let { if (it.isNotEmpty()) " in $it" else "" })
    "run_command" -> "Ran" to tc.arg("command")
    "update_plan" -> "Updated plan" to ""
    else -> tc.name to tc.arguments.toString().take(80)
}

private fun approvalVerb(name: String) = mapOf("write_file" to "Create", "edit_file" to "Edit", "run_command" to "Run")[name] ?: name

private fun pendingVerb(name: String) = mapOf(
    "list_files" to "Listing", "read_file" to "Reading", "write_file" to "Writing", "edit_file" to "Editing",
    "search_files" to "Searching", "run_command" to "Running", "update_plan" to "Updating plan",
)[name] ?: "Running"

@Composable
fun ToolStep(
    call: ToolCall,
    result: CoworkMessage?,
    waitingApproval: Boolean,
    active: Boolean,
    liveOutput: String?,
    interrupted: Boolean,
    onApprove: (approved: Boolean, always: Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val chat = LocalChatColors.current
    var open by rememberSaveable(call.id) { mutableStateOf(false) }

    if (call.name == "update_plan" && result != null && result.isError != true) {
        val plan = result.meta?.arr("plan")?.mapNotNull { runCatching { ApiJson.decodeFromJsonElement<PlanItem>(it) }.getOrNull() }.orEmpty()
        Surface(shape = MaterialTheme.shapes.medium, color = chat.assistantBubble, contentColor = chat.onAssistantBubble, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Checklist, null, Modifier.size(16.dp), tint = scheme.primary)
                    Text("Plan · ${plan.count { it.status == "completed" }}/${plan.size}", style = MaterialTheme.typography.labelLarge)
                }
                PlanList(plan, compact = true)
            }
        }
        return
    }

    val (verb, target) = describe(call, result)
    val denied = result?.denied == true
    val failed = result?.isError == true && !denied
    val running = result == null && (active || (!waitingApproval && !interrupted))
    val exitCode = result?.metaInt("exit_code")
    val diff = result?.metaString("diff")
    val showLive = result == null && call.name == "run_command" && (active || !liveOutput.isNullOrEmpty())

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = chat.assistantBubble,
        contentColor = chat.onAssistantBubble,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (waitingApproval) Modifier.border(1.5.dp, scheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .animateContentSize(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (failed) scheme.errorContainer else scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(iconFor(call.name), null, Modifier.size(16.dp), tint = if (failed) scheme.onErrorContainer else scheme.onPrimaryContainer) }
                Text(
                    if (result != null) verb else if (waitingApproval) approvalVerb(call.name) else pendingVerb(call.name),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = if (running) Modifier.pulse() else Modifier,
                )
                Text(
                    target,
                    style = if (call.name == "run_command") MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
                    color = chat.onAssistantBubble.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                DiffStat(result?.metaInt("added"), result?.metaInt("removed"))
                if (exitCode != null && exitCode != 0) {
                    Text("exit $exitCode", color = scheme.error, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                if (result?.metaBool("timed_out") == true) Text("timed out", color = scheme.error, fontSize = 11.sp)
                when {
                    running -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    waitingApproval -> Icon(Icons.Outlined.GppMaybe, null, Modifier.size(18.dp), tint = scheme.primary)
                    result != null && result.isError != true -> Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = chat.success)
                    failed -> Icon(Icons.Outlined.Close, null, Modifier.size(18.dp), tint = scheme.error)
                    denied || (result == null && interrupted) -> Icon(Icons.Outlined.Block, null, Modifier.size(16.dp), tint = scheme.outline)
                }
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp).rotate(if (open) 90f else 0f), tint = scheme.outline)
            }

            if (waitingApproval) ApprovalPanel(call, onApprove)

            if (showLive && !open) LiveOutput(liveOutput.orEmpty())

            if (open) {
                Column(Modifier.fillMaxWidth()) {
                    when {
                        diff != null -> Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { DiffView(diff) }
                        call.name == "run_command" && result == null -> LiveOutput(liveOutput.orEmpty())
                        else -> {
                            if (call.name != "run_command") {
                                Text(
                                    call.arguments.entries.joinToString("\n") { (k, v) -> "$k: ${v.toString().take(400)}" },
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    color = chat.onAssistantBubble.copy(alpha = 0.65f),
                                    modifier = Modifier.fillMaxWidth().background(scheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                            if (result != null) {
                                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                    if (call.name == "run_command") CodeBlock(result.content)
                                    else Text(
                                        result.content, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveOutput(text: String) {
    val scroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(text.length) { scroll.scrollTo(scroll.maxValue) }
    Box(Modifier.heightIn(max = 240.dp).verticalScroll(scroll)) { CodeBlock(text) }
}

@Composable
private fun ApprovalPanel(call: ToolCall, onApprove: (Boolean, Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(scheme.primary.copy(alpha = 0.06f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            when (call.name) {
                "run_command" -> "Allow Cowork to run this command?"
                "write_file" -> "Allow Cowork to write this file?"
                else -> "Allow Cowork to edit this file?"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(Modifier.clip(MaterialTheme.shapes.small).heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            when (call.name) {
                "run_command" -> CodeBlock(call.arg("command"))
                "write_file" -> CodeBlock(call.arg("content"))
                else -> DiffView(
                    call.arg("old_string").lines().joinToString("\n") { "-$it" } + "\n" +
                        call.arg("new_string").lines().joinToString("\n") { "+$it" },
                    modifier = Modifier.background(scheme.surface),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { onApprove(true, false) }) { Text("Allow") }
            FilledTonalButton(onClick = { onApprove(true, true) }) { Text("Allow all for this task") }
            TextButton(onClick = { onApprove(false, false) }) { Text("Deny") }
        }
    }
}

@Composable
fun PlanList(plan: List<PlanItem>, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val chat = LocalChatColors.current
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        plan.forEach { p ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (p.status) {
                    "completed" -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = chat.success)
                    "in_progress" -> Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary).pulse())
                    }
                    else -> Icon(Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(18.dp), tint = scheme.outline)
                }
                Text(
                    p.title,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    textDecoration = if (p.status == "completed") TextDecoration.LineThrough else null,
                    color = if (p.status == "completed") LocalChatColors.current.onAssistantBubble.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.Unspecified,
                )
            }
        }
    }
}

@Composable
fun PlanProgress(plan: List<PlanItem>, modifier: Modifier = Modifier) {
    val done = plan.count { it.status == "completed" }
    val current = plan.firstOrNull { it.status == "in_progress" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                current?.title ?: if (done == plan.size) "All steps done" else "Planning…",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("$done/${plan.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (plan.isEmpty()) 0f else done / plan.size.toFloat() },
            modifier = Modifier.fillMaxWidth().clip(CircleShape),
        )
    }
}

/** "Ask first" / "Auto" selector used on the new-task screen and in a task. */
@Composable
fun ApprovalModeRow(mode: String, enabled: Boolean, onChange: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = enabled) { menu = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(if (mode == "auto") Icons.Outlined.Bolt else Icons.Outlined.Shield, null, tint = if (mode == "auto") LocalChatColors.current.warning else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Approval mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (mode == "auto") "Run automatically" else "Ask before acting", style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
        ApprovalMenu(menu, mode, onDismiss = { menu = false }, onChange = onChange)
    }
}

@Composable
fun ApprovalMenu(expanded: Boolean, mode: String, onDismiss: () -> Unit, onChange: (String) -> Unit) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        listOf(
            Triple("ask", "Ask before acting", "Approve each file edit and command"),
            Triple("auto", "Run automatically", "Edits and commands run without asking"),
        ).forEach { (v, title, desc) ->
            DropdownMenuItem(
                leadingIcon = { Icon(if (v == "auto") Icons.Outlined.Bolt else Icons.Outlined.Shield, null) },
                text = {
                    Column {
                        Text(title)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                trailingIcon = { if (mode == v) Icon(Icons.Outlined.Check, null) },
                onClick = { onDismiss(); onChange(v) },
            )
        }
    }
}
