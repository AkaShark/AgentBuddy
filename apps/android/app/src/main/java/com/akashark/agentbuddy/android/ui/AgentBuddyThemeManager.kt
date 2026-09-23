package com.akashark.agentbuddy.android.ui

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

private const val THEME_LOG_TAG = "AgentBuddyThemeManager"
private const val UI_PREFERENCES_NAME = "agentbuddy_ui_prefs"
private const val SELECTED_LIGHT_THEME_KEY = "selected_light_theme"
private const val SELECTED_DARK_THEME_KEY = "selected_dark_theme"
private const val APPEARANCE_MODE_KEY = "appearance_mode"
private const val DARK_MODE_KEY = "dark_mode_enabled"
private const val FONT_MONO_KEY = "font_family_mono"

enum class AgentBuddyAppearanceMode(
    val storageValue: String,
    val displayName: String,
) {
    SYSTEM("system", "系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromStorageValue(value: String?): AgentBuddyAppearanceMode? =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) }
    }

    fun resolvesDarkTheme(systemIsDark: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemIsDark
            LIGHT -> false
            DARK -> true
        }
}

enum class AgentBuddyColorThemeType {
    LIGHT,
    DARK,
}

data class AgentBuddyThemeIndexEntry(
    val slug: String,
    val name: String,
    val type: AgentBuddyColorThemeType,
    val accentHex: String,
    val backgroundHex: String,
    val foregroundHex: String,
)

data class AgentBuddyThemeDefinition(
    val name: String,
    val type: AgentBuddyColorThemeType,
    val colors: Map<String, String>,
)

data class AgentBuddyResolvedTheme(
    val slug: String,
    val name: String,
    val type: AgentBuddyColorThemeType,
    val background: Color,
    val surface: Color,
    val surfaceLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textBody: Color,
    val textSystem: Color,
    val accent: Color,
    val accentStrong: Color,
    val border: Color,
    val separator: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val textOnAccent: Color,
    val codeBackground: Color,
) {
    companion object {
        val defaultLight =
            resolve(
                slug = "codex-light",
                definition =
                    AgentBuddyThemeDefinition(
                        name = "Codex Light",
                        type = AgentBuddyColorThemeType.LIGHT,
                        colors =
                            mapOf(
                                "editor.background" to "#FFFFFF",
                                "editor.foreground" to "#0D0D0D",
                                "sideBar.background" to "#FCFCFC",
                                "sideBar.foreground" to "#212121",
                                "activityBar.background" to "#FCFCFC",
                                "textLink.foreground" to "#0169CC",
                                "button.background" to "#0169CC",
                            ),
                    ),
            )

        val defaultDark =
            resolve(
                slug = "codex-dark",
                definition =
                    AgentBuddyThemeDefinition(
                        name = "Codex Dark",
                        type = AgentBuddyColorThemeType.DARK,
                        colors =
                            mapOf(
                                "editor.background" to "#111111",
                                "editor.foreground" to "#FCFCFC",
                                "sideBar.background" to "#131313",
                                "sideBar.foreground" to "#8F8F8F",
                                "activityBar.background" to "#131313",
                                "textLink.foreground" to "#0169CC",
                                "button.background" to "#0169CC",
                            ),
                    ),
            )

        fun resolve(
            slug: String,
            definition: AgentBuddyThemeDefinition,
        ): AgentBuddyResolvedTheme {
            val colors = definition.colors
            val background =
                colorFromHex(
                    colors["editor.background"],
                    fallback = if (definition.type == AgentBuddyColorThemeType.DARK) Color(0xFF111111) else Color.White,
                )
            val foreground =
                colorFromHex(
                    colors["editor.foreground"],
                    fallback = if (definition.type == AgentBuddyColorThemeType.DARK) Color(0xFFFCFCFC) else Color(0xFF0D0D0D),
                )
            val surface =
                colors["sideBar.background"]?.let(::colorFromHex)
                    ?: adjustBrightness(background, if (definition.type == AgentBuddyColorThemeType.DARK) 0.03f else -0.02f)
            val surfaceLight =
                colors["activityBar.background"]?.let(::colorFromHex)
                    ?: adjustBrightness(surface, if (definition.type == AgentBuddyColorThemeType.DARK) 0.04f else -0.03f)
            val accent =
                colors["textLink.foreground"]?.let(::colorFromHex)
                    ?: colors["button.background"]?.let(::colorFromHex)
                    ?: if (definition.type == AgentBuddyColorThemeType.DARK) Color(0xFFB0B0B0) else Color(0xFF4A4A4A)
            val accentStrong =
                colors["button.background"]?.let(::colorFromHex)
                    ?: colors["textLink.foreground"]?.let(::colorFromHex)
                    ?: accent
            val border =
                colors["editorGroup.border"]?.let(::colorFromHex)
                    ?: colors["sideBar.border"]?.let(::colorFromHex)
                    ?: adjustBrightness(surface, if (definition.type == AgentBuddyColorThemeType.DARK) 0.05f else -0.05f)
            val separator =
                colors["panel.border"]?.let(::colorFromHex)
                    ?: adjustBrightness(background, if (definition.type == AgentBuddyColorThemeType.DARK) 0.04f else -0.04f)

            return AgentBuddyResolvedTheme(
                slug = slug,
                name = definition.name,
                type = definition.type,
                background = background,
                surface = surface,
                surfaceLight = surfaceLight,
                textPrimary = foreground,
                textSecondary = colors["sideBar.foreground"]?.let(::colorFromHex) ?: dimColor(foreground, 0.55f),
                textMuted = colors["editorLineNumber.foreground"]?.let(::colorFromHex) ?: dimColor(foreground, 0.35f),
                textBody = dimColor(foreground, 0.88f),
                textSystem = dimColor(foreground, 0.7f),
                accent = accent,
                accentStrong = accentStrong,
                border = border,
                separator = separator,
                danger = if (definition.type == AgentBuddyColorThemeType.DARK) Color(0xFFFF5555) else Color(0xFFD32F2F),
                success = if (definition.type == AgentBuddyColorThemeType.DARK) Color(0xFF6EA676) else Color(0xFF2E7D32),
                warning = if (definition.type == AgentBuddyColorThemeType.DARK) Color(0xFFE2A644) else Color(0xFFE65100),
                textOnAccent = if (brightness(accentStrong) > 0.5f) Color(0xFF0D0D0D) else Color.White,
                codeBackground = background,
            )
        }

        fun brightness(color: Color): Float = (0.299f * color.red) + (0.587f * color.green) + (0.114f * color.blue)

        fun adjustBrightness(
            color: Color,
            amount: Float,
        ): Color =
            Color(
                red = (color.red + amount).coerceIn(0f, 1f),
                green = (color.green + amount).coerceIn(0f, 1f),
                blue = (color.blue + amount).coerceIn(0f, 1f),
                alpha = color.alpha,
            )

        fun dimColor(
            color: Color,
            factor: Float,
        ): Color =
            if (brightness(color) > 0.5f) {
                Color(
                    red = (color.red * factor).coerceIn(0f, 1f),
                    green = (color.green * factor).coerceIn(0f, 1f),
                    blue = (color.blue * factor).coerceIn(0f, 1f),
                    alpha = color.alpha,
                )
            } else {
                val inverse = 1f - factor
                Color(
                    red = (color.red + ((1f - color.red) * inverse)).coerceIn(0f, 1f),
                    green = (color.green + ((1f - color.green) * inverse)).coerceIn(0f, 1f),
                    blue = (color.blue + ((1f - color.blue) * inverse)).coerceIn(0f, 1f),
                    alpha = color.alpha,
                )
            }
    }
}

