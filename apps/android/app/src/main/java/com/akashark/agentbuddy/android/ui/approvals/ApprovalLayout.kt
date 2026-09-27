package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing

/**
 * Two buttons side by side (secondary start, primary end) when both labels fit
 * in half the width on one line; otherwise stacked full width with the primary
 * first, so large text never squeezes a label into a letter-by-letter wrap.
 */
@Composable
internal fun AdaptiveButtonPair(
    secondary: @Composable (Modifier) -> Unit,
    primary: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    horizontalGap: Dp = BuddySpacing.sm,
    verticalGap: Dp = BuddySpacing.xs,
) {
    Layout(
        content = {
            secondary(Modifier)
            primary(Modifier)
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val secondaryButton = measurables[0]
        val primaryButton = measurables[1]
        val width = constraints.maxWidth
        val hGap = horizontalGap.roundToPx()
        val half = (width - hGap) / 2
        val fits = secondaryButton.maxIntrinsicWidth(Constraints.Infinity) <= half &&
            primaryButton.maxIntrinsicWidth(Constraints.Infinity) <= half
        if (fits) {
            val start = secondaryButton.measure(Constraints.fixedWidth(half))
            val end = primaryButton.measure(Constraints.fixedWidth(width - hGap - half))
            val height = maxOf(start.height, end.height)
            layout(width, height) {
                start.placeRelative(0, (height - start.height) / 2)
                end.placeRelative(half + hGap, (height - end.height) / 2)
            }
        } else {
            val top = primaryButton.measure(Constraints.fixedWidth(width))
            val bottom = secondaryButton.measure(Constraints.fixedWidth(width))
            val vGap = verticalGap.roundToPx()
            layout(width, top.height + vGap + bottom.height) {
                top.placeRelative(0, 0)
                bottom.placeRelative(0, top.height + vGap)
            }
        }
    }
}

/** `~`-shortened path for display when no host-aware formatter is available. */
fun abbreviateHomePath(path: String): String =
    path.trim()
        .replace(Regex("^/home/[^/]+"), "~")
        .replace(Regex("^/Users/[^/]+"), "~")
