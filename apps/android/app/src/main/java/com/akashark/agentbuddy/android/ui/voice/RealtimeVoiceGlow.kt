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

/** Status text for every phase, shown next to the dot so the phase never relies on colour. */
internal fun voicePhaseLabel(phase: AppVoiceSessionPhase): String =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING -> "正在连接"
        AppVoiceSessionPhase.LISTENING -> "正在聆听"
        AppVoiceSessionPhase.SPEAKING -> "搭子正在说话"
        AppVoiceSessionPhase.THINKING -> "思考中"
        AppVoiceSessionPhase.HANDOFF -> "正在执行工具"
        AppVoiceSessionPhase.ERROR -> "出错了"
    }

internal fun voicePhaseColor(phase: AppVoiceSessionPhase): Color =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING -> AgentBuddyTheme.textSecondary
        AppVoiceSessionPhase.LISTENING -> AgentBuddyTheme.success
        AppVoiceSessionPhase.SPEAKING -> AgentBuddyTheme.link
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

/** Sweep colours for the edge glow: the phase colour blended with the brand surface. */
private fun voicePhaseGlowColors(phase: AppVoiceSessionPhase): List<Color> {
    val tone = voicePhaseColor(phase)
    val brand = AgentBuddyTheme.brand
    return listOf(
        tone,
        brand.copy(alpha = 0.8f),
        tone.copy(alpha = 0.6f),
        brand,
        tone.copy(alpha = 0.85f),
        brand.copy(alpha = 0.6f),
        tone,
    )
}
