package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

@Composable
internal fun GoalTextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    helper: String? = null,
    singleLine: Boolean = true,
    keyboardNumeric: Boolean = false,
) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Text(
                text = confirmLabel,
                color = AgentBuddyTheme.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onConfirm(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        },
        dismissButton = {
            Text(
                text = "取消",
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        },
        title = {
            Text(title, color = AgentBuddyTheme.textPrimary, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        color = AgentBuddyTheme.textPrimary,
                        fontSize = AgentBuddyTextStyle.body.scaled,
                        fontFamily = AgentBuddyTheme.monoFont,
                    ),
                    cursorBrush = SolidColor(AgentBuddyTheme.accent),
                    keyboardOptions = if (keyboardNumeric) {
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        )
                    } else {
                        androidx.compose.foundation.text.KeyboardOptions.Default
                    },
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = AgentBuddyTheme.textMuted,
                                    fontSize = AgentBuddyTextStyle.body.scaled,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (helper != null) {
                    Text(
                        text = helper,
                        color = AgentBuddyTheme.textSecondary,
                        fontSize = AgentBuddyTextStyle.caption.scaled,
                    )
                }
            }
        },
        containerColor = AgentBuddyTheme.surface,
    )
}
