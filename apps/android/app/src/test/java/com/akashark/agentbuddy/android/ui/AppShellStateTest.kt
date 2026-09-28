package com.akashark.agentbuddy.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.ThreadKey

class AppShellStateTest {
    private val sessions = Route.Sessions(serverId = null, title = "全部任务")
    private val key = ThreadKey(serverId = "s1", threadId = "t1")

    @Test
    fun `a conversation opened from 全部任务 returns to the list on back`() {
        val shell = AppShellState(initialServerId = null)
        shell.navigate(sessions)
        shell.pushConversation(key)
        assertEquals(listOf(Route.Home, sessions, Route.Conversation(key)), shell.navStack)
        shell.navigateBack()
        assertEquals(sessions, shell.currentRoute)
    }

    @Test
    fun `pushing the conversation already on top does not stack a duplicate`() {
        val shell = AppShellState(initialServerId = null)
        shell.navigate(sessions)
        shell.navigateToConversation(key)
        shell.pushConversation(key)
        assertEquals(listOf(Route.Home, Route.Conversation(key)), shell.navStack)
    }
}
