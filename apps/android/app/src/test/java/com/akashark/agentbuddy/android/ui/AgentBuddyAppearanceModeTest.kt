package com.akashark.agentbuddy.android.ui

import android.app.UiModeManager
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentBuddyAppearanceModeTest {
    @Test
    fun parsesStoredAppearanceModes() {
        assertEquals(AgentBuddyAppearanceMode.SYSTEM, AgentBuddyAppearanceMode.fromStorageValue("system"))
        assertEquals(AgentBuddyAppearanceMode.LIGHT, AgentBuddyAppearanceMode.fromStorageValue("LIGHT"))
        assertEquals(AgentBuddyAppearanceMode.DARK, AgentBuddyAppearanceMode.fromStorageValue("dark"))
    }

    @Test
    fun ignoresUnknownStoredAppearanceMode() {
        assertNull(AgentBuddyAppearanceMode.fromStorageValue("sepia"))
        assertNull(AgentBuddyAppearanceMode.fromStorageValue(null))
    }

    @Test
    fun resolvesDarkThemeFromSystemPreference() {
        assertEquals(false, AgentBuddyAppearanceMode.SYSTEM.resolvesDarkTheme(systemIsDark = false))
        assertEquals(true, AgentBuddyAppearanceMode.SYSTEM.resolvesDarkTheme(systemIsDark = true))
        assertEquals(false, AgentBuddyAppearanceMode.LIGHT.resolvesDarkTheme(systemIsDark = true))
        assertEquals(true, AgentBuddyAppearanceMode.DARK.resolvesDarkTheme(systemIsDark = false))
    }

    @Test
    fun systemAppearanceClearsThePersistentApplicationOverride() {
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, AgentBuddyAppearanceMode.SYSTEM.applicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_NO, AgentBuddyAppearanceMode.LIGHT.applicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_YES, AgentBuddyAppearanceMode.DARK.applicationNightMode())
    }

    @Test
    fun globalPreferenceWinsOverThePreviousApplicationOverride() {
        assertEquals(false, resolveSystemDarkTheme(UiModeManager.MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES, true))
        assertEquals(true, resolveSystemDarkTheme(UiModeManager.MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_NO, false))
    }

    @Test
    fun scheduledSystemAppearanceUsesTheFrameworksResolvedConfiguration() {
        for (mode in listOf(UiModeManager.MODE_NIGHT_AUTO, UiModeManager.MODE_NIGHT_CUSTOM)) {
            assertEquals(false, resolveSystemDarkTheme(mode, Configuration.UI_MODE_NIGHT_NO, true))
            assertEquals(true, resolveSystemDarkTheme(mode, Configuration.UI_MODE_NIGHT_YES, false))
        }
        assertEquals(true, resolveSystemDarkTheme(null, Configuration.UI_MODE_NIGHT_UNDEFINED, true))
        assertEquals(false, resolveSystemDarkTheme(null, Configuration.UI_MODE_NIGHT_UNDEFINED, false))
    }
}
