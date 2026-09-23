package com.akashark.agentbuddy.android.ui

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
}
