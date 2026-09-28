package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Assistant code block on the page: code background, radius 16, CODE 14 in
 * Berkeley Mono. Only the code area scrolls sideways; the page never does.
 * Text stays selectable (long-press → copy).
 */
@Composable
internal fun CodeBlockSegment(
    language: String?,
    code: String,
    modifier: Modifier = Modifier,
    codeStyle: TextStyle = buddyTextStyle(BuddyTextStyle.CODE),
) {
    val shape = timelineCodeShape(nested = false)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(timelineCodeFill(nested = false))
            .border(1.dp, AgentBuddyTheme.border, shape),
    ) {
        language?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = AgentBuddyTheme.textSecondary,
                modifier = Modifier.padding(start = BuddySpacing.sm, end = BuddySpacing.sm, top = BuddySpacing.xs),
            )
        }
        if (isDiffLanguage(language)) {
            SyntaxHighlightedDiffBlock(
                diff = code,
                titleHint = language,
                fontSize = BuddyTextStyle.CODE.size.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BuddySpacing.sm),
            )
        } else {
            SelectableConversationText(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = code,
                    style = codeStyle,
                    color = AgentBuddyTheme.textPrimary,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(BuddySpacing.sm),
                )
            }
        }
    }
}
