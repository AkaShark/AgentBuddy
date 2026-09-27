package com.akashark.agentbuddy.android.ui.terminal

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.settings.SettingsGroup
import com.akashark.agentbuddy.android.ui.settings.SettingsRowDivider
import com.akashark.agentbuddy.android.ui.settings.SettingsSectionHeader
import com.akashark.agentbuddy.android.ui.settings.SettingsSelectRow
import com.akashark.agentbuddy.android.ui.settings.SettingsSwitchRow

/** Font size, theme and cursor blink for the terminal, saved to [TerminalConfigPrefs]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalConfigSheet(
    context: Context,
    onDismiss: () -> Unit,
) {
    BuddyBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        TerminalConfigContent(
            fontSize = TerminalConfigPrefs.fontSize,
            onFontSizeCommit = { TerminalConfigPrefs.setFontSize(context, it) },
            theme = TerminalConfigPrefs.theme,
            onThemeChange = { TerminalConfigPrefs.setTheme(context, it) },
            cursorBlink = TerminalConfigPrefs.cursorBlink,
            onCursorBlinkChange = { TerminalConfigPrefs.setCursorBlink(context, it) },
        )
    }
}

/**
 * Settings-style groups: 字号 (slider, applied when released), 主题 (single
 * choice) and 光标闪烁.
 */
@Composable
internal fun TerminalConfigContent(
    fontSize: Float,
    onFontSizeCommit: (Float) -> Unit,
    theme: TerminalThemeChoice,
    onThemeChange: (TerminalThemeChoice) -> Unit,
    cursorBlink: Boolean,
    onCursorBlinkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftFontSize by remember(fontSize) { mutableFloatStateOf(fontSize) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = BuddySpacing.xl, end = BuddySpacing.xl, bottom = BuddySpacing.xl),
    ) {
        Text(
            text = "终端",
            style = buddyTextStyle(BuddyTextStyle.TITLE),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.semantics { heading() },
        )

        SettingsSectionHeader("字号")
        SettingsGroup {
            Column(Modifier.padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "终端字号",
                        style = buddyTextStyle(BuddyTextStyle.BODY),
                        color = AgentBuddyTheme.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${draftFontSize.toInt()} pt",
                        style = buddyTextStyle(BuddyTextStyle.CODE),
                        color = AgentBuddyTheme.textSecondary,
                    )
                }
                Slider(
                    value = draftFontSize,
                    onValueChange = { draftFontSize = it },
                    onValueChangeFinished = { onFontSizeCommit(draftFontSize) },
                    valueRange = 10f..24f,
                    steps = 13,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = AgentBuddyTheme.action,
                            activeTrackColor = AgentBuddyTheme.action,
                            activeTickColor = AgentBuddyTheme.onAction,
                            inactiveTrackColor = AgentBuddyTheme.surfaceSoft,
                            inactiveTickColor = AgentBuddyTheme.borderControl,
                        ),
                    modifier = Modifier.semantics { contentDescription = "终端字号" },
                )
            }
        }

        SettingsSectionHeader("主题")
        SettingsGroup {
            TerminalThemeChoice.entries.forEachIndexed { index, choice ->
                SettingsSelectRow(
                    title = choice.title,
                    selected = theme == choice,
                    onClick = { onThemeChange(choice) },
                )
                if (index < TerminalThemeChoice.entries.lastIndex) SettingsRowDivider(indentForIcon = false)
            }
        }

        SettingsSectionHeader("光标")
        SettingsGroup {
            SettingsSwitchRow(title = "光标闪烁", checked = cursorBlink, onCheckedChange = onCursorBlinkChange)
        }
    }
}
