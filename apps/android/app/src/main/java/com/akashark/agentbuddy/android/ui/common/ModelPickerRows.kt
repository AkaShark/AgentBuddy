package com.akashark.agentbuddy.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.settings.SettingsRow
import com.akashark.agentbuddy.android.ui.settings.settingsSwitchColors
import uniffi.codex_mobile_client.ModelInfo

// Building blocks of the model panel (iOS ModelPickerComponents parity):
// caption section labels, selectable chips, the search field, model rows and
// the mode / access switch rows.

/** Caption label that separates the partner, model, effort and mode groups. */
@Composable
internal fun ModelPickerSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
        color = AgentBuddyTheme.textSecondary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = BuddySpacing.md, end = BuddySpacing.md, top = BuddySpacing.md, bottom = BuddySpacing.xxs)
                .semantics { heading() },
    )
}

/**
 * Selectable capsule (partner filter, reasoning effort). Selection shows as
 * the action fill, a checkmark and a heavier weight, never colour alone. The
 * 34dp pill sits in a 48dp hit area.
 */
@Composable
internal fun ModelPickerChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val content = if (selected) AgentBuddyTheme.onAction else AgentBuddyTheme.textPrimary
    Box(
        modifier =
            modifier
                .heightIn(min = BuddySize.minHitTarget)
                .clip(CircleShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = BuddySize.compactPill)
                    .background(if (selected) AgentBuddyTheme.action else AgentBuddyTheme.surfaceSoft, CircleShape)
                    .padding(horizontal = BuddySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            }
            leading?.invoke()
            Text(
                text = text,
                style = buddyTextStyle(BuddyTextStyle.LABEL, if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count != null) {
                Text(
                    text = "$count",
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = content.copy(alpha = 0.78f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Mint search field: surface fill, control outline, 48dp tall, Body text. */
@Composable
internal fun ModelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = buddyTextStyle(BuddyTextStyle.BODY),
        placeholder = { Text("搜索模型", style = buddyTextStyle(BuddyTextStyle.BODY)) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(BuddySize.icon))
        },
        trailingIcon =
            if (query.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Cancel, contentDescription = "清除搜索", modifier = Modifier.size(BuddySize.icon))
                    }
                }
            },
        shape = BuddyShapes.control,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = AgentBuddyTheme.textPrimary,
                unfocusedTextColor = AgentBuddyTheme.textPrimary,
                focusedContainerColor = AgentBuddyTheme.surface,
                unfocusedContainerColor = AgentBuddyTheme.surface,
                cursorColor = AgentBuddyTheme.focus,
                focusedBorderColor = AgentBuddyTheme.focus,
                unfocusedBorderColor = AgentBuddyTheme.borderControl,
                focusedPlaceholderColor = AgentBuddyTheme.textSecondary,
                unfocusedPlaceholderColor = AgentBuddyTheme.textSecondary,
                focusedLeadingIconColor = AgentBuddyTheme.textSecondary,
                unfocusedLeadingIconColor = AgentBuddyTheme.textSecondary,
                focusedTrailingIconColor = AgentBuddyTheme.textSecondary,
                unfocusedTrailingIconColor = AgentBuddyTheme.textSecondary,
            ),
        modifier = modifier.fillMaxWidth().heightIn(min = BuddySize.control),
    )
}

/**
 * One model: partner icon, name (semibold when chosen), "默认" tag, partner
 * and description caption, and a link-coloured checkmark on the selection.
 * TalkBack reads it as a radio button.
 */
@Composable
internal fun ModelOptionRow(
    model: ModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = model.modelPickerDisplayName()
    val detail =
        model.description
            .takeIf { it.isNotBlank() }
            ?: model.model.takeIf { it.isNotBlank() && it != title && it != model.id }
    val runtimeLabel =
        model.agentRuntimeKind.runtimeLabel
            .takeUnless { model.agentRuntimeKind == "amp" }
    val subtitle = listOfNotNull(runtimeLabel, detail).joinToString(" · ").ifEmpty { null }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        ModelRuntimeIcon(model.agentRuntimeKind)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            ) {
                Text(
                    text = title,
                    style = buddyTextStyle(BuddyTextStyle.BODY, if (selected) FontWeight.SemiBold else null),
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (model.isDefault) {
                    Text(
                        text = "默认",
                        style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                        color = AgentBuddyTheme.textSecondary,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .background(AgentBuddyTheme.surfaceSoft, CircleShape)
                                .padding(horizontal = BuddySpacing.xs),
                    )
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = buddyTextStyle(BuddyTextStyle.CAPTION),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = AgentBuddyTheme.link,
                modifier = Modifier.size(BuddySize.icon),
            )
        } else {
            Spacer(Modifier.size(BuddySize.icon))
        }
    }
}

@Composable
internal fun ModelRuntimeIcon(kind: AgentRuntimeKind) {
    AgentIconView(kind = kind, sizeDp = 20, modifier = Modifier.clip(BuddyShapes.control))
}

/**
 * Mode / access row with a trailing switch; the whole row toggles and reads
 * as one switch. [highlighted] puts the row on the warning surface (full
 * access is on).
 */
@Composable
internal fun ModelPanelSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = AgentBuddyTheme.textSecondary,
    highlighted: Boolean = false,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        modifier =
            Modifier
                .then(if (highlighted) Modifier.background(AgentBuddyTheme.warningSurface) else Modifier)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = {
            Switch(checked = checked, onCheckedChange = null, colors = settingsSwitchColors())
        },
    )
}

/** Explains a control that cannot change right now (icon + Label text). */
@Composable
internal fun ModelPanelNote(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = BuddySize.control)
                .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(BuddySize.icon))
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Centred status line in the model list (loading, no match). */
@Composable
internal fun ModelPanelStatusText(text: String) {
    Text(
        text = text,
        style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
        color = AgentBuddyTheme.textSecondary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.lg),
    )
}
