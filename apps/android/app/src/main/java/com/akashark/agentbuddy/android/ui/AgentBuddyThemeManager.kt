package com.akashark.agentbuddy.android.ui

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

private const val THEME_LOG_TAG = "AgentBuddyThemeManager"
private const val UI_PREFERENCES_NAME = "agentbuddy_ui_prefs"
private const val SELECTED_LIGHT_THEME_KEY = "selected_light_theme"
private const val SELECTED_DARK_THEME_KEY = "selected_dark_theme"
private const val APPEARANCE_MODE_KEY = "appearance_mode"
private const val DARK_MODE_KEY = "dark_mode_enabled"
private const val FONT_MONO_KEY = "font_family_mono"
const val DEFAULT_LIGHT_THEME = "agentbuddy-mint-light"
const val DEFAULT_DARK_THEME = "agentbuddy-mint-dark"

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

object AgentBuddyThemeManager {
    private val lock = Any()
    private var appContext: Context? = null
    private var initialized = false
    private var definitionCache = LinkedHashMap<String, AgentBuddyThemeDefinition>()
    private var systemIsDark = false

    var appearanceMode by mutableStateOf(AgentBuddyAppearanceMode.SYSTEM)
        private set

    /** Interface font: Berkeley Mono when true, the system font otherwise (default). */
    var monoFontEnabled by mutableStateOf(false)
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

    // Mint is the default only for users who never picked a theme: the keys are
    // written by an explicit selection, so an existing choice is kept as is.
    val selectedLightSlug: String
        get() = preferences?.getString(SELECTED_LIGHT_THEME_KEY, null) ?: DEFAULT_LIGHT_THEME

    val selectedDarkSlug: String
        get() = preferences?.getString(SELECTED_DARK_THEME_KEY, null) ?: DEFAULT_DARK_THEME

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
            // The system font is the default only when the user never chose one.
            monoFontEnabled = preferences?.getBoolean(FONT_MONO_KEY, false) ?: false
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
