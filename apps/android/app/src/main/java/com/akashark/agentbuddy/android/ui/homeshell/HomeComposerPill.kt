package com.akashark.agentbuddy.android.ui.homeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * 「有个想法？交给搭子…」 entry above the navigation bar. Tapping the text
 * opens the new-task sheet; the trailing button starts a realtime voice
 * session when voice is enabled ([onVoice] non-null), otherwise it also opens
 * the sheet.
 */
@Composable
fun HomeComposerPill(
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
    onVoice: (() -> Unit)? = null,
    isStartingVoice: Boolean = false,
    isVoiceActive: Boolean = false,
) {
    BuddyChromeTypeLimit {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .shadow(12.dp, BuddyShapes.composer, ambientColor = AgentBuddyTheme.floatingShadow, spotColor = AgentBuddyTheme.floatingShadow)
                    .clip(BuddyShapes.composer)
                    .background(AgentBuddyTheme.surface, BuddyShapes.composer)
                    .border(1.dp, AgentBuddyTheme.border, BuddyShapes.composer)
                    .padding(vertical = BuddySpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = BuddySize.minHitTarget)
                        .clickable(role = Role.Button, onClickLabel = "开始新任务", onClick = onCompose)
                        .semantics { contentDescription = "开始新任务" }
                        .padding(start = BuddySpacing.lg),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "有个想法？交给搭子…",
                    style = buddyTextStyle(BuddyTextStyle.BODY),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.padding(end = BuddySpacing.xxs)) {
                if (onVoice != null) {
                    VoiceButton(onVoice = onVoice, isStarting = isStartingVoice, isActive = isVoiceActive)
                } else {
                    BuddyIconButton(
                        icon = Icons.Outlined.ArrowUpward,
                        contentDescription = "开始新任务",
                        onClick = onCompose,
                        tone = BuddyIconButtonTone.ACTION,
                        diameter = 40.dp,
                        iconSize = 20.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceButton(
    onVoice: () -> Unit,
    isStarting: Boolean,
    isActive: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(BuddySize.minHitTarget)
                .clip(CircleShape)
                .clickable(enabled = !isStarting, role = Role.Button, onClick = onVoice)
                .semantics {
                    contentDescription = "开始语音对话"
                    if (isStarting) stateDescription = "正在准备" else if (isActive) stateDescription = "语音对话进行中"
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(AgentBuddyTheme.action, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isStarting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = AgentBuddyTheme.onAction,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = AgentBuddyTheme.onAction,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
