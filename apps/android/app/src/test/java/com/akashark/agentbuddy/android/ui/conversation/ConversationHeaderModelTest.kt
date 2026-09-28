package com.akashark.agentbuddy.android.ui.conversation

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.AppServerTransportState

class ConversationHeaderModelTest {
    @Test
    fun `title prefers the thread title then the preview then untitled`() {
        assertEquals("修复登录", conversationHeaderTitle(" 修复登录 ", "preview"))
        assertEquals("preview", conversationHeaderTitle("  ", "preview"))
        assertEquals("未命名任务", conversationHeaderTitle(null, " "))
    }

    @Test
    fun `connection state follows the transport and a failed step`() {
        assertEquals(
            BuddyConnectionState.CONNECTED,
            conversationConnectionState(AppServerTransportState.CONNECTED, connectionFailed = true),
        )
        assertEquals(
            BuddyConnectionState.CONNECTING,
            conversationConnectionState(AppServerTransportState.CONNECTING, connectionFailed = false),
        )
        assertEquals(
            BuddyConnectionState.CONNECTING,
            conversationConnectionState(AppServerTransportState.UNRESPONSIVE, connectionFailed = false),
        )
        assertEquals(
            BuddyConnectionState.FAILED,
            conversationConnectionState(AppServerTransportState.DISCONNECTED, connectionFailed = true),
        )
        assertEquals(
            BuddyConnectionState.DISCONNECTED,
            conversationConnectionState(AppServerTransportState.UNKNOWN, connectionFailed = false),
        )
        assertEquals(BuddyConnectionState.DISCONNECTED, conversationConnectionState(null, connectionFailed = false))
    }

    @Test
    fun `subtitle reads partner and host and spells out a lost connection`() {
        assertEquals(
            "Codex · MacBook Pro",
            conversationHeaderSubtitle("Codex", "MacBook Pro", BuddyConnectionState.CONNECTED),
        )
        assertEquals(
            "Codex · MacBook Pro · 已断开",
            conversationHeaderSubtitle("Codex", "MacBook Pro", BuddyConnectionState.DISCONNECTED),
        )
        assertEquals(
            "Claude Code · 正在连接…",
            conversationHeaderSubtitle("Claude Code", null, BuddyConnectionState.CONNECTING),
        )
        assertEquals("连接失败", conversationHeaderSubtitle("", " ", BuddyConnectionState.FAILED))
    }
}
