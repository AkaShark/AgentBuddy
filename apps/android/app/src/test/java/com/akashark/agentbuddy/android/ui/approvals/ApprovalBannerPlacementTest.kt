package com.akashark.agentbuddy.android.ui.approvals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.codex_mobile_client.ThreadKey

class ApprovalBannerPlacementTest {
    private val threadA = ThreadKey(serverId = "srv", threadId = "thread-a")
    private val threadB = ThreadKey(serverId = "srv", threadId = "thread-b")

    @Test
    fun `request for another thread opens that thread`() {
        assertFalse(approvalBannerExpandsInPlace(threadB, onScreenThread = threadA, keepCurrentScreen = false))
        assertFalse(approvalBannerExpandsInPlace(threadB, onScreenThread = null, keepCurrentScreen = false))
    }

    @Test
    fun `request without a thread expands in place`() {
        assertTrue(approvalBannerExpandsInPlace(null, onScreenThread = null, keepCurrentScreen = false))
    }

    @Test
    fun `thread already on screen without its stack expands in place`() {
        // Realtime voice, or a conversation with the minigame covering the stack.
        assertTrue(approvalBannerExpandsInPlace(threadA, onScreenThread = threadA, keepCurrentScreen = false))
    }

    @Test
    fun `an active voice call is never left to answer another thread`() {
        assertTrue(approvalBannerExpandsInPlace(threadB, onScreenThread = threadA, keepCurrentScreen = true))
    }

    @Test
    fun `banner stays below the tallest registered header`() {
        val first = Any()
        val second = Any()
        assertEquals(0, ApprovalBannerTopInset.px)
        ApprovalBannerTopInset.set(first, 120)
        ApprovalBannerTopInset.set(second, 180)
        assertEquals(180, ApprovalBannerTopInset.px)
        ApprovalBannerTopInset.clear(second)
        assertEquals(120, ApprovalBannerTopInset.px)
        ApprovalBannerTopInset.clear(first)
        assertEquals(0, ApprovalBannerTopInset.px)
    }
}
