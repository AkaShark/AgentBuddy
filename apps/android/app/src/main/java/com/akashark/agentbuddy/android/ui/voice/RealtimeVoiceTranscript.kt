package com.akashark.agentbuddy.android.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppVoiceSessionPhase
import uniffi.codex_mobile_client.AppVoiceSpeaker
import uniffi.codex_mobile_client.AppVoiceTranscriptEntry

/**
 * Transcript area: a waveform (and a hint) until someone speaks, then the
 * lines styled like the conversation — the user in soft bubbles on the
 * trailing side, 搭子 as plain body text.
 */
@Composable
internal fun TranscriptContent(
    entries: List<AppVoiceTranscriptEntry>,
    phase: AppVoiceSessionPhase,
    inputLevel: Float,
    outputLevel: Float,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (entries.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
            ) {
                AudioWaveformView(
                    level = if (phase == AppVoiceSessionPhase.LISTENING) inputLevel else outputLevel,
                    tint = voicePhaseColor(phase),
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(40.dp)
                            .alpha(if (phase == AppVoiceSessionPhase.CONNECTING) 0.4f else 1f),
                )
                voiceHint(phase)?.let { hint ->
                    Text(
                        text = hint,
                        style = buddyTextStyle(BuddyTextStyle.BODY),
                        color = AgentBuddyTheme.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm, Alignment.Bottom),
                contentPadding = PaddingValues(vertical = BuddySpacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = entries, key = { entry -> entry.itemId }) { entry ->
                    TranscriptLine(entry)
                }
            }
        }
    }
}

private fun voiceHint(phase: AppVoiceSessionPhase): String? =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING -> "正在接通实时语音…"
        AppVoiceSessionPhase.LISTENING -> "直接说话，搭子在听。"
        else -> null
    }

@Composable
private fun TranscriptLine(entry: AppVoiceTranscriptEntry) {
    if (entry.speaker == AppVoiceSpeaker.USER) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(start = BuddySpacing.xxxl),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = entry.text,
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textPrimary,
                modifier =
                    Modifier
                        .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.userBubble)
                        .padding(horizontal = BuddySpacing.md, vertical = 10.dp)
                        .semantics { contentDescription = "你说：${entry.text}" },
            )
        }
    } else {
        Text(
            text = entry.text,
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textBody,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "搭子说：${entry.text}" },
        )
    }
}

/** 8dp phase dot; it pulses only while active and motion is allowed. */
@Composable
internal fun VoiceScreenPulsingDot(
    color: Color,
    isActive: Boolean,
) {
    if (isActive && !buddyReduceMotion) {
        val transition = rememberInfiniteTransition(label = "voice-dot")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "voice-dot-scale",
        )
        Box(Modifier.size(8.dp).scale(scale).background(color, CircleShape))
    } else {
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}

/** Five bars that follow the audio level; the idle pulse stops with reduced motion. */
@Composable
private fun AudioWaveformView(
    level: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val pulse =
        if (buddyReduceMotion) {
            1f
        } else {
            val transition = rememberInfiniteTransition(label = "voice-wave")
            val animated by transition.animateFloat(
                initialValue = 0.78f,
                targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "voice-wave-pulse",
            )
            animated
        }
    val normalizedLevel = (0.3f + (level * 0.9f)).coerceIn(0.2f, 1f)
    val multipliers = listOf(0.36f, 0.62f, 1f, 0.62f, 0.36f)

    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val spacing = size.width / (multipliers.size * 2f + 1f)
        multipliers.forEachIndexed { index, multiplier ->
            val barHeight = size.height * (0.28f + multiplier * normalizedLevel * pulse * 0.6f)
            val left = spacing + index * spacing * 2f
            val top = (size.height - barHeight) / 2f
            drawLine(
                color = tint,
                start = Offset(left, top),
                end = Offset(left, top + barHeight),
                strokeWidth = spacing,
                cap = StrokeCap.Round,
            )
        }
    }
}
