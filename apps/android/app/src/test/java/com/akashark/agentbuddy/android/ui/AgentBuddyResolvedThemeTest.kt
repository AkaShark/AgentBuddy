package com.akashark.agentbuddy.android.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors iOS `ResolvedThemeSemanticTests`: Mint keys win, other themes get derived roles. */
class AgentBuddyResolvedThemeTest {
    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.005f)
        assertEquals(expected.green, actual.green, 0.005f)
        assertEquals(expected.blue, actual.blue, 0.005f)
    }

    @Test
    fun blendMatchesIosVectors() {
        val black = Color(0xFF000000)
        val white = Color(0xFFFFFFFF)
        assertColor(white, AgentBuddyResolvedTheme.blend(black, over = white, alpha = 0f))
        assertColor(black, AgentBuddyResolvedTheme.blend(black, over = white, alpha = 1f))
        assertColor(
            Color(red = 0.5f, green = 0f, blue = 0.5f),
            AgentBuddyResolvedTheme.blend(Color(0xFFFF0000), over = Color(0xFF0000FF), alpha = 0.5f),
        )
    }

    @Test
    fun eightDigitHexDropsTrailingAlphaLikeIos() {
        assertEquals("#C3E7B5", sanitizeThemeHex("#C3E7B566"))
        assertEquals("#C3E7B5", sanitizeThemeHex("#C3E7B5"))
        assertColor(Color(0xFFC3E7B5), colorFromHex("#C3E7B566"))
    }

    @Test
    fun mintKeysAreReadExplicitly() {
        val theme =
            AgentBuddyResolvedTheme.resolve(
                slug = "agentbuddy-mint-light",
                definition =
                    AgentBuddyThemeDefinition(
                        name = "Mint",
                        type = AgentBuddyColorThemeType.LIGHT,
                        colors =
                            mapOf(
                                "editor.background" to "#F5F6F1",
                                "editor.foreground" to "#172B20",
                                "button.background" to "#172B20",
                                "agentbuddy.brand" to "#C3E7B5",
                                "agentbuddy.onBrand" to "#23442C",
                                "agentbuddy.onAction" to "#F5F6F1",
                                "agentbuddy.error" to "#A63832",
                                "agentbuddy.errorSurface" to "#FBE9E6",
                                "agentbuddy.disabled" to "#E1E6DE",
                            ),
                    ),
            )
        assertColor(Color(0xFFC3E7B5), theme.brand)
        assertColor(Color(0xFF23442C), theme.onBrand)
        assertColor(Color(0xFFF5F6F1), theme.textOnAccent)
        assertColor(Color(0xFFA63832), theme.danger)
        assertColor(Color(0xFFFBE9E6), theme.dangerSurface)
        assertColor(Color(0xFFE1E6DE), theme.disabled)
    }

    @Test
    fun legacyThemesDeriveRolesAndKeepTheirPalette() {
        val theme = AgentBuddyResolvedTheme.defaultLight
        assertColor(Color(0xFFD32F2F), theme.danger)
        assertColor(theme.surfaceLight, theme.disabled)
        assertColor(theme.textMuted, theme.onDisabled)
        assertColor(theme.accentStrong, theme.focus)
        assertColor(AgentBuddyResolvedTheme.blend(theme.accentStrong, over = theme.surface, alpha = 0.22f), theme.brand)
        assertColor(AgentBuddyResolvedTheme.blend(theme.danger, over = theme.background, alpha = 0.1f), theme.dangerSurface)
    }
}
