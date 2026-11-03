package io.github.cametendo.opencowork.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.github.cametendo.opencowork.data.AppPrefs
import io.github.cametendo.opencowork.data.ColorSlot
import io.github.cametendo.opencowork.data.ThemeMode

// ---------------------------------------------------------------- base palettes

private val LightBlue = lightColorScheme(
    primary = Color(0xFF1D5FD1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0A2A66),
    secondary = Color(0xFF4A6FA5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8FB),
    onSecondaryContainer = Color(0xFF13305C),
    tertiary = Color(0xFF0E8FA8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEFF6),
    onTertiaryContainer = Color(0xFF003A45),
    background = Color(0xFFF2F7FF),
    onBackground = Color(0xFF0F1B2D),
    surface = Color(0xFFFBFDFF),
    onSurface = Color(0xFF0F1B2D),
    surfaceVariant = Color(0xFFE1EBF8),
    onSurfaceVariant = Color(0xFF4A5A70),
    surfaceTint = Color(0xFF1D5FD1),
    inverseSurface = Color(0xFF1B2A40),
    inverseOnSurface = Color(0xFFEAF1FC),
    inversePrimary = Color(0xFF9CC2FF),
    outline = Color(0xFF8CA0BC),
    outlineVariant = Color(0xFFCBD8EA),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E1),
    onErrorContainer = Color(0xFF5F0B0B),
    surfaceBright = Color(0xFFFBFDFF),
    surfaceDim = Color(0xFFD8E2F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F9FF),
    surfaceContainer = Color(0xFFEEF4FD),
    surfaceContainerHigh = Color(0xFFE7EFFB),
    surfaceContainerHighest = Color(0xFFDFE9F8),
)

private val DarkBlue = darkColorScheme(
    primary = Color(0xFF6FA8FF),
    onPrimary = Color(0xFF00214F),
    primaryContainer = Color(0xFF163E7A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF9DB8E0),
    onSecondary = Color(0xFF0D2547),
    secondaryContainer = Color(0xFF22385C),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Color(0xFF6ED3E8),
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF0B4B57),
    onTertiaryContainer = Color(0xFFCDEFF6),
    background = Color(0xFF0A1424),
    onBackground = Color(0xFFE3ECFA),
    surface = Color(0xFF0E1A2E),
    onSurface = Color(0xFFE3ECFA),
    surfaceVariant = Color(0xFF1C2C45),
    onSurfaceVariant = Color(0xFFA7B6CC),
    surfaceTint = Color(0xFF6FA8FF),
    inverseSurface = Color(0xFFE3ECFA),
    inverseOnSurface = Color(0xFF14233A),
    inversePrimary = Color(0xFF1D5FD1),
    outline = Color(0xFF5B6E8C),
    outlineVariant = Color(0xFF2A3B57),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF22324D),
    surfaceDim = Color(0xFF0A1424),
    surfaceContainerLowest = Color(0xFF07101D),
    surfaceContainerLow = Color(0xFF0E1A2E),
    surfaceContainer = Color(0xFF122139),
    surfaceContainerHigh = Color(0xFF172843),
    surfaceContainerHighest = Color(0xFF1D304E),
)

fun defaultColor(dark: Boolean, slot: ColorSlot): Color {
    val s = if (dark) DarkBlue else LightBlue
    return when (slot) {
        ColorSlot.ACCENT -> s.primary
        ColorSlot.BACKGROUND -> s.background
        ColorSlot.SURFACE -> s.surface
        ColorSlot.USER_BUBBLE -> s.primary
        ColorSlot.ASSISTANT_BUBBLE -> if (dark) Color(0xFF13223A) else Color.White
    }
}

// ---------------------------------------------------------------- extended (chat) colors

@Immutable
data class ChatColors(
    val userBubble: Color,
    val onUserBubble: Color,
    val assistantBubble: Color,
    val onAssistantBubble: Color,
    val codeBackground: Color,
    val success: Color,
    val warning: Color,
    val diffAdded: Color,
    val diffRemoved: Color,
    val isDark: Boolean,
)

val LocalChatColors = staticCompositionLocalOf {
    ChatColors(Color.Blue, Color.White, Color.White, Color.Black, Color(0xFF0B1220), Color(0xFF15803D), Color(0xFFB45309), Color(0xFF15803D), Color(0xFFB91C1C), false)
}

