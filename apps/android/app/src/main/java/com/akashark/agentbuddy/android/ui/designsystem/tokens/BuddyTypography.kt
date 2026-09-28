package com.akashark.agentbuddy.android.ui.designsystem.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.LocalTextScale

/**
 * Type scale from the Mint design system (`spec/UI-GUIDELINES.md` §3). Sizes
 * are sp at the default font size: they grow with the system font scale and
 * with the app-wide text size setting ([LocalTextScale]).
 */
enum class BuddyTextStyle(
    val size: Float,
    val lineHeight: Float,
    val weight: FontWeight,
) {
    /** 32 / 42, semibold: short home title, at most two lines. */
    DISPLAY(32f, 42f, FontWeight.SemiBold),

    /** 24 / 34, semibold: page and sheet titles. */
    TITLE(24f, 34f, FontWeight.SemiBold),

    /** 17 / 26, semibold: section and card titles. */
    HEADING(17f, 26f, FontWeight.SemiBold),

    /** 16 / 26, regular: conversation and explanatory text. */
    BODY(16f, 26f, FontWeight.Normal),

    /** 14 / 20, medium: buttons, pickers, secondary actions. */
    LABEL(14f, 20f, FontWeight.Medium),

    /** 12 / 18, regular: timestamps and secondary status. Nothing goes smaller. */
    CAPTION(12f, 18f, FontWeight.Normal),

    /** 14 / 22, regular, monospaced: commands, paths, code. */
    CODE(14f, 22f, FontWeight.Normal),
    ;

    val isMonospaced: Boolean
        get() = this == CODE
}

@Suppress("DEPRECATION")
private val BuddyPlatformTextStyle = PlatformTextStyle(includeFontPadding = false)

/**
 * Interface text follows the user's font preference (system by default); code
 * is always Berkeley Mono.
 */
fun buddyFontFamily(style: BuddyTextStyle): FontFamily =
    if (style.isMonospaced || AgentBuddyThemeManager.monoFontEnabled) BerkeleyMono else FontFamily.Default

/** Smallest Mint text size in sp: the spec allows nothing below 12. */
const val BUDDY_MIN_TEXT_SIZE = 12f

/** [size] scaled by the app text size, floored at [BUDDY_MIN_TEXT_SIZE] (极小 / 小 shrink the scale). */
fun buddyScaledTextSize(size: Float, textScale: Float): Float =
    (size * textScale).coerceAtLeast(BUDDY_MIN_TEXT_SIZE)

/** A Mint text style: font, weight and line height, scaled with system and app text size. */
@Composable
@ReadOnlyComposable
fun buddyTextStyle(
    style: BuddyTextStyle,
    weight: FontWeight? = null,
): TextStyle {
    val scale = LocalTextScale.current
    return TextStyle(
        fontFamily = buddyFontFamily(style),
        fontWeight = weight ?: style.weight,
        fontSize = buddyScaledTextSize(style.size, scale).sp,
        // Relative line height keeps the ratio under Android's non-linear font scaling.
        lineHeight = (style.lineHeight / style.size).em,
        platformStyle = BuddyPlatformTextStyle,
    )
}

/** Largest system font scale that compact chrome follows (tab bar, chip rows, headers). */
const val BUDDY_CHROME_MAX_FONT_SCALE = 1.3f

/**
 * Caps text growth for compact chrome (bottom bar, button rows, toolbar
 * headers). Content text outside keeps scaling into the largest sizes; chrome
 * would otherwise push controls off screen.
 */
@Composable
fun BuddyChromeTypeLimit(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val textScale = LocalTextScale.current
    CompositionLocalProvider(
        LocalDensity provides
            Density(density.density, density.fontScale.coerceAtMost(BUDDY_CHROME_MAX_FONT_SCALE)),
        LocalTextScale provides textScale.coerceAtMost(1f),
        content = content,
    )
}
