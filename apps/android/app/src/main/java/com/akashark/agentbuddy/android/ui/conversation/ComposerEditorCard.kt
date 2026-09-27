package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyContextChip
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

internal const val COMPOSER_PLACEHOLDER = "补充想法，或调整方向…"

/** Show the expand affordance once the draft is multi-line or long (iOS parity). */
internal fun composerShowsExpand(text: String): Boolean = text.contains('\n') || text.length > 60

/**
 * Mint composer card (radius 24, surface, 1dp border): body-16 editor on top,
 * then 「+」 and the partner chip on the left, dictation, stop and the single
 * primary action on the right. The button row is capped for large text; the
 * editor text keeps growing.
 */
@Composable
internal fun ComposerEditorCard(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    controls: ComposerControlsState,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onShowExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = COMPOSER_PLACEHOLDER,
    partnerLabel: String? = null,
    isFastMode: Boolean = false,
    onOpenModelPanel: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    voiceControl: @Composable () -> Unit = {},
    popups: @Composable () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val text = textFieldValue.text
    val showsExpand = composerShowsExpand(text) && !controls.isVoiceBusy
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xxs)
            .shadow(8.dp, BuddyShapes.composer, ambientColor = AgentBuddyTheme.floatingShadow, spotColor = AgentBuddyTheme.floatingShadow)
            .clip(BuddyShapes.composer)
            .background(AgentBuddyTheme.surface)
            .border(1.dp, if (focused) AgentBuddyTheme.borderControl else AgentBuddyTheme.border, BuddyShapes.composer)
            .padding(start = BuddySpacing.sm, end = BuddySpacing.xs, top = BuddySpacing.xxs, bottom = BuddySpacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BuddySize.composerMinHeight),
        ) {
            val textStyle = buddyTextStyle(BuddyTextStyle.BODY).copy(color = AgentBuddyTheme.textPrimary)
            if (text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = buddyTextStyle(BuddyTextStyle.BODY),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 2,
                    modifier = Modifier.padding(start = BuddySpacing.xxs, top = BuddySpacing.sm, end = BuddySpacing.xxl),
                )
            }
            BasicTextField(
                value = textFieldValue,
                onValueChange = onTextFieldValueChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(AgentBuddyTheme.focus),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BuddySize.composerMinHeight, max = 200.dp)
                    // Room for the expand button so wrapped lines never slide under it.
                    .padding(start = BuddySpacing.xxs, top = BuddySpacing.sm, bottom = BuddySpacing.xxs, end = 40.dp)
                    .semantics { contentDescription = "消息输入框" }
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
            if (showsExpand) {
                BuddyIconButton(
                    icon = Icons.Outlined.OpenInFull,
                    contentDescription = "展开输入框",
                    onClick = onShowExpanded,
                    modifier = Modifier.align(Alignment.TopEnd),
                    diameter = 28.dp,
                    iconSize = 14.dp,
                    tint = AgentBuddyTheme.textSecondary,
                )
            }
            popups()
        }
        BuddyChromeTypeLimit {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Leading group takes the free space so the partner chip only
                // shrinks (with an ellipsis) when the trailing controls need it.
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (controls.showsAttach) {
                        BuddyIconButton(
                            icon = Icons.Outlined.Add,
                            contentDescription = "附加",
                            onClick = onAttach,
                            tone = BuddyIconButtonTone.SOFT,
                            diameter = 36.dp,
                        )
                    }
                    if (partnerLabel != null && onOpenModelPanel != null) {
                        BuddyContextChip(
                            text = partnerLabel,
                            onClick = onOpenModelPanel,
                            icon = if (isFastMode) Icons.Outlined.Bolt else null,
                            trailingIcon = Icons.Outlined.KeyboardArrowDown,
                            onClickLabel = "选择搭档、模型与权限",
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics { contentDescription = "搭档：$partnerLabel" },
                        )
                    }
                }
                voiceControl()
                if (controls.showsStop) ComposerStopButton(controls = controls, onStop = onStop)
                if (controls.showsSend) ComposerSendButton(controls = controls, onSend = onSend)
            }
        }
    }
}
