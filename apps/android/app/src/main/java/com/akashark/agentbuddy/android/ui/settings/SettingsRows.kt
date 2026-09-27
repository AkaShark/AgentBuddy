package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChevron
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

// Mint grouped-list styling for the settings screens (iOS SettingsMintStyle parity):
// caption section headers, surface groups with hairline separators, 24dp icon
// column, body titles and caption subtitles.

/** Minimum row height inside a settings group. */
internal val SettingsRowMinHeight = 56.dp

/** Leading inset of separators under rows that have an icon (padding + icon column + gap). */
private val SettingsIconIndent = BuddySpacing.md + BuddySize.iconLarge + BuddySpacing.sm

/** Caption section header in textSecondary, sentence case. */
@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
        color = AgentBuddyTheme.textSecondary,
        modifier =
            modifier
                .padding(start = BuddySpacing.xxs, top = BuddySpacing.xl, bottom = BuddySpacing.xs)
                .semantics { heading() },
    )
}

/** Caption footer under a group, optionally led by an icon. */
@Composable
internal fun SettingsFooter(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = AgentBuddyTheme.textSecondary,
) {
    Row(
        modifier = modifier.padding(start = BuddySpacing.xxs, end = BuddySpacing.xxs, top = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        }
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.CAPTION),
            color = AgentBuddyTheme.textSecondary,
        )
    }
}

/** Surface group holding rows separated by [SettingsRowDivider]. */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.card, padding = null),
        content = content,
    )
}

/** Hairline separator between rows of a group; indented past the icon column. */
@Composable
internal fun SettingsRowDivider(indentForIcon: Boolean = true) {
    BuddyDivider(startIndent = if (indentForIcon) SettingsIconIndent else BuddySpacing.md)
}

/** Selected marker for single-choice rows. Selection is also exposed to TalkBack by the row. */
@Composable
internal fun SettingsCheckmark() {
    Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = null,
        tint = AgentBuddyTheme.link,
        modifier = Modifier.size(BuddySize.icon),
    )
}

/** Small indeterminate spinner for rows that are loading. */
@Composable
internal fun SettingsSpinner(modifier: Modifier = Modifier) {
    Box(modifier.width(BuddySize.iconLarge), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = AgentBuddyTheme.textSecondary,
            strokeWidth = 2.dp,
        )
    }
}

/** Mint switch colours: action track when on, control outline when off. */
@Composable
internal fun settingsSwitchColors(): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = AgentBuddyTheme.onAction,
        checkedTrackColor = AgentBuddyTheme.action,
        checkedBorderColor = AgentBuddyTheme.action,
        checkedIconColor = AgentBuddyTheme.action,
        uncheckedThumbColor = AgentBuddyTheme.borderControl,
        uncheckedTrackColor = AgentBuddyTheme.surfaceSoft,
        uncheckedBorderColor = AgentBuddyTheme.borderControl,
        disabledCheckedTrackColor = AgentBuddyTheme.disabled,
        disabledUncheckedTrackColor = AgentBuddyTheme.disabled,
    )

/**
 * Icon + title + optional subtitle + trailing content. Tappable when
 * [onClick] is set. Titles are Body, subtitles Caption; both wrap.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = AgentBuddyTheme.textSecondary,
    titleColor: Color = AgentBuddyTheme.textPrimary,
    titleWeight: FontWeight? = null,
    subtitleColor: Color = AgentBuddyTheme.textSecondary,
    subtitleFontFamily: FontFamily? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRowMinHeight)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            icon != null ->
                Box(Modifier.width(BuddySize.iconLarge), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
        }
        Column(
            modifier = Modifier.weight(1f).padding(vertical = BuddySpacing.xxs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.BODY, titleWeight),
                color = if (enabled) titleColor else AgentBuddyTheme.onDisabled,
            )
            if (!subtitle.isNullOrEmpty()) {
                val subtitleStyle = buddyTextStyle(BuddyTextStyle.CAPTION)
                Text(
                    text = subtitle,
                    style = if (subtitleFontFamily != null) subtitleStyle.copy(fontFamily = subtitleFontFamily) else subtitleStyle,
                    color = subtitleColor,
                )
            }
            below?.invoke()
        }
        trailing?.invoke()
    }
}

/** Row that opens another screen: trailing chevron. */
@Composable
internal fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        trailing = { BuddyChevron() },
    )
}

/**
 * Row with a trailing switch. By default the whole row toggles (TalkBack reads
 * it as one switch); with [onClick] the row opens something else and only the
 * switch toggles.
 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    if (onClick != null) {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            onClick = onClick,
            onClickLabel = onClickLabel,
            enabled = enabled,
            modifier = modifier,
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    colors = settingsSwitchColors(),
                    modifier = Modifier.semantics { this.contentDescription = title },
                )
            },
        )
    } else {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            enabled = enabled,
            modifier =
                modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = settingsSwitchColors(),
                )
            },
        )
    }
}

/** Single-choice row: link-coloured check and selected semantics when chosen. */
@Composable
internal fun SettingsSelectRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleFontFamily: FontFamily? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailingWhenUnselected: (@Composable () -> Unit)? = null,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        subtitleFontFamily = subtitleFontFamily,
        icon = icon,
        titleWeight = if (selected) FontWeight.SemiBold else null,
        enabled = enabled,
        leading = leading,
        modifier =
            modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        trailing = {
            if (selected) SettingsCheckmark() else trailingWhenUnselected?.invoke()
        },
    )
}

/** Plain explanatory row (empty lists, notes) in Label type. */
@Composable
internal fun SettingsNoteRow(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = AgentBuddyTheme.textSecondary,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = BuddySize.control)
                .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.width(BuddySize.iconLarge), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(BuddySize.icon))
            }
        }
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}
