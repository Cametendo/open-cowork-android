package io.github.cametendo.opencowork.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import io.github.cametendo.opencowork.data.splitThinking

@Composable
fun MarkdownText(content: String, color: Color, modifier: Modifier = Modifier) {
    val state = rememberMarkdownState(content, retainState = true)
    val t = MaterialTheme.typography
    val body = t.bodyLarge.copy(color = color)
    Markdown(
        markdownState = state,
        colors = markdownColor(
            text = color,
            codeBackground = color.copy(alpha = 0.07f),
            inlineCodeBackground = color.copy(alpha = 0.1f),
            dividerColor = MaterialTheme.colorScheme.outlineVariant,
            tableBackground = color.copy(alpha = 0.03f),
        ),
        typography = markdownTypography(
            h1 = t.headlineSmall.copy(color = color, fontWeight = FontWeight.SemiBold),
            h2 = t.titleLarge.copy(color = color, fontWeight = FontWeight.SemiBold),
            h3 = t.titleMedium.copy(color = color, fontWeight = FontWeight.SemiBold),
            h4 = t.titleSmall.copy(color = color),
            h5 = t.titleSmall.copy(color = color),
            h6 = t.titleSmall.copy(color = color),
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            quote = t.bodyMedium.copy(color = color.copy(alpha = 0.8f)),
            code = t.bodySmall.copy(fontFamily = FontFamily.Monospace, color = color),
            inlineCode = body.copy(fontFamily = FontFamily.Monospace),
            table = body,
            textLink = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)),
        ),
        components = markdownComponents(codeFence = highlightedCodeFence, codeBlock = highlightedCodeBlock),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Assistant reply: optional collapsible reasoning, then the Markdown answer. */
@Composable
fun AssistantContent(content: String, reasoning: String?, streaming: Boolean, color: Color, modifier: Modifier = Modifier) {
    val split = remember(content) { splitThinking(content) }
    val thinking = listOfNotNull(reasoning?.takeIf { it.isNotBlank() }, split.thinking?.takeIf { it.isNotBlank() }).joinToString("\n\n")
    val thinkingActive = streaming && (split.open || (!reasoning.isNullOrBlank() && split.answer.isBlank()))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (thinking.isNotBlank()) ThinkingBlock(thinking, thinkingActive, color)
        when {
            split.answer.isNotBlank() -> SelectionContainer { MarkdownText(split.answer, color) }
            streaming && thinking.isBlank() -> TypingDots(color)
        }
    }
}

@Composable
fun ThinkingBlock(text: String, active: Boolean, color: Color) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, color.copy(alpha = 0.12f), MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), tint = color.copy(alpha = 0.7f))
            Text(
                if (active) "Thinking…" else "Thought process",
                style = MaterialTheme.typography.labelLarge,
                color = color.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f).then(if (active) Modifier.pulse() else Modifier),
            )
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = color.copy(alpha = 0.6f))
        }
        AnimatedVisibility(open) {
            Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
fun Modifier.pulse(): Modifier {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    return this.alpha(a)
}

@Composable
fun TypingDots(color: Color) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 180), RepeatMode.Reverse),
                label = "d$i",
            )
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(color))
        }
    }
}
