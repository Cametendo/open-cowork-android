package io.github.cametendo.opencowork.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.WallpaperTarget
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.theme.LocalChatColors

@Composable
fun rememberWallpaper(target: WallpaperTarget): Pair<ImageBitmap?, io.github.cametendo.opencowork.data.WallpaperSettings> {
    val container = LocalContainer.current
    val prefs by container.prefs.collectAsStateWithLifecycle()
    val settings = prefs.wallpaper(target)
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val maxSide = with(density) { maxOf(config.screenWidthDp, config.screenHeightDp).dp.roundToPx() }.coerceAtMost(2400)
    val bitmap by produceState<ImageBitmap?>(null, settings.version, maxSide) {
        value = if (settings.enabled) container.prefsStore.loadWallpaper(target, maxSide)?.asImageBitmap() else null
    }
    return bitmap to settings
}

/** Chat background: the user's wallpaper (cropped, optionally blurred and dimmed) or the theme background. */
@Composable
fun WallpaperBackground(target: WallpaperTarget, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val (bitmap, settings) = rememberWallpaper(target)
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(bg)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (settings.blur > 0f && Build.VERSION.SDK_INT >= 31) Modifier.blur(settings.blur.dp) else Modifier),
            )
            if (settings.dim > 0f) Box(Modifier.fillMaxSize().background(bg.copy(alpha = settings.dim)))
        }
        content()
    }
}

/** Colored unified diff. Long diffs are cut off to keep scrolling smooth. */
@Composable
fun DiffView(diff: String, modifier: Modifier = Modifier, maxLines: Int = 400) {
    val chat = LocalChatColors.current
    val lines = remember(diff) { diff.lines() }
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth()) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val minW = maxWidth
      Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp).width(IntrinsicSize.Max).widthIn(min = minW)) {
        for (line in lines.take(maxLines)) {
            val (fg, bgc) = when {
                line.startsWith("+++") || line.startsWith("---") -> onSurface.copy(alpha = 0.6f) to Color.Transparent
                line.startsWith("+") -> chat.diffAdded to chat.diffAdded.copy(alpha = 0.12f)
                line.startsWith("-") -> chat.diffRemoved to chat.diffRemoved.copy(alpha = 0.12f)
                line.startsWith("@@") -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else -> onSurface.copy(alpha = 0.8f) to Color.Transparent
            }
            Text(
                line.ifEmpty { " " },
                color = fg,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().background(bgc).padding(horizontal = 10.dp),
            )
        }
      }
    }
        if (lines.size > maxLines) {
            Text(
                "… ${lines.size - maxLines} more lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

/** "+12 −3" line stats. */
@Composable
fun DiffStat(added: Int?, removed: Int?) {
    val chat = LocalChatColors.current
    Row {
        if ((added ?: 0) > 0) Text("+$added", color = chat.diffAdded, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        if ((added ?: 0) > 0 && (removed ?: 0) > 0) HSpace(4)
        if ((removed ?: 0) > 0) Text("−$removed", color = chat.diffRemoved, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

/** Terminal-style monospace block. */
@Composable
fun CodeBlock(text: String, modifier: Modifier = Modifier, wrap: Boolean = true) {
    val chat = LocalChatColors.current
    Box(modifier.fillMaxWidth().background(chat.codeBackground)) {
        Text(
            text.ifEmpty { " " },
            color = Color(0xFFD7E3F5),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            softWrap = wrap,
            modifier = Modifier
                .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
