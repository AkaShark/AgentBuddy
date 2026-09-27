package com.akashark.agentbuddy.android.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import uniffi.codex_mobile_client.AppVoiceSessionPhase

@Composable
internal fun VoiceEdgeGlow(
    intensity: Float,
    phase: AppVoiceSessionPhase,
) {
    val colors = voicePhaseGlowColors(phase)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        GlowStrokeLayer(colors = colors, intensity = intensity, strokeWidth = 4.dp, blurRadius = 0.dp, alpha = 1f)
        GlowStrokeLayer(colors = colors, intensity = intensity, strokeWidth = 6.dp, blurRadius = 4.dp, alpha = 0.95f)
        GlowStrokeLayer(colors = colors, intensity = intensity, strokeWidth = 8.dp, blurRadius = 12.dp, alpha = 0.82f)
        GlowStrokeLayer(colors = colors, intensity = intensity, strokeWidth = 12.dp, blurRadius = 20.dp, alpha = 0.7f)
    }
}

@Composable
private fun GlowStrokeLayer(
    colors: List<Color>,
    intensity: Float,
    strokeWidth: androidx.compose.ui.unit.Dp,
    blurRadius: androidx.compose.ui.unit.Dp,
    alpha: Float,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(blurRadius),
    ) {
        val strokePx = strokeWidth.toPx() + intensity * strokeWidth.toPx() * 0.7f
        val inset = strokePx / 2f
        val cornerRadius = size.minDimension * 0.115f

        drawRoundRect(
            brush = Brush.sweepGradient(colors),
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                width = size.width - strokePx,
                height = size.height - strokePx,
            ),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokePx),
            alpha = (intensity * alpha).coerceIn(0f, 1f),
        )
    }
}

internal fun voicePhaseLabel(phase: AppVoiceSessionPhase): String =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING -> "连接中"
        AppVoiceSessionPhase.LISTENING -> "聆听中"
        AppVoiceSessionPhase.SPEAKING -> "讲话中"
        AppVoiceSessionPhase.THINKING -> "思考中"
        AppVoiceSessionPhase.HANDOFF -> "交接中"
        AppVoiceSessionPhase.ERROR -> "错误"
    }

internal fun voicePhaseColor(phase: AppVoiceSessionPhase): Color =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING -> AgentBuddyTheme.accent
        AppVoiceSessionPhase.LISTENING -> AgentBuddyTheme.accentStrong
        AppVoiceSessionPhase.SPEAKING,
        AppVoiceSessionPhase.THINKING,
        AppVoiceSessionPhase.HANDOFF,
        -> AgentBuddyTheme.warning
        AppVoiceSessionPhase.ERROR -> AgentBuddyTheme.danger
    }

internal fun voiceGlowIntensity(
    phase: AppVoiceSessionPhase,
    inputLevel: Float,
    outputLevel: Float,
): Float =
    when (phase) {
        AppVoiceSessionPhase.LISTENING -> maxOf(0.3f, inputLevel)
        AppVoiceSessionPhase.SPEAKING -> maxOf(0.3f, outputLevel)
        AppVoiceSessionPhase.THINKING,
        AppVoiceSessionPhase.HANDOFF,
        -> 0.4f
        AppVoiceSessionPhase.CONNECTING -> 0.25f
        AppVoiceSessionPhase.ERROR -> 0.1f
    }

private fun voicePhaseGlowColors(phase: AppVoiceSessionPhase): List<Color> {
    val accent = AgentBuddyTheme.accent
    val accentStrong = AgentBuddyTheme.accentStrong
    val warning = AgentBuddyTheme.warning
    val success = AgentBuddyTheme.success
    val danger = AgentBuddyTheme.danger

    return when (phase) {
        AppVoiceSessionPhase.LISTENING -> listOf(
            accentStrong,
            accentStrong.copy(alpha = 0.7f),
            accent,
            success,
            accentStrong.copy(alpha = 0.5f),
            accent.copy(alpha = 0.8f),
        )
        AppVoiceSessionPhase.SPEAKING -> listOf(
            warning,
            warning.copy(alpha = 0.7f),
            warning.copy(alpha = 0.9f),
            warning.copy(alpha = 0.5f),
            warning.copy(alpha = 0.8f),
            warning.copy(alpha = 0.6f),
        )
        AppVoiceSessionPhase.THINKING,
        AppVoiceSessionPhase.HANDOFF,
        -> listOf(
            warning.copy(alpha = 0.6f),
            accent.copy(alpha = 0.4f),
            warning.copy(alpha = 0.4f),
            accentStrong.copy(alpha = 0.3f),
            warning.copy(alpha = 0.5f),
            accent.copy(alpha = 0.3f),
        )
        AppVoiceSessionPhase.CONNECTING -> listOf(
            accent.copy(alpha = 0.4f),
            accentStrong.copy(alpha = 0.3f),
            accent.copy(alpha = 0.2f),
            Color.Gray.copy(alpha = 0.2f),
            accent.copy(alpha = 0.3f),
            accentStrong.copy(alpha = 0.2f),
        )
        AppVoiceSessionPhase.ERROR -> listOf(
            danger,
            danger.copy(alpha = 0.6f),
            danger.copy(alpha = 0.5f),
            danger.copy(alpha = 0.4f),
            danger.copy(alpha = 0.3f),
            danger.copy(alpha = 0.5f),
        )
    }
}