internal fun colorFromHex(
    hex: String?,
    fallback: Color = Color.Transparent,
): Color {
    val normalized = hex?.trim()?.takeIf { it.isNotEmpty() } ?: return fallback
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrElse { fallback }
}

object AgentBuddyThemeManager {
    private val lock = Any()
    private var appContext: Context? = null
    private var initialized = false
    private var definitionCache = LinkedHashMap<String, AgentBuddyThemeDefinition>()
    private var systemIsDark = false

    var appearanceMode by mutableStateOf(AgentBuddyAppearanceMode.SYSTEM)
        private set

    var monoFontEnabled by mutableStateOf(true)
        private set

    var lightTheme by mutableStateOf(AgentBuddyResolvedTheme.defaultLight)
        private set

    var darkTheme by mutableStateOf(AgentBuddyResolvedTheme.defaultDark)
        private set

    var activeTheme by mutableStateOf(AgentBuddyResolvedTheme.defaultDark)
        private set

    var themeVersion by mutableIntStateOf(0)
        private set

    var themeIndex by mutableStateOf<List<AgentBuddyThemeIndexEntry>>(emptyList())
        private set

    val lightThemes: List<AgentBuddyThemeIndexEntry>
        get() = themeIndex.filter { it.type == AgentBuddyColorThemeType.LIGHT }

    val darkThemes: List<AgentBuddyThemeIndexEntry>
        get() = themeIndex.filter { it.type == AgentBuddyColorThemeType.DARK }

    val selectedLightSlug: String
        get() = preferences?.getString(SELECTED_LIGHT_THEME_KEY, null) ?: "agentbuddy-light"

    val selectedDarkSlug: String
        get() = preferences?.getString(SELECTED_DARK_THEME_KEY, null) ?: "agentbuddy-dark"

