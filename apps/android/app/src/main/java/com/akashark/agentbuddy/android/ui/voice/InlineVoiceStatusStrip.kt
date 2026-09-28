package com.akashark.agentbuddy.android.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppVoiceSessionPhase
import kotlin.math.abs
import kotlin.math.max

@Composable
fun InlineVoiceStatusStrip(
    phase: AppVoiceSessionPhase,
    inputLevel: Float,
    outputLevel: Float,
    onToggleSpeaker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isListening = phase == AppVoiceSessionPhase.LISTENING
    val isSpeaking = phase == AppVoiceSessionPhase.SPEAKING

    val scaledInputLevel = if (isListening) max(0.08f, inputLevel) else max(0f, inputLevel)
    val scaledOutputLevel = if (isSpeaking) max(0.08f, outputLevel) else max(0f, outputLevel)

    BuddyChromeTypeLimit {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surfaceSoft)
                .padding(start = BuddySpacing.md, end = BuddySpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            VoiceLevelIndicator(
                label = "你",
                active = isListening,
                level = scaledInputLevel,
                tint = AgentBuddyTheme.link,
            )
            VoiceLevelIndicator(
                label = "搭子",
                active = isSpeaking,
                level = scaledOutputLevel,
                tint = AgentBuddyTheme.warning,
            )
            Spacer(Modifier.weight(1f))
            // Phase in words, so the state never depends on colour alone.
            Text(
                text = phaseLabel(phase),
                color = phaseColor(phase),
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            )
            BuddyIconButton(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = "切换扬声器",
                onClick = onToggleSpeaker,
                diameter = 32.dp,
            )
        }
    }
}

@Composable
private fun VoiceLevelIndicator(
    label: String,
    active: Boolean,
    level: Float,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (active) tint else AgentBuddyTheme.onDisabled, CircleShape),
        )
        Text(
            text = label,
            color = if (active) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary,
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
        )
        AudioWaveform(
            level = level,
            tint = tint,
            modifier = Modifier.size(width = 40.dp, height = 14.dp),
        )
    }
}

@Composable
private fun AudioWaveform(
    level: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val barCount = 12
    Canvas(modifier = modifier) {
        val barWidth = 2f.dp.toPx()
        val totalBarWidth = barWidth * barCount
        val gap = if (barCount > 1) {
            (size.width - totalBarWidth) / (barCount - 1)
        } else {
            0f
        }
        val midY = size.height / 2f
        val center = (barCount - 1) / 2f

        for (index in 0 until barCount) {
            val distance = abs(index - center) / max(center, 1f)
            val base = 1f - distance * 0.5f
            val activeLevel = max(0.15f, level)
            val barHeight = max(0.1f, base * activeLevel) * size.height
            val x = index * (barWidth + gap)
            val cornerRadius = 1f.dp.toPx()

            val rect = Rect(
                left = x,
                top = midY - barHeight / 2f,
                right = x + barWidth,
                bottom = midY + barHeight / 2f,
            )
            drawPath(
                path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = rect,
                            radiusX = cornerRadius,
                            radiusY = cornerRadius,
                        )
                    )
                },
                color = tint,
                style = Fill,
            )
        }
    }
}

private fun phaseLabel(phase: AppVoiceSessionPhase): String =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING -> "连接中"
        AppVoiceSessionPhase.LISTENING -> "聆听中"
        AppVoiceSessionPhase.SPEAKING -> "说话中"
        AppVoiceSessionPhase.THINKING -> "思考中"
        AppVoiceSessionPhase.HANDOFF -> "交接中"
        AppVoiceSessionPhase.ERROR -> "错误"
    }

private fun phaseColor(phase: AppVoiceSessionPhase): Color =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING,
        AppVoiceSessionPhase.THINKING,
        AppVoiceSessionPhase.HANDOFF,
        -> AgentBuddyTheme.warning
        AppVoiceSessionPhase.LISTENING,
        AppVoiceSessionPhase.SPEAKING,
        -> AgentBuddyTheme.accent
        AppVoiceSessionPhase.ERROR -> AgentBuddyTheme.danger
    }
