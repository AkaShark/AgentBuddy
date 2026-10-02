package com.akashark.agentbuddy.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.R
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Continues the system splash with a stationary mark and then reveals the details.
 * Resource colors deliberately follow system light/dark mode, like the launch
 * window; the user's custom app theme takes over when startup finishes.
 */
@Composable
fun AnimatedSplashScreen(
    showDetails: Boolean = true,
    onMarkPositioned: ((Rect) -> Unit)? = null,
) {
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    val reduceMotion = buddyReduceMotion
    val detailsAlpha by animateFloatAsState(
        targetValue = if (showDetails) 1f else 0f,
        animationSpec = BuddyMotion.STATE.spec(reduceMotion),
        label = "Splash details",
    )
    val background = colorResource(R.color.launch_background)
    val primary = colorResource(R.color.launch_text_primary)
    val secondary = colorResource(R.color.launch_text_secondary)
    val muted = colorResource(R.color.launch_text_muted)

    LaunchedEffect(reduceMotion, showDetails) {
        elapsed = 0.0
        if (showDetails && !reduceMotion) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { elapsed = (it - start) / 1_000_000_000.0 }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(background),
        contentAlignment = Alignment.Center,
    ) {
        BuddyIconTile(
            content = BuddyTileContent.BrandMark,
            fill = colorResource(R.color.launch_brand),
            foreground = colorResource(R.color.launch_on_brand),
            size = BuddySize.splashMark,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                onMarkPositioned?.invoke(coordinates.boundsInWindow())
            },
        )
        SplashDetails(
            elapsed = elapsed,
            primary = primary,
            secondary = secondary,
            muted = muted,
            modifier = Modifier.fillMaxSize().graphicsLayer(alpha = detailsAlpha),
        )
    }
}

/** Measure the actual scaled text before deciding which details fit below the fixed mark. */
@Composable
private fun SplashDetails(
    elapsed: Double,
    primary: Color,
    secondary: Color,
    muted: Color,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val title = subcompose("title") {
            Text(
                text = "搭子",
                color = primary,
                style = buddyTextStyle(BuddyTextStyle.DISPLAY),
                maxLines = 1,
            )
        }.single().measure(contentConstraints)
        val footer = subcompose("footer") {
            BuddyChromeTypeLimit {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpinningProviderCarousel(elapsed = elapsed, selectedColor = secondary, mutedColor = muted)
                    Text(
                        text = " 在你的手机上",
                        color = muted,
                        style = buddyTextStyle(BuddyTextStyle.CODE),
                        maxLines = 1,
                    )
                }
            }
        }.single().measure(contentConstraints)

        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val markBottom = (height + BuddySize.splashMark.roundToPx()) / 2
        val minimumGap = BuddySpacing.md.roundToPx()
        val normalBottomPadding = BuddySpacing.huge.roundToPx()
        val bottomPadding = if (height - normalBottomPadding - footer.height >= markBottom + minimumGap) {
            normalBottomPadding
        } else {
            BuddySpacing.md.roundToPx()
        }
        val footerTop = height - bottomPadding - footer.height
        val footerFits = footerTop >= markBottom + minimumGap
        val titleTop = markBottom + BuddySpacing.xl.roundToPx()
        val titleFits = footerFits && titleTop + title.height + BuddySpacing.xl.roundToPx() <= footerTop

        layout(width, height) {
            // The carousel takes priority in short landscape/split-screen windows.
            // If even it cannot clear the mark, the centered mark remains alone.
            if (titleFits) title.placeRelative((width - title.width) / 2, titleTop)
            if (footerFits) footer.placeRelative((width - footer.width) / 2, footerTop)
        }
    }
}

// Keep the order identical to the iOS splash.
private val SplashProviders = listOf(
    "codex", "pi", "amp", "opencode", "claude", "droid", "hermes", "devin", "grok",
)

@Composable
private fun SpinningProviderCarousel(
    elapsed: Double,
    selectedColor: Color,
    mutedColor: Color,
) {
    val textStyle = buddyTextStyle(BuddyTextStyle.CODE)
    val density = LocalDensity.current
    val rowHeight = with(density) { textStyle.fontSize.toDp() } * (BuddySize.splashProviderRow.value / BuddyTextStyle.CODE.size)
    val itemHeight = rowHeight.value
    val phase = providerCarouselPhase(elapsed)
    val cycleHeight = itemHeight * SplashProviders.size

    Box(
        modifier = Modifier
            .width(BuddySize.splashProviderWidth * density.fontScale)
            .height(rowHeight * 3)
            .clipToBounds()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.2f to Color.Black,
                        0.8f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .clearAndSetSemantics { contentDescription = "AI 代理" },
        contentAlignment = Alignment.CenterEnd,
    ) {
        SplashProviders.forEachIndexed { index, provider ->
            val raw = (index - phase).toFloat() * itemHeight
            val y = wrapCarouselOffset(raw, cycleHeight)
            val distance = min(abs(y) / itemHeight, 1f)
            val selected = distance < 0.35f
            val alpha = if (selected) 1f else max(0.18f, 1f - distance * 0.7f)

            Text(
                text = provider,
                color = if (selected) selectedColor else mutedColor,
                style = textStyle,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = y.dp)
                    .graphicsLayer(alpha = alpha),
            )
        }
    }
}

private fun providerCarouselPhase(elapsed: Double): Double {
    val perWord = 0.9
    val transitionFraction = 0.45
    val totalCycle = perWord * SplashProviders.size
    val cycleTime = elapsed % totalCycle
    val wordIndex = floor(cycleTime / perWord)
    val withinWord = cycleTime - wordIndex * perWord
    val dwell = perWord * (1.0 - transitionFraction)
    if (withinWord < dwell) return wordIndex
    val progress = (withinWord - dwell) / (perWord * transitionFraction)
    val eased = 0.5 - 0.5 * cos(progress * Math.PI)
    return wordIndex + eased
}

private fun wrapCarouselOffset(offset: Float, range: Float): Float {
    var value = offset % range
    if (value > range / 2f) value -= range
    if (value < -range / 2f) value += range
    return value
}
