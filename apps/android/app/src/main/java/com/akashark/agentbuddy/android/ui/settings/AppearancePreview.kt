package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalTextScale
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Live conversation sample for the Appearance screen, drawn at [textScale]
 * over the chat wallpaper: Mint user bubbles (19/19/5/19), a command row and
 * assistant text with a code block.
 */
@Composable
internal fun AppearanceConversationPreview(
    textScale: Float,
    modifier: Modifier = Modifier,
    wallpaper: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BuddyShapes.card)
                .background(AgentBuddyTheme.background)
                .border(1.dp, AgentBuddyTheme.border, BuddyShapes.card)
                .clearAndSetSemantics { contentDescription = "对话预览" },
    ) {
        wallpaper()
        CompositionLocalProvider(LocalTextScale provides textScale) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(BuddySpacing.md),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
            ) {
                PreviewUserBubble("嘿，生产环境怎么炸了")
                PreviewCommandRow()
                PreviewAssistantText("找到问题了。有人部署了这个：")
                PreviewCodeBlock()
                PreviewAssistantText("我不是生气，只是有点失望。")
                PreviewUserBubble("那就是你")
            }
        }
    }
}

@Composable
private fun PreviewUserBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(start = BuddySpacing.xxxl), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textPrimary,
            modifier =
                Modifier
                    .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.userBubble)
                    .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        )
    }
}

@Composable
private fun PreviewAssistantText(text: String) {
    Text(
        text = text,
        style = buddyTextStyle(BuddyTextStyle.BODY),
        color = AgentBuddyTheme.textBody,
    )
}

@Composable
private fun PreviewCommandRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surface, BuddyShapes.detailCard)
                .border(1.dp, AgentBuddyTheme.border, BuddyShapes.detailCard)
                .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = AgentBuddyTheme.success,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "rg 'TODO: fix later' --count",
            style = buddyTextStyle(BuddyTextStyle.CODE),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "0.3s",
            style = buddyTextStyle(BuddyTextStyle.CAPTION),
            color = AgentBuddyTheme.textSecondary,
        )
    }
}

@Composable
private fun PreviewCodeBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
        Text(
            text = "python",
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = AgentBuddyTheme.textSecondary,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = "if is_friday():\n    yolo_deploy(skip_tests=True)",
            style = buddyTextStyle(BuddyTextStyle.CODE),
            color = AgentBuddyTheme.textBody,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AgentBuddyTheme.codeBackground, BuddyShapes.control)
                    .border(1.dp, AgentBuddyTheme.border, BuddyShapes.control)
                    .padding(BuddySpacing.sm),
        )
    }
}
