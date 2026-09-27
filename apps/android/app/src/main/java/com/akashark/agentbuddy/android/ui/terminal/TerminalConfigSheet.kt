package com.akashark.agentbuddy.android.ui.terminal

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalConfigSheet(
    context: Context,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var draftFontSize by remember(TerminalConfigPrefs.fontSize) {
        mutableStateOf(TerminalConfigPrefs.fontSize)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
        contentColor = AgentBuddyTheme.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "终端",
                color = AgentBuddyTheme.textPrimary,
                fontFamily = AgentBuddyTheme.monoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "字号",
                        color = AgentBuddyTheme.textSecondary,
                        fontFamily = AgentBuddyTheme.monoFont,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${draftFontSize.toInt()} pt",
                        color = AgentBuddyTheme.textMuted,
                        fontFamily = AgentBuddyTheme.monoFont,
                        fontSize = 13.sp,
                    )
                }
                Slider(
                    value = draftFontSize,
                    onValueChange = { draftFontSize = it },
                    onValueChangeFinished = {
                        TerminalConfigPrefs.setFontSize(context, draftFontSize)
                    },
                    valueRange = 10f..24f,
                    steps = 13,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "主题",
                    color = AgentBuddyTheme.textSecondary,
                    fontFamily = AgentBuddyTheme.monoFont,
                    fontSize = 13.sp,
                )
                TerminalThemeChoice.entries.forEach { choice ->
                    val selected = TerminalConfigPrefs.theme == choice
                    TextButton(
                        onClick = { TerminalConfigPrefs.setTheme(context, choice) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = choice.title,
                            color = if (selected) AgentBuddyTheme.accent else AgentBuddyTheme.textPrimary,
                            fontFamily = AgentBuddyTheme.monoFont,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(
                                text = "•",
                                color = AgentBuddyTheme.accent,
                                fontFamily = AgentBuddyTheme.monoFont,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "光标闪烁",
                    color = AgentBuddyTheme.textPrimary,
                    fontFamily = AgentBuddyTheme.monoFont,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = TerminalConfigPrefs.cursorBlink,
                    onCheckedChange = { TerminalConfigPrefs.setCursorBlink(context, it) },
                )
            }
        }
    }
}
