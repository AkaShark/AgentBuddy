package com.akashark.agentbuddy.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBrandMark
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
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

/** Shared connection mark and the familiar vertical agent-name carousel. */
@Composable
fun AnimatedSplashScreen() {
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    val reduceMotion = buddyReduceMotion

    LaunchedEffect(reduceMotion) {
        elapsed = 0.0
        if (!reduceMotion) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { elapsed = (it - start) / 1_000_000_000.0 }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(AgentBuddyTheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(y = -BuddySpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xl),
        ) {
            BuddyBrandMark(size = BuddySize.splashMark)
            Text(
                text = "搭子",
                color = AgentBuddyTheme.textPrimary,
                style = buddyTextStyle(BuddyTextStyle.DISPLAY),
            )
        }

        BuddyChromeTypeLimit {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = BuddySpacing.huge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpinningProviderCarousel(elapsed = elapsed)
                Text(
                    text = " 在你的手机上",
                    color = AgentBuddyTheme.textMuted,
                    style = buddyTextStyle(BuddyTextStyle.CODE),
                )
            }
        }
    }
}

// Keep the order identical to the iOS splash.
private val SplashProviders = listOf(
    "codex", "pi", "amp", "opencode", "claude", "droid", "hermes", "devin", "grok",
)

@Composable
private fun SpinningProviderCarousel(elapsed: Double) {
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
                color = if (selected) AgentBuddyTheme.textSecondary else AgentBuddyTheme.textMuted,
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