    private val preferences
        get() = appContext?.getSharedPreferences(UI_PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) {
                return
            }
            appContext = context.applicationContext
            themeIndex = loadThemeIndex()
            lightTheme = loadAndResolve(selectedLightSlug) ?: AgentBuddyResolvedTheme.defaultLight
            darkTheme = loadAndResolve(selectedDarkSlug) ?: AgentBuddyResolvedTheme.defaultDark
            val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val systemIsDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            systemIsDark = systemIsDarkMode
            appearanceMode = loadAppearanceMode()
            activeTheme = themeForMode(appearanceMode)
            monoFontEnabled = preferences?.getBoolean(FONT_MONO_KEY, true) ?: true
            initialized = true
        }
    }

    fun applySystemTheme(isDark: Boolean) {
        systemIsDark = isDark
        applyActiveTheme()
    }

    fun applyDarkMode(enabled: Boolean) {
        applyAppearanceMode(if (enabled) AgentBuddyAppearanceMode.DARK else AgentBuddyAppearanceMode.LIGHT)
    }

    fun applyAppearanceMode(mode: AgentBuddyAppearanceMode) {
        preferences?.edit()?.putString(APPEARANCE_MODE_KEY, mode.storageValue)?.apply()
        if (appearanceMode != mode) {
            appearanceMode = mode
            themeVersion += 1
        }
        applyActiveTheme()
    }

    fun applyFont(isMono: Boolean) {
        preferences?.edit()?.putBoolean(FONT_MONO_KEY, isMono)?.apply()
        monoFontEnabled = isMono
    }

    fun selectLightTheme(slug: String) {
        preferences?.edit()?.putString(SELECTED_LIGHT_THEME_KEY, slug)?.apply()
        lightTheme = loadAndResolve(slug) ?: AgentBuddyResolvedTheme.defaultLight
        if (!usesDarkTheme()) {
            activeTheme = lightTheme
        }
        themeVersion += 1
    }

    fun selectDarkTheme(slug: String) {
        preferences?.edit()?.putString(SELECTED_DARK_THEME_KEY, slug)?.apply()
        darkTheme = loadAndResolve(slug) ?: AgentBuddyResolvedTheme.defaultDark
        if (usesDarkTheme()) {
            activeTheme = darkTheme
        }
        themeVersion += 1
    }

    private fun loadAppearanceMode(): AgentBuddyAppearanceMode {
        val prefs = preferences ?: return AgentBuddyAppearanceMode.SYSTEM
        AgentBuddyAppearanceMode.fromStorageValue(prefs.getString(APPEARANCE_MODE_KEY, null))?.let {
            return it
        }
        return if (prefs.contains(DARK_MODE_KEY)) {
            if (prefs.getBoolean(DARK_MODE_KEY, false)) {
                AgentBuddyAppearanceMode.DARK
            } else {
                AgentBuddyAppearanceMode.LIGHT
            }
        } else {
            AgentBuddyAppearanceMode.SYSTEM
        }
    }

    private fun usesDarkTheme(mode: AgentBuddyAppearanceMode = appearanceMode): Boolean =
        mode.resolvesDarkTheme(systemIsDark)

    private fun themeForMode(mode: AgentBuddyAppearanceMode): AgentBuddyResolvedTheme =
        if (usesDarkTheme(mode)) {
            darkTheme
        } else {
            lightTheme
        }

    private fun applyActiveTheme() {
        val nextTheme = themeForMode(appearanceMode)
        if (activeTheme.slug != nextTheme.slug || activeTheme.type != nextTheme.type) {
            activeTheme = nextTheme
        }
    }

    private fun loadThemeIndex(): List<AgentBuddyThemeIndexEntry> {
        val context = appContext ?: return emptyList()
        return runCatching {
            context.assets.open("theme-manifest.json").bufferedReader().use { reader ->
                val array = JSONArray(reader.readText())
                buildList(array.length()) {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            AgentBuddyThemeIndexEntry(
                                slug = item.optString("slug"),
                                name = item.optString("name"),
                                type = item.optString("type").toThemeType(),
                                accentHex = item.optString("accentHex"),
                                backgroundHex = item.optString("backgroundHex"),
                                foregroundHex = item.optString("foregroundHex"),
                            ),
                        )
                    }
                }
            }
        }.onFailure { error ->
            Log.w(THEME_LOG_TAG, "Failed to load theme manifest", error)
        }.getOrDefault(emptyList())
    }

    private fun loadAndResolve(slug: String): AgentBuddyResolvedTheme? {
        val definition = loadDefinition(slug) ?: return null
        return AgentBuddyResolvedTheme.resolve(slug = slug, definition = definition)
    }

    private fun loadDefinition(slug: String): AgentBuddyThemeDefinition? {
        definitionCache[slug]?.let { return it }
        val context = appContext ?: return null
        return runCatching {
            context.assets.open("$slug.json").bufferedReader().use { reader ->
                parseThemeDefinition(JSONObject(reader.readText())).also { parsed ->
                    definitionCache[slug] = parsed
                }
            }
        }.onFailure { error ->
            Log.w(THEME_LOG_TAG, "Failed to load theme $slug", error)
        }.getOrNull()
    }

    private fun parseThemeDefinition(json: JSONObject): AgentBuddyThemeDefinition {
        val colorsJson = json.optJSONObject("colors") ?: JSONObject()
        val colors = LinkedHashMap<String, String>()
        val keys = colorsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            colors[key] = colorsJson.optString(key)
        }
        return AgentBuddyThemeDefinition(
            name = json.optString("name"),
            type = json.optString("type").toThemeType(),
            colors = colors,
        )
    }
}

private fun String.toThemeType(): AgentBuddyColorThemeType =
    if (equals("light", ignoreCase = true)) {
        AgentBuddyColorThemeType.LIGHT
    } else {
        AgentBuddyColorThemeType.DARK
    }
