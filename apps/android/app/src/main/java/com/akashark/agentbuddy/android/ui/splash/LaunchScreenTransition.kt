package com.akashark.agentbuddy.android.ui.splash

import android.os.SystemClock
import android.view.View
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.WindowCompat
import com.akashark.agentbuddy.android.ui.AnimatedSplashScreen
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import kotlinx.coroutines.delay

/** Hands the system mark to the matching Compose mark without replaying startup. */
internal class LaunchScreenTransition(
    startedAtMillis: Long,
    val showBranding: Boolean,
) {
    val timing = LaunchScreenTiming(startedAtMillis)
    var showDetails by mutableStateOf(false)
        private set
    var markBounds: Rect? = null
    private var systemView: SplashScreenViewProvider? = null
    private var systemIcon: View? = null

    fun install(splash: SplashScreen, window: Window) {
        splash.setOnExitAnimationListener { provider ->
            systemView = provider
            // AndroidX restores decor fitting before calling the listener.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            showDetails = true
            if (!showBranding || BuddyMotion.isReducedMotion(window.context)) {
                removeSystemView()
                return@setOnExitAnimationListener
            }

            // Android 13+ may omit the icon for non-launcher starts. AndroidX
            // 1.0.1 exposes that nullable platform view through a !! getter.
            val icon = runCatching { provider.iconView }.getOrNull()
            systemIcon = icon
            if (icon != null && markBounds != null) {
                val bounds = checkNotNull(markBounds)
                val position = IntArray(2)
                icon.getLocationInWindow(position)
                // Preserve the platform's drawable scale: on API31+ iconView
                // excludes part of the 288dp viewport. Only correct the center
                // for differences in system-bar insets between the two windows.
                icon.animate()
                    .translationX(bounds.center.x - position[0] - icon.width / 2f)
                    .translationY(bounds.center.y - position[1] - icon.height / 2f)
                    .setDuration(BuddyMotion.STATE.durationMillis.toLong())
                    .start()
            }
            provider.view.animate()
                .alpha(0f)
                .setDuration(BuddyMotion.STATE.durationMillis.toLong())
                .withEndAction { removeSystemView() }
                .start()
        }
    }

    fun onFirstFrame() {
        // A recreated Activity need not receive a system exit callback. The
        // Compose overlay and its deadline must never depend on that callback.
        showDetails = true
    }

    fun dispose() {
        systemIcon?.animate()?.cancel()
        systemView?.view?.animate()?.cancel()
        removeSystemView()
    }

    private fun removeSystemView() {
        systemView?.remove()
        systemView = null
        systemIcon = null
    }
}

@Composable
internal fun LaunchScreenOverlay(
    transition: LaunchScreenTransition,
    contentReady: Boolean,
) {
    var visible by remember(transition) { mutableStateOf(transition.showBranding) }
    val reduceMotion = buddyReduceMotion

    LaunchedEffect(transition) {
        withFrameNanos { }
        transition.onFirstFrame()
    }
    LaunchedEffect(transition, contentReady) {
        delay(transition.timing.remainingMillis(SystemClock.elapsedRealtime(), contentReady))
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = BuddyMotion.STATE.spec(reduceMotion)),
    ) {
        AnimatedSplashScreen(
            showDetails = transition.showDetails,
            onMarkPositioned = { transition.markBounds = it },
        )
    }
}
