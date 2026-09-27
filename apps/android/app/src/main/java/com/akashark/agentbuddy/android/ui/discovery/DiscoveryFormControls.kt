package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalTextScale
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BUDDY_CHROME_MAX_FONT_SCALE
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

// Mint form pieces shared by the add-host sheets (manual entry, SSH login,
// SSH partner picker, QR pairing) and the task/folder pickers. They only
// style; every value and action stays with the screen that owns it.

/** Caption title, content, optional caption footer. */
@Composable
internal fun DiscoveryFormSection(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = AgentBuddyTheme.textSecondary,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
        if (footer != null) {
            Text(
                text = footer,
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
            )
        }
    }
}

/**
 * Mint text input: surface fill, borderControl outline (focus ring while
 * editing, danger when [isError]), radius 16, at least 48dp tall. Pasted keys,
 * JSON and paths use the code face via [monospaced]. TalkBack reads the
 * placeholder while empty; pass [label] when the field needs a different name.
 */
@Composable
internal fun MintTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    monospaced: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = buddyTextStyle(if (monospaced) BuddyTextStyle.CODE else BuddyTextStyle.BODY)
    val outline = when {
        isError -> AgentBuddyTheme.danger
        focused -> AgentBuddyTheme.focus
        else -> AgentBuddyTheme.borderControl
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (label != placeholder) Modifier.semantics { contentDescription = label } else Modifier),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle.copy(
            color = if (enabled) AgentBuddyTheme.textPrimary else AgentBuddyTheme.onDisabled,
        ),
        cursorBrush = SolidColor(AgentBuddyTheme.focus),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BuddySize.control)
                    .background(
                        if (enabled) AgentBuddyTheme.surface else AgentBuddyTheme.disabled,
                        BuddyShapes.button,
                    )
                    .border(if (focused || isError) 2.dp else 1.dp, outline, BuddyShapes.button)
                    .padding(
                        start = if (leading != null) BuddySpacing.sm else BuddySpacing.md,
                        end = if (trailing != null) 0.dp else BuddySpacing.md,
                    ),
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                leading?.invoke()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = BuddySpacing.sm - 1.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = AgentBuddyTheme.textMuted,
                            maxLines = if (singleLine) 1 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * Two or three mutually exclusive options on a surfaceSoft track. The selected
 * option gets a surface fill, outline and semibold label, and is announced as
 * selected, so it never depends on colour alone.
 */
@Composable
internal fun <T> DiscoverySegmentedPicker(
    options: List<Pair<T, String>>,
    selection: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.button)
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        options.forEach { (value, title) ->
            val selected = value == selection
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BuddySize.minHitTarget - 4.dp)
                    .then(
                        if (selected) {
                            Modifier
                                .background(AgentBuddyTheme.surface, BuddyShapes.control)
                                .border(1.dp, AgentBuddyTheme.borderControl, BuddyShapes.control)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(value) },
                    )
                    .padding(horizontal = BuddySpacing.xs, vertical = BuddySpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = buddyTextStyle(
                        BuddyTextStyle.LABEL,
                        if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = when {
                        !enabled -> AgentBuddyTheme.onDisabled
                        selected -> AgentBuddyTheme.textPrimary
                        else -> AgentBuddyTheme.textSecondary
                    },
                    maxLines = 2,
                )
            }
        }
    }
}

/** Switch row on a surface detail card; the whole row toggles. */
@Composable
internal fun DiscoveryToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.detailCard, padding = null)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = BuddySize.control)
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = if (enabled) AgentBuddyTheme.textPrimary else AgentBuddyTheme.onDisabled,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = buddyTextStyle(BuddyTextStyle.CAPTION),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AgentBuddyTheme.action,
                checkedThumbColor = AgentBuddyTheme.onAction,
            ),
        )
    }
}

/**
 * Two buttons side by side that stack vertically once the text is enlarged
 * past the chrome limit, so neither label is clipped.
 */
@Composable
internal fun MintButtonPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stacked = LocalDensity.current.fontScale * LocalTextScale.current > BUDDY_CHROME_MAX_FONT_SCALE
    if (stacked) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}

/** Tile + name + address line at the top of a connect sheet. */
@Composable
internal fun DiscoveryHostSummary(
    icon: ImageVector,
    name: String,
    address: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuddyIconTile(BuddyTileContent.Symbol(icon))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = address,
                style = buddyTextStyle(BuddyTextStyle.CODE),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
