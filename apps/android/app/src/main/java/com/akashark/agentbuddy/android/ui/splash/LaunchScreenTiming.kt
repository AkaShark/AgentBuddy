package com.akashark.agentbuddy.android.ui.splash

/** One deadline for startup, including the time spent in the system splash. */
internal class LaunchScreenTiming(private val startedAtMillis: Long) {
    fun remainingMillis(nowMillis: Long, contentReady: Boolean): Long {
        val duration = if (contentReady) 800L else 3_000L
        val elapsed = (nowMillis - startedAtMillis).coerceAtLeast(0L)
        return (duration - elapsed).coerceAtLeast(0L)
    }
}
