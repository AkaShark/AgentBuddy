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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import uniffi.codex_mobile_client.AppVoiceSessionPhase
import uniffi.codex_mobile_client.AppVoiceSpeaker
import uniffi.codex_mobile_client.AppVoiceTranscriptEntry

@Composable
internal fun TranscriptContent(
    entries: List<AppVoiceTranscriptEntry>,
    phase: AppVoiceSessionPhase,
    phaseColor: Color,
    inputLevel: Float,
    outputLevel: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceScreenPulsingDot(
                color = phaseColor,
                isActive = phase == AppVoiceSessionPhase.LISTENING || phase == AppVoiceSessionPhase.SPEAKING,
            )

            Text(
                text = voicePhaseLabel(phase),
                color = phaseColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = AgentBuddyTheme.monoFont,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (entries.isEmpty()) {
                AudioWaveformView(
                    level = if (phase == AppVoiceSessionPhase.LISTENING) inputLevel else outputLevel,
                    tint = phaseColor,
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(40.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, entry -> entry.itemId },
                    ) { index, entry ->
                        TranscriptLine(
                            entry = entry,
                            recencyIndex = entries.lastIndex - index,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptLine(
    entry: AppVoiceTranscriptEntry,
    recencyIndex: Int,
) {
    val isUser = entry.speaker == AppVoiceSpeaker.USER
    val opacity = when (recencyIndex) {
        0 -> 0.96f
        1 -> 0.72f
        2 -> 0.5f
        else -> 0.34f
    }

    Text(
        text = entry.text,
        color = Color.White.copy(alpha = opacity),
        fontSize = if (isUser) 17.sp else 22.sp,
        fontWeight = if (isUser) FontWeight.Normal else FontWeight.Medium,
        textAlign = TextAlign.Center,
        lineHeight = if (isUser) 24.sp else 30.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
    )
}

@Composable
private fun VoiceScreenPulsingDot(
    color: Color,
    isActive: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "voice-dot")
    val scale by transition.animateFloat(
        initialValue = if (isActive) 1f else 0.7f,
        targetValue = if (isActive) 1.4f else 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-dot-scale",
    )

    Box(
        modifier = Modifier
            .size((8.dp * scale).coerceAtLeast(6.dp))
            .background(color, CircleShape),
    )
}

@Composable
private fun AudioWaveformView(
    level: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "voice-wave")
    val pulse by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-wave-pulse",
    )
    val normalizedLevel = (0.3f + (level * 0.9f)).coerceIn(0.2f, 1f)
    val multipliers = listOf(0.36f, 0.62f, 1f, 0.62f, 0.36f)

    Canvas(modifier = modifier) {
        val spacing = size.width / (multipliers.size * 2f + 1f)
        val barWidth = spacing
        multipliers.forEachIndexed { index, multiplier ->
            val barHeight = size.height * (0.28f + multiplier * normalizedLevel * pulse * 0.6f)
            val left = spacing + index * spacing * 2f
            val top = (size.height - barHeight) / 2f
            drawLine(
                color = tint.copy(alpha = 0.8f),
                start = androidx.compose.ui.geometry.Offset(left, top),
                end = androidx.compose.ui.geometry.Offset(left, top + barHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
