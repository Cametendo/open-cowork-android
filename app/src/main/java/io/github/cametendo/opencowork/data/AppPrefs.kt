package io.github.cametendo.opencowork.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

private val Context.prefsDataStore by preferencesDataStore(name = "app_prefs")

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light blue"), DARK("Dark blue") }

/** The colors a user can change, per theme (light / dark). */
enum class ColorSlot(val key: String, val label: String, val description: String) {
    ACCENT("accent", "Accent", "Buttons, links, highlights and the send button"),
    BACKGROUND("background", "Background", "Behind every screen (and behind chats without a wallpaper)"),
    SURFACE("surface", "Cards & bars", "Top bars, cards, sheets and the message box"),
    USER_BUBBLE("user_bubble", "Your messages", "Bubble color of the messages you send"),
    ASSISTANT_BUBBLE("assistant_bubble", "Assistant messages", "Bubble behind replies (shown over wallpapers)"),
}

enum class WallpaperTarget(val key: String, val label: String) { CHAT("chat", "Chats"), COWORK("cowork", "Cowork tasks") }

data class WallpaperSettings(
    /** 0 = no wallpaper; otherwise the time it was set (used to refresh the image). */
    val version: Long = 0,
    /** 0..0.9 — how much the theme background is laid over the image. */
    val dim: Float = 0.35f,
    /** Blur radius in dp (Android 12+). */
    val blur: Float = 0f,
) {
    val enabled get() = version != 0L
}

data class AppPrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lightColors: Map<ColorSlot, Int> = emptyMap(),
    val darkColors: Map<ColorSlot, Int> = emptyMap(),
    val wallpapers: Map<WallpaperTarget, WallpaperSettings> = emptyMap(),
    val coworkNotifications: Boolean = true,
    val replyNotifications: Boolean = true,
    val lastChatModel: String? = null,
    val lastCoworkModel: String? = null,
    val webSearch: Boolean = false,
) {
    fun wallpaper(t: WallpaperTarget) = wallpapers[t] ?: WallpaperSettings()
}

class AppPrefsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val coworkNotifKey = booleanPreferencesKey("cowork_notifications")
    private val replyNotifKey = booleanPreferencesKey("reply_notifications")
    private val lastChatModelKey = stringPreferencesKey("last_chat_model")
    private val lastCoworkModelKey = stringPreferencesKey("last_cowork_model")
    private val webSearchKey = booleanPreferencesKey("web_search")
    private fun colorKey(dark: Boolean, slot: ColorSlot) = intPreferencesKey("${if (dark) "dark" else "light"}_${slot.key}")
    private fun wpVersion(t: WallpaperTarget) = longPreferencesKey("wp_${t.key}_version")
    private fun wpDim(t: WallpaperTarget) = floatPreferencesKey("wp_${t.key}_dim")
    private fun wpBlur(t: WallpaperTarget) = floatPreferencesKey("wp_${t.key}_blur")

    val prefs: Flow<AppPrefs> = context.prefsDataStore.data.map { it.toPrefs() }

    private fun Preferences.toPrefs() = AppPrefs(
        themeMode = runCatching { ThemeMode.valueOf(this[themeKey] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        lightColors = ColorSlot.entries.mapNotNull { s -> this[colorKey(false, s)]?.let { s to it } }.toMap(),
        darkColors = ColorSlot.entries.mapNotNull { s -> this[colorKey(true, s)]?.let { s to it } }.toMap(),
        wallpapers = WallpaperTarget.entries.associateWith { t ->
            WallpaperSettings(this[wpVersion(t)] ?: 0L, this[wpDim(t)] ?: 0.35f, this[wpBlur(t)] ?: 0f)
        },
        coworkNotifications = this[coworkNotifKey] ?: true,
        replyNotifications = this[replyNotifKey] ?: true,
        lastChatModel = this[lastChatModelKey],
        lastCoworkModel = this[lastCoworkModelKey],
        webSearch = this[webSearchKey] ?: false,
    )

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.prefsDataStore.edit(block)
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[themeKey] = mode.name }
    suspend fun setColor(dark: Boolean, slot: ColorSlot, argb: Int?) = edit {
        if (argb == null) it.remove(colorKey(dark, slot)) else it[colorKey(dark, slot)] = argb
    }
    suspend fun resetColors(dark: Boolean) = edit { p -> ColorSlot.entries.forEach { p.remove(colorKey(dark, it)) } }
    suspend fun setCoworkNotifications(on: Boolean) = edit { it[coworkNotifKey] = on }
    suspend fun setReplyNotifications(on: Boolean) = edit { it[replyNotifKey] = on }
    suspend fun setLastChatModel(id: String) = edit { it[lastChatModelKey] = id }
    suspend fun setLastCoworkModel(id: String) = edit { it[lastCoworkModelKey] = id }
    suspend fun setWebSearch(on: Boolean) = edit { it[webSearchKey] = on }
    suspend fun setWallpaperLook(t: WallpaperTarget, dim: Float, blur: Float) = edit {
        it[wpDim(t)] = dim
        it[wpBlur(t)] = blur
    }

    // ------------------------------------------------------------------ wallpaper files

    fun wallpaperFile(t: WallpaperTarget) = File(File(context.filesDir, "wallpapers"), "${t.key}.jpg")

    /** Copy (and downscale) the picked image into app storage. */
    suspend fun setWallpaper(t: WallpaperTarget, uri: Uri) {
        withContext(Dispatchers.IO) {
            val bitmap = decodeSampled(context, uri, 2400) ?: throw IllegalArgumentException("That image couldn't be opened")
            val file = wallpaperFile(t)
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        edit { it[wpVersion(t)] = System.currentTimeMillis() }
    }

    suspend fun copyWallpaper(from: WallpaperTarget, to: WallpaperTarget, look: WallpaperSettings) {
        withContext(Dispatchers.IO) { wallpaperFile(from).copyTo(wallpaperFile(to), overwrite = true) }
        edit {
            it[wpVersion(to)] = System.currentTimeMillis()
            it[wpDim(to)] = look.dim
            it[wpBlur(to)] = look.blur
        }
    }

    suspend fun removeWallpaper(t: WallpaperTarget) {
        withContext(Dispatchers.IO) { wallpaperFile(t).delete() }
        edit { it.remove(wpVersion(t)) }
    }

    suspend fun loadWallpaper(t: WallpaperTarget, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = wallpaperFile(t)
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        BitmapFactory.decodeFile(f.path, opts)
    }
}

fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
    var sample = 1
    val longest = max(w, h)
    while (longest / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/** Decode an image from a content Uri, downsampled so its longest side is about [maxSide] px. */
fun decodeSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val longest = max(decoded.width, decoded.height)
    if (longest <= maxSide) return decoded
    val scale = maxSide.toFloat() / longest
    val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    if (scaled !== decoded) decoded.recycle()
    return scaled
}
