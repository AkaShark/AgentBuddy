package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

/** Assistant code block rendered from a Rust render block. */
@Composable
internal fun CodeBlockSegment(
    language: String?,
    code: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        language?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it.uppercase(),
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            if (isDiffLanguage(language)) {
                SyntaxHighlightedDiffBlock(
                    diff = code,
                    titleHint = language,
                    fontSize = AgentBuddyTextStyle.caption.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SelectableConversationText {
                    Text(
                        text = code,
                        color = AgentBuddyTheme.textBody,
                        fontFamily = AgentBuddyTheme.monoFont,
                        fontSize = AgentBuddyTextStyle.body.scaled,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