/** Readable text color on top of [bg]. */
fun contentColorFor(bg: Color): Color = if (bg.luminance() > 0.45f) Color(0xFF0F1B2D) else Color(0xFFF1F6FF)

private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

fun buildColorScheme(dark: Boolean, overrides: Map<ColorSlot, Int>): ColorScheme {
    var s = if (dark) DarkBlue else LightBlue
    overrides[ColorSlot.ACCENT]?.let { Color(it) }?.let { accent ->
        val on = contentColorFor(accent)
        val container = mix(accent, s.background, if (dark) 0.62f else 0.8f)
        s = s.copy(
            primary = accent,
            onPrimary = on,
            primaryContainer = container,
            onPrimaryContainer = contentColorFor(container),
            secondaryContainer = mix(accent, s.background, if (dark) 0.72f else 0.86f),
            onSecondaryContainer = contentColorFor(mix(accent, s.background, if (dark) 0.72f else 0.86f)),
            surfaceTint = accent,
            inversePrimary = mix(accent, if (dark) Color.Black else Color.White, 0.4f),
        )
    }
    overrides[ColorSlot.BACKGROUND]?.let { Color(it) }?.let { bg ->
        val on = contentColorFor(bg)
        s = s.copy(background = bg, onBackground = on, surfaceDim = bg)
    }
    overrides[ColorSlot.SURFACE]?.let { Color(it) }?.let { surf ->
        val on = contentColorFor(surf)
        val darkSurf = surf.luminance() < 0.45f
        val step = if (darkSurf) Color.White else Color(0xFF1D3A66)
        s = s.copy(
            surface = surf,
            onSurface = on,
            surfaceBright = mix(surf, step, 0.06f),
            surfaceContainerLowest = mix(surf, if (darkSurf) Color.Black else Color.White, 0.5f),
            surfaceContainerLow = surf,
            surfaceContainer = mix(surf, step, 0.04f),
            surfaceContainerHigh = mix(surf, step, 0.07f),
            surfaceContainerHighest = mix(surf, step, 0.1f),
            surfaceVariant = mix(surf, step, 0.09f),
            onSurfaceVariant = mix(on, surf, 0.3f),
            outlineVariant = mix(surf, on, 0.16f),
            outline = mix(surf, on, 0.45f),
        )
    }
    return s
}

fun buildChatColors(dark: Boolean, scheme: ColorScheme, overrides: Map<ColorSlot, Int>): ChatColors {
    val user = overrides[ColorSlot.USER_BUBBLE]?.let { Color(it) } ?: scheme.primary
    val assistant = overrides[ColorSlot.ASSISTANT_BUBBLE]?.let { Color(it) }
        ?: if (overrides[ColorSlot.SURFACE] != null) scheme.surfaceContainerLowest else defaultColor(dark, ColorSlot.ASSISTANT_BUBBLE)
    return ChatColors(
        userBubble = user,
        onUserBubble = contentColorFor(user),
        assistantBubble = assistant,
        onAssistantBubble = contentColorFor(assistant),
        codeBackground = if (dark) Color(0xFF060D18) else Color(0xFF0E1A2E),
        success = if (dark) Color(0xFF4ADE80) else Color(0xFF15803D),
        warning = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309),
        diffAdded = if (dark) Color(0xFF4ADE80) else Color(0xFF15803D),
        diffRemoved = if (dark) Color(0xFFF87171) else Color(0xFFB91C1C),
        isDark = dark,
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDarkTheme(prefs: AppPrefs): Boolean = when (prefs.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun OpenCoworkTheme(prefs: AppPrefs, content: @Composable () -> Unit) {
    val dark = isDarkTheme(prefs)
    val overrides = if (dark) prefs.darkColors else prefs.lightColors
    val scheme = remember(dark, overrides) { buildColorScheme(dark, overrides) }
    val chat = remember(dark, scheme, overrides) { buildChatColors(dark, scheme, overrides) }
    CompositionLocalProvider(LocalChatColors provides chat) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}

/** A translucent version of [c] laid over [under] — used for bars over wallpapers. */
fun Color.over(under: Color, alpha: Float) = copy(alpha = alpha).compositeOver(under)
