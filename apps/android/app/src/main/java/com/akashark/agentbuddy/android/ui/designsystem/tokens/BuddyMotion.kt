package com.akashark.agentbuddy.android.ui.designsystem.tokens

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Durations from the Mint design system. */
enum class BuddyMotion(val durationMillis: Int) {
    PRESS(120),
    STATE(160),
    PAGE(240),
    SHEET(320),
    ;

    /** An ease-out tween, or an instant change when reduced motion is on. */
    fun <T> spec(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMillis, easing = EaseOut)

    companion object {
        /**
         * Android has no dedicated "reduce motion" switch; "Remove animations"
         * in accessibility settings sets the animator duration scale to 0.
         */
        fun isReducedMotion(context: Context): Boolean =
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }.getOrDefault(false)
    }
}

/** True when the user removed animations; looping and decorative motion must stop. */
val LocalBuddyReduceMotion = staticCompositionLocalOf { false }

val buddyReduceMotion: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalBuddyReduceMotion.current
