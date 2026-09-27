package com.akashark.agentbuddy.android.ui.settings

import com.akashark.agentbuddy.android.ui.AgentBuddyColorThemeType
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePickerSectionsTest {
    private fun theme(slug: String, name: String = slug) =
        AgentBuddyThemeIndexEntry(
            slug = slug,
            name = name,
            type = AgentBuddyColorThemeType.LIGHT,
            accentHex = "#3D7149",
            backgroundHex = "#FFFFFF",
            foregroundHex = "#000000",
        )

    private val themes =
        listOf(
            theme("absolutely-light", "Absolutely Light"),
            theme("agentbuddy-light", "AgentBuddy Light"),
            theme("github-light"),
            theme("agentbuddy-mint-light", "AgentBuddy Mint Light"),
        )

    @Test
    fun groupsAgentBuddyThemesFirstWithMintOnTop() {
        val sections = themePickerSections(themes, query = "")

        assertEquals(listOf("推荐", "全部主题"), sections.map { it.title })
        assertEquals(listOf("agentbuddy-mint-light", "agentbuddy-light"), sections[0].entries.map { it.slug })
        assertEquals(listOf("absolutely-light", "github-light"), sections[1].entries.map { it.slug })
    }

    @Test
    fun searchShowsOneFlatListMatchingNameOrSlug() {
        val byName = themePickerSections(themes, query = "  mint ")
        assertEquals(1, byName.size)
        assertEquals(null, byName[0].title)
        assertEquals(listOf("agentbuddy-mint-light"), byName[0].entries.map { it.slug })

        val bySlug = themePickerSections(themes, query = "GITHUB")
        assertEquals(listOf("github-light"), bySlug.single().entries.map { it.slug })
    }

    @Test
    fun noMatchesAndNoBrandThemes() {
        assertTrue(themePickerSections(themes, query = "solarized").isEmpty())

        val plain = listOf(theme("github-light"), theme("nord-light"))
        val sections = themePickerSections(plain, query = "")
        assertEquals(listOf<String?>(null), sections.map { it.title })
        assertEquals(plain, sections.single().entries)
    }
}
