package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyColorThemeType
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeIndexEntry
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.colorFromHex
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

// ═══════════════════════════════════════════════════════════════════════════════
// Theme Picker Sheet (matches iOS ThemePickerSheet)
// ═══════════════════════════════════════════════════════════════════════════════

/** A titled run of themes in the picker; the title is null for search results. */
internal data class ThemePickerSection(
    val title: String?,
    val entries: List<AgentBuddyThemeIndexEntry>,
)

private const val BrandThemePrefix = "agentbuddy-"

/** Row padding + 28dp swatch + gap, so separators start under the theme name. */
private val ThemeRowDividerIndent = BuddySpacing.md + 28.dp + BuddySpacing.sm

/**
 * Every theme stays selectable. Without a search the AgentBuddy themes (Mint
 * first) are listed under 「推荐」, followed by the rest under 「全部主题」;
 * a search shows one flat list of matches.
 */
internal fun themePickerSections(
    themes: List<AgentBuddyThemeIndexEntry>,
    query: String,
): List<ThemePickerSection> {
    val trimmed = query.trim()
    if (trimmed.isNotEmpty()) {
        val matches =
            themes.filter {
                it.name.contains(trimmed, ignoreCase = true) || it.slug.contains(trimmed, ignoreCase = true)
            }
        return if (matches.isEmpty()) emptyList() else listOf(ThemePickerSection(null, matches))
    }
    val brand = themes.filter { it.slug.startsWith(BrandThemePrefix) }
    if (brand.isEmpty()) return if (themes.isEmpty()) emptyList() else listOf(ThemePickerSection(null, themes))
    val suggested = brand.filter { "mint" in it.slug } + brand.filterNot { "mint" in it.slug }
    val others = themes.filterNot { it.slug.startsWith(BrandThemePrefix) }
    return listOfNotNull(
        ThemePickerSection("推荐", suggested),
        others.takeIf { it.isNotEmpty() }?.let { ThemePickerSection("全部主题", it) },
    )
}

@Composable
internal fun ThemePickerContent(
    title: String,
    themes: List<AgentBuddyThemeIndexEntry>,
    selectedSlug: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val sections = remember(themes, searchQuery) { themePickerSections(themes, searchQuery) }

    Column(modifier.fillMaxWidth().imePadding()) {
        SettingsPageHeader(title = title, onDone = onDismiss)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                ThemeSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.padding(horizontal = settingsGutter()).padding(top = BuddySpacing.xs),
                )
                if (sections.isEmpty()) {
                    ThemePickerEmptyState(searchQuery.trim())
                } else {
                    LazyColumn(
                        contentPadding =
                            PaddingValues(
                                start = settingsGutter(),
                                end = settingsGutter(),
                                bottom = BuddySpacing.xxl,
                            ),
                    ) {
                        sections.forEach { section ->
                            settingsSection(title = section.title, key = section.title ?: "results") {
                                section.entries.forEachIndexed { index, entry ->
                                    if (index > 0) SettingsRowDivider(startIndent = ThemeRowDividerIndent)
                                    SettingsSelectRow(
                                        title = entry.name,
                                        selected = entry.slug == selectedSlug,
                                        onClick = { onSelect(entry.slug) },
                                        leading = { ThemePreviewBadge(entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 48dp search field: control outline, focus colour while editing, clear button. */
@Composable
private fun ThemeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = BuddySize.control)
                .background(AgentBuddyTheme.surface, BuddyShapes.control)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) AgentBuddyTheme.focus else AgentBuddyTheme.borderControl,
                    shape = BuddyShapes.control,
                ).padding(start = BuddySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(BuddySize.icon))
        Box(Modifier.weight(1f).padding(vertical = BuddySpacing.sm)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = buddyTextStyle(BuddyTextStyle.BODY).copy(color = AgentBuddyTheme.textPrimary),
                cursorBrush = SolidColor(AgentBuddyTheme.focus),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "搜索主题" },
            )
            if (query.isEmpty()) {
                Text(
                    text = "搜索主题",
                    style = buddyTextStyle(BuddyTextStyle.BODY),
                    color = AgentBuddyTheme.textSecondary,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        if (query.isNotEmpty()) {
            BuddyIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "清除搜索",
                onClick = { onQueryChange("") },
                tint = AgentBuddyTheme.textSecondary,
            )
        }
    }
}

@Composable
private fun ThemePickerEmptyState(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = BuddySpacing.xxxl, start = BuddySpacing.xl, end = BuddySpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(BuddySize.icon))
        Text("没有匹配的主题", style = buddyTextStyle(BuddyTextStyle.HEADING), color = AgentBuddyTheme.textPrimary)
        if (query.isNotEmpty()) {
            Text(query, style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal), color = AgentBuddyTheme.textSecondary)
        }
    }
}

/** Swatch drawn from the theme's own colours (data, not interface colour). */
@Composable
internal fun ThemePreviewBadge(entry: AgentBuddyThemeIndexEntry) {
    val isDarkTheme = entry.type == AgentBuddyColorThemeType.DARK
    val bg = themeSwatchColor(entry.backgroundHex, fallback = if (isDarkTheme) Color.Black else Color.White)
    val fg = themeSwatchColor(entry.foregroundHex, fallback = if (isDarkTheme) Color.White else Color.Black)
    val accent = themeSwatchColor(entry.accentHex, fallback = AgentBuddyTheme.link)
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.clearAndSetSemantics {}) {
        Box(
            Modifier
                .size(width = 28.dp, height = 22.dp)
                .background(bg, shape)
                .border(1.dp, AgentBuddyTheme.border, shape),
            contentAlignment = Alignment.Center,
        ) {
            // Fixed size on purpose: the swatch is a picture of the theme, not text to read.
            Text("Aa", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = BerkeleyMono)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(6.dp)
                .background(accent, CircleShape),
        )
    }
}

/** Theme swatch colour; also accepts CSS short hex ("#fff"), which some themes use. */
internal fun themeSwatchColor(hex: String, fallback: Color): Color {
    val trimmed = hex.trim()
    val expanded =
        if (trimmed.length == 4 && trimmed.startsWith("#")) {
            "#" + trimmed.drop(1).map { "$it$it" }.joinToString("")
        } else {
            trimmed
        }
    return colorFromHex(expanded, fallback)
}

/** Current theme with an up/down marker; opens the picker. */
@Composable
internal fun ThemePickerButton(
    entry: AgentBuddyThemeIndexEntry?,
    onClick: () -> Unit,
    onClickLabel: String,
) {
    SettingsRow(
        title = entry?.name ?: "未知主题",
        onClick = onClick,
        onClickLabel = onClickLabel,
        leading = entry?.let { { ThemePreviewBadge(it) } },
        trailing = {
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = null,
                tint = AgentBuddyTheme.textSecondary,
                modifier = Modifier.size(BuddySize.icon),
            )
        },
    )
}
