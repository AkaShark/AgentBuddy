package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Widest the settings lists grow on tablets and landscape. */
private val SettingsMaxContentWidth = 640.dp

/**
 * Inline title bar of a settings screen: optional back button, centred title,
 * optional "完成" or custom trailing action. Chrome, so its type is capped.
 */
@Composable
internal fun SettingsPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    BuddyChromeTypeLimit {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = BuddySpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                BuddyIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    iconSize = BuddySize.iconLarge,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(horizontal = 72.dp)
                        .semantics { heading() },
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (trailing != null) trailing()
                if (onDone != null) SettingsTextAction("完成", onClick = onDone)
            }
        }
    }
}

/** Link-coloured text action (Done, Save) with a 48dp hit area. */
@Composable
internal fun SettingsTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AgentBuddyTheme.link,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = BuddySize.minHitTarget)
                .widthIn(min = BuddySize.minHitTarget)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = BuddySpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
            color = if (enabled) color else AgentBuddyTheme.onDisabled,
        )
    }
}

/** Horizontal page gutter for settings lists (24, 16 on narrow phones). */
@Composable
internal fun settingsGutter() = BuddySpacing.pageGutter(LocalConfiguration.current.screenWidthDp.dp)

/**
 * Settings screen: [SettingsPageHeader] above a lazily scrolled list on the
 * page background, width-capped and centred on wide screens.
 */
@Composable
internal fun SettingsPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AgentBuddyTheme.background)
                .imePadding(),
    ) {
        SettingsPageHeader(title = title, onBack = onBack, onDone = onDone, trailing = headerTrailing)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = SettingsMaxContentWidth).fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        start = settingsGutter(),
                        end = settingsGutter(),
                        bottom = BuddySpacing.xxxl,
                    ),
                content = content,
            )
        }
    }
}

/** One settings section: caption header, surface group, optional caption footer. */
internal fun LazyListScope.settingsSection(
    title: String?,
    key: String? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        Column(Modifier.fillMaxWidth()) {
            if (title != null) {
                SettingsSectionHeader(title)
            } else {
                Spacer(Modifier.height(BuddySpacing.md))
            }
            SettingsGroup(content = content)
            footer?.invoke()
        }
    }
}

/**
 * Mint text input: Body text, 48dp minimum, radius 12, 1dp control outline
 * that turns into the focus colour while editing.
 */
@Composable
internal fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = buddyTextStyle(BuddyTextStyle.BODY)) } },
        singleLine = singleLine,
        enabled = enabled,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        textStyle = buddyTextStyle(BuddyTextStyle.BODY),
        shape = BuddyShapes.control,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = AgentBuddyTheme.textPrimary,
                unfocusedTextColor = AgentBuddyTheme.textPrimary,
                disabledTextColor = AgentBuddyTheme.onDisabled,
                focusedContainerColor = AgentBuddyTheme.surface,
                unfocusedContainerColor = AgentBuddyTheme.surface,
                disabledContainerColor = AgentBuddyTheme.disabled,
                cursorColor = AgentBuddyTheme.focus,
                focusedBorderColor = AgentBuddyTheme.focus,
                unfocusedBorderColor = AgentBuddyTheme.borderControl,
                disabledBorderColor = AgentBuddyTheme.border,
                focusedLabelColor = AgentBuddyTheme.focus,
                unfocusedLabelColor = AgentBuddyTheme.textSecondary,
                focusedPlaceholderColor = AgentBuddyTheme.textSecondary,
                unfocusedPlaceholderColor = AgentBuddyTheme.textSecondary,
            ),
        modifier = modifier.fillMaxWidth().heightIn(min = BuddySize.control),
    )
}

/**
 * Segmented single choice (appearance mode, connection mode): surfaceSoft
 * track, the chosen segment on surface. Each segment is a radio button for
 * TalkBack.
 */
@Composable
internal fun SettingsSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.control)
                .padding(BuddySpacing.xxs)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .then(
                            if (selected) {
                                Modifier.background(AgentBuddyTheme.surface, BuddyShapes.control)
                            } else {
                                Modifier
                            },
                        ).selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = BuddySpacing.xs, vertical = BuddySpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Themed alert: surface container, heading title, link (or danger) text actions. */
@Composable
internal fun SettingsAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    destructive: Boolean = false,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = buddyTextStyle(BuddyTextStyle.HEADING)) },
        text =
            text?.let {
                {
                    CompositionLocalProvider(LocalTextStyle provides buddyTextStyle(BuddyTextStyle.BODY)) { it() }
                }
            },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = BuddySize.minHitTarget)) {
                Text(
                    confirmText,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                    color = if (destructive) AgentBuddyTheme.danger else AgentBuddyTheme.link,
                )
            }
        },
        dismissButton =
            dismissText?.let {
                {
                    TextButton(onClick = onDismissRequest, modifier = Modifier.heightIn(min = BuddySize.minHitTarget)) {
                        Text(it, style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Medium), color = AgentBuddyTheme.link)
                    }
                }
            },
        shape = BuddyShapes.confirmCard,
        containerColor = AgentBuddyTheme.surface,
        titleContentColor = AgentBuddyTheme.textPrimary,
        textContentColor = AgentBuddyTheme.textSecondary,
        tonalElevation = 0.dp,
    )
}
