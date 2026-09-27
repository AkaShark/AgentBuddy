package com.akashark.agentbuddy.android.ui.approvals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.codex_mobile_client.ApprovalKind

class ApprovalPresentationTest {
    @Test
    fun `stack index follows the selected request and clamps when it leaves`() {
        assertEquals(0, resolveApprovalIndex(emptyList(), "x", 3))
        assertEquals(1, resolveApprovalIndex(listOf("a", "b", "c"), "b", 0))
        assertEquals(2, resolveApprovalIndex(listOf("a", "b", "c"), "gone", 2))
        assertEquals(1, resolveApprovalIndex(listOf("a", "b"), "gone", 2))
        assertEquals(0, resolveApprovalIndex(listOf("a", "b"), null, 0))
    }

    @Test
    fun `copy uses the fixed counter and host sentinel wording`() {
        assertEquals("第 1 个，共 3 个", ApprovalCopy.position(1, 3))
        assertEquals("有 2 个请求等待你确认", ApprovalCopy.pendingCount(2))
        assertEquals("本设备", hostDisplayName("This Device"))
        assertNull(hostDisplayName("  "))
        assertEquals("MacBook Pro · 修复登录", approvalBannerDetail("MacBook Pro", " 修复登录 "))
        assertEquals("搭子想在 你的电脑 上运行一条命令。", ApprovalCopy.question(ApprovalKind.COMMAND, null))
    }
}
