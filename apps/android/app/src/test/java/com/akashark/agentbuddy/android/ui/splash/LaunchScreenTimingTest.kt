package com.akashark.agentbuddy.android.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchScreenTimingTest {
    @Test
    fun systemStartupTimeCountsTowardTheMinimum() {
        val timing = LaunchScreenTiming(startedAtMillis = 1_000)
        assertEquals(2_300L, timing.remainingMillis(nowMillis = 1_500, contentReady = true))
    }

    @Test
    fun slowInitializationDoesNotAddASecondBrandingWait() {
        val timing = LaunchScreenTiming(startedAtMillis = 1_000)
        assertEquals(0L, timing.remainingMillis(nowMillis = 4_000, contentReady = true))
    }

    @Test
    fun readinessSwitchKeepsTheOriginalDeadline() {
        val timing = LaunchScreenTiming(startedAtMillis = 1_000)
        assertEquals(2_500L, timing.remainingMillis(nowMillis = 1_500, contentReady = false))
        assertEquals(2_100L, timing.remainingMillis(nowMillis = 1_700, contentReady = true))
    }

    @Test
    fun failedStartupCanStillRevealItsErrorWithoutAnExitCallback() {
        val timing = LaunchScreenTiming(startedAtMillis = 1_000)
        assertEquals(0L, timing.remainingMillis(nowMillis = 4_000, contentReady = false))
        assertEquals(0L, timing.remainingMillis(nowMillis = 5_000, contentReady = false))
    }
}
