package io.github.cametendo.opencowork.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cametendo.opencowork.data.ColorSlot
import io.github.cametendo.opencowork.data.ThemeMode
import io.github.cametendo.opencowork.data.WallpaperTarget
import io.github.cametendo.opencowork.ui.LocalContainer
import io.github.cametendo.opencowork.ui.components.ColorPickerDialog
import io.github.cametendo.opencowork.ui.components.SectionHeader
import io.github.cametendo.opencowork.ui.components.SettingsCard
import io.github.cametendo.opencowork.ui.components.hex
import io.github.cametendo.opencowork.ui.components.rememberActions
import io.github.cametendo.opencowork.ui.components.rememberWallpaper
import io.github.cametendo.opencowork.ui.theme.AppShapes
import io.github.cametendo.opencowork.ui.theme.LocalChatColors
import io.github.cametendo.opencowork.ui.theme.buildChatColors
import io.github.cametendo.opencowork.ui.theme.buildColorScheme
import io.github.cametendo.opencowork.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val prefs by container.prefs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val currentlyDark = isDarkTheme(prefs)
    var editDark by rememberSaveable { mutableStateOf(currentlyDark) }
    var picking by remember { mutableStateOf<ColorSlot?>(null) }

    val overrides = if (editDark) prefs.darkColors else prefs.lightColors
    val scheme = remember(editDark, overrides) { buildColorScheme(editDark, overrides) }
    val chat = remember(editDark, scheme, overrides) { buildChatColors(editDark, scheme, overrides) }

    fun slotColor(slot: ColorSlot): Color = when (slot) {
        ColorSlot.ACCENT -> scheme.primary
        ColorSlot.BACKGROUND -> scheme.background
        ColorSlot.SURFACE -> scheme.surface
        ColorSlot.USER_BUBBLE -> chat.userBubble
        ColorSlot.ASSISTANT_BUBBLE -> chat.assistantBubble
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = { Text("Appearance") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            // ------------------------------------------------------------ theme
            SectionHeader("Theme")
            SettingsCard {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { container.prefsStore.setThemeMode(mode) } }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            when (mode) { ThemeMode.SYSTEM -> Icons.Outlined.PhoneAndroid; ThemeMode.LIGHT -> Icons.Outlined.LightMode; ThemeMode.DARK -> Icons.Outlined.DarkMode },
                            null, tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Light blue when your phone is light, dark blue when it's dark"
                                    ThemeMode.LIGHT -> "Always the light blue theme"
                                    ThemeMode.DARK -> "Always the dark blue theme"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RadioButton(selected = prefs.themeMode == mode, onClick = { scope.launch { container.prefsStore.setThemeMode(mode) } })
                    }
                }
            }

            // ------------------------------------------------------------ colors
            SectionHeader("Colors")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                SegmentedButton(selected = !editDark, onClick = { editDark = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Outlined.LightMode, null, Modifier.size(18.dp)) }) { Text("Light blue") }
                SegmentedButton(selected = editDark, onClick = { editDark = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Outlined.DarkMode, null, Modifier.size(18.dp)) }) { Text("Dark blue") }
            }
            // Live preview of the palette being edited
            MaterialTheme(colorScheme = scheme, shapes = AppShapes) {
                CompositionLocalProvider(LocalChatColors provides chat) { ThemePreview() }
            }
            SettingsCard {
                ColorSlot.entries.forEachIndexed { i, slot ->
                    if (i > 0) Divider()
                    val c = slotColor(slot)
                    Row(
                        Modifier.fillMaxWidth().clickable { picking = slot }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(slot.label, style = MaterialTheme.typography.bodyLarge)
                            Text(slot.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (overrides.containsKey(slot)) c.hex() else "Default",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (overrides.containsKey(slot)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(
                onClick = { scope.launch { container.prefsStore.resetColors(editDark) } },
                enabled = overrides.isNotEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Outlined.RestartAlt, null)
                Text("  Reset ${if (editDark) "dark blue" else "light blue"} colors")
            }

            // ------------------------------------------------------------ wallpapers
            SectionHeader("Chat wallpapers")
            Text(
                "Set a background image for normal chats and for Cowork tasks. Dim keeps text readable; blur softens busy photos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            WallpaperTarget.entries.forEach { target -> WallpaperEditor(target) }
        }
    }

    picking?.let { slot ->
        ColorPickerDialog(
            title = "${slot.label} · ${if (editDark) "dark" else "light"}",
            initial = slotColor(slot),
            default = remember(editDark, slot) { buildColorScheme(editDark, emptyMap()).let { s ->
                when (slot) {
                    ColorSlot.ACCENT -> s.primary
                    ColorSlot.BACKGROUND -> s.background
                    ColorSlot.SURFACE -> s.surface
                    ColorSlot.USER_BUBBLE -> s.primary
                    ColorSlot.ASSISTANT_BUBBLE -> buildChatColors(editDark, s, emptyMap()).assistantBubble
                }
            } },
            onDismiss = { picking = null },
            onPick = { c -> scope.launch { container.prefsStore.setColor(editDark, slot, c?.toArgb()) } },
        )
    }
}

@Composable
private fun ThemePreview() {
    val chat = LocalChatColors.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.background,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column {
            Surface(color = scheme.surface) {
                Text("Preview", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp))
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(color = chat.userBubble, shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)) {
                        Text("Can you summarize this?", color = chat.onUserBubble, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Surface(color = chat.assistantBubble, shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)) {
                    Text("Sure! Here are the key points…", color = chat.onAssistantBubble, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = scheme.surfaceContainerLowest, modifier = Modifier.weight(1f).height(40.dp)) {
                        Box(contentAlignment = Alignment.CenterStart) { Text("Message", color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp)) }
                    }
                    FilledIconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WallpaperEditor(target: WallpaperTarget) {
    val container = LocalContainer.current
    val prefs by container.prefs.collectAsStateWithLifecycle()
    val actions = rememberActions()
    val settings = prefs.wallpaper(target)
    val (bitmap, _) = rememberWallpaper(target)
    var dim by remember(settings.version) { mutableFloatStateOf(settings.dim) }
    var blur by remember(settings.version) { mutableFloatStateOf(settings.blur) }
    LaunchedEffect(settings.dim, settings.blur) { dim = settings.dim; blur = settings.blur }
    val other = WallpaperTarget.entries.first { it != target }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.launch("${target.label} wallpaper set") { container.prefsStore.setWallpaper(target, uri) }
    }

    SettingsCard(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.width(96.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().then(if (blur > 0 && Build.VERSION.SDK_INT >= 31) Modifier.blur((blur / 3).dp) else Modifier),
                    )
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = dim)))
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.align(Alignment.End).width(44.dp).height(10.dp).clip(CircleShape).background(LocalChatColors.current.userBubble))
                        Box(Modifier.width(56.dp).height(10.dp).clip(CircleShape).background(LocalChatColors.current.assistantBubble))
                        Box(Modifier.align(Alignment.End).width(32.dp).height(10.dp).clip(CircleShape).background(LocalChatColors.current.userBubble))
                    }
                } else Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(target.label, style = MaterialTheme.typography.titleMedium)
                Text(if (settings.enabled) "Custom wallpaper" else "No wallpaper (theme background)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (settings.enabled) "Change image" else "Choose image")
                }
                if (settings.enabled) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { actions.launch { container.prefsStore.removeWallpaper(target) } }) {
                        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp)); Text(" Remove")
                    }
                }
            }
        }
        if (settings.enabled) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Text("Dim · ${(dim * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(dim, { dim = it }, valueRange = 0f..0.9f, onValueChangeFinished = { actions.launch { container.prefsStore.setWallpaperLook(target, dim, blur) } })
                if (Build.VERSION.SDK_INT >= 31) {
                    Text("Blur · ${blur.toInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(blur, { blur = it }, valueRange = 0f..30f, onValueChangeFinished = { actions.launch { container.prefsStore.setWallpaperLook(target, dim, blur) } })
                }
                TextButton(onClick = { actions.launch("Also used for ${other.label.lowercase()}") { container.prefsStore.copyWallpaper(target, other, settings.copy(dim = dim, blur = blur)) } }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Text("  Use for ${other.label.lowercase()} too")
                }
            }
        }
    }
}
