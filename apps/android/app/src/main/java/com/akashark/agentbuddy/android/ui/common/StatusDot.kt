package com.akashark.agentbuddy.android.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion

/**
 * Shared visual language for "this thing's current state" — used for task
 * rows (active / hydrating / hydrated / idle) and server pills (connected /
 * connecting / failed / idle). Colours come from the semantic success /
 * warning / danger roles. The dot only supports text next to it: callers
 * pair it with a label so the state never depends on colour alone.
 *
 * With reduced motion the pulses stop: ACTIVE shows a ring around a solid
 * dot and PENDING a solid dot, so they stay distinguishable without motion.
 */
enum class StatusDotState {
    /** Solid green. Something is done / healthy. */
    OK,
    /** Pulsing green. Something is live and running right now. */
    ACTIVE,
    /** Pulsing orange. Work in flight (connecting, reconnecting, loading). */
    PENDING,
    /** Solid red. Failed state that needs attention. */
    ERROR,
    /** Empty grey ring. Known-but-dormant state. */
    IDLE,
}

@Composable
fun StatusDot(
    state: StatusDotState,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val reduceMotion = buddyReduceMotion
    val success = AgentBuddyTheme.success
    val warning = AgentBuddyTheme.warning
    Box(
        modifier = modifier.size(size + 2.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        when (state) {
            StatusDotState.OK -> SolidDot(success, size)
            StatusDotState.ACTIVE ->
                if (reduceMotion) {
                    RingedDot(success, size)
                } else {
                    PulsingDot(success, size, withShimmer = true)
                }
            StatusDotState.PENDING ->
                if (reduceMotion) SolidDot(warning, size) else PulsingDot(warning, size, withShimmer = false)
            StatusDotState.ERROR -> SolidDot(AgentBuddyTheme.danger, size)
            StatusDotState.IDLE -> Box(
                modifier = Modifier
                    .size(size + 2.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, AgentBuddyTheme.textMuted.copy(alpha = 0.6f), CircleShape),
            )
        }
    }
}

@Composable
private fun SolidDot(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** Static stand-in for the ACTIVE pulse: a solid dot inside a thin ring. */
@Composable
private fun RingedDot(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size + 2.dp)
            .border(1.5.dp, color.copy(alpha = 0.55f), CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        SolidDot(color, size * 0.6f)
    }
}

@Composable
private fun PulsingDot(color: Color, size: Dp, withShimmer: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "status-dot-pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "status-dot-pulse-phase",
    )
    val alpha = 1.0f - (0.65f * phase)
    val scale = 1.0f - (0.15f * phase)

    val sweepPhase = if (withShimmer) {
        val sweepTransition = rememberInfiniteTransition(label = "status-dot-sweep")
        val sweep by sweepTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "status-dot-sweep-phase",
        )
        sweep
    } else {
        null
    }

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
            .then(
                if (sweepPhase != null) {
                    Modifier.drawWithContent {
                        drawContent()
                        val w = this.size.width
                        val h = this.size.height
                        // Sweep travels from fully-offscreen-left to fully-offscreen-right
                        // so the gradient band reads as a moving highlight across the dot.
                        val bandHalf = w * 0.3f
                        val center = -bandHalf + sweepPhase * (w + 2f * bandHalf)
                        val brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(alpha = 0f),
                                0.5f to Color.White.copy(alpha = 0.3f),
                                1f to Color.White.copy(alpha = 0f),
                            ),
                            start = Offset(center - bandHalf, 0f),
                            end = Offset(center + bandHalf, h),
                        )
                        drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                    }
                } else Modifier,
            ),
    )
}
