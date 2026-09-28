package com.akashark.agentbuddy.android.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Describes one swipe-revealed action slot. The caller supplies icon, label,
 * fill (`tint`, normally `AgentBuddyTheme.swipeFill(...)`), and an
 * `onTrigger` callback that fires once the gesture commits. The label and
 * icon are drawn in white on the fill, so pass a strong colour.
 */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onTrigger: () -> Unit,
    /** Solid slot fill that overrides [tint] (use `AgentBuddyTheme.swipeFill`). */
    val fill: Color? = null,
)

/**
 * Generalized swipe wrapper: leading swipe (drag right) and/or trailing
 * swipe (drag left) reveal action slots behind the row. Releasing past the
 * commit threshold fires the action with a haptic; otherwise the row
 * springs back.
 *
 * Both actions are also exposed to TalkBack as custom actions, since a
 * swipe is not discoverable without sight. With reduced motion the row
 * snaps back instead of springing.
 */
@Composable
fun SwipeableRow(
    leadingAction: SwipeAction? = null,
    trailingAction: SwipeAction? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reduceMotion = buddyReduceMotion

    // Commit threshold and max reveal mirror iOS: commit at ~35% of 260dp ≈ 90dp,
    // with extra reveal room up to 140dp.
    val commitDistancePx = with(density) { 90.dp.toPx() }
    val maxRevealPx = with(density) { 140.dp.toPx() }
    val activationDistancePx = with(density) { 8.dp.toPx() }

    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        // Leading (right-swipe) reveal — visible on the left edge.
        if (leadingAction != null) {
            val progress = (offsetX.value / commitDistancePx).coerceIn(0f, 1f)
            ActionSlot(
                action = leadingAction,
                alignment = Alignment.CenterStart,
                progress = progress,
                modifier = Modifier.matchParentSize(),
            )
        }

        // Trailing (left-swipe) reveal — visible on the right edge.
        if (trailingAction != null) {
            val progress = (-offsetX.value / commitDistancePx).coerceIn(0f, 1f)
            ActionSlot(
                action = trailingAction,
                alignment = Alignment.CenterEnd,
                progress = progress,
                modifier = Modifier.matchParentSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .semantics {
                    customActions = listOfNotNull(leadingAction, trailingAction).map { action ->
                        CustomAccessibilityAction(action.label) {
                            action.onTrigger()
                            true
                        }
                    }
                }
                .pointerInput(leadingAction, trailingAction, reduceMotion) {
                    var activated = false

                    detectHorizontalDragGestures(
                        onDragStart = { activated = false },
                        onDragEnd = {
                            val dx = offsetX.value
                            val trigger: SwipeAction? = when {
                                dx >= commitDistancePx && leadingAction != null -> leadingAction
                                dx <= -commitDistancePx && trailingAction != null -> trailingAction
                                else -> null
                            }
                            if (trigger != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                trigger.onTrigger()
                            }
                            scope.launch {
                                if (reduceMotion) {
                                    offsetX.snapTo(0f)
                                } else {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                if (reduceMotion) {
                                    offsetX.snapTo(0f)
                                } else {
                                    offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!activated && abs(dragAmount) < activationDistancePx) return@detectHorizontalDragGestures
                            activated = true

                            val proposed = offsetX.value + dragAmount
                            val clamped = when {
                                proposed > 0f && leadingAction == null -> 0f
                                proposed < 0f && trailingAction == null -> 0f
                                else -> proposed.coerceIn(-maxRevealPx, maxRevealPx)
                            }
                            scope.launch { offsetX.snapTo(clamped) }
                            if (clamped != proposed) return@detectHorizontalDragGestures
                            change.consume()
                        },
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
private fun BoxScope.ActionSlot(
    action: SwipeAction,
    alignment: Alignment,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    // Solid fill as soon as the row moves; the white label fades in with the
    // drag so the commit point stays legible in light and dark mode.
    Box(
        modifier = modifier
            .then(if (progress > 0f) Modifier.background(action.fill ?: action.tint) else Modifier)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(progress),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = action.label,
                color = Color.White,
                style = buddyTextStyle(BuddyTextStyle.LABEL),
            )
        }
    }
}
