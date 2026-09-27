package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.conversation.ConversationHeaderActions
import com.akashark.agentbuddy.android.ui.conversation.ConversationHeaderContent
import com.akashark.agentbuddy.android.ui.conversation.ConversationHeaderState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySectionHeader

private val noopHeaderActions = ConversationHeaderActions(
    onBack = {},
    onToggleModelPanel = {},
    onOpenModelPanel = {},
    onOpenPermissions = {},
    onOpenPlanMode = {},
    onReload = {},
    onOpenInfo = {},
)

/** Conversation header in its connection and marker states; the last one has the 「…」 menu open. */
@Composable
fun GalleryConversationHeaderPage() {
    ChromeGalleryScroll {
        BuddySectionHeader("已连接")
        GalleryHeader(ConversationHeaderState("让登录体验更流畅", "Codex · MacBook Pro", BuddyConnectionState.CONNECTED))
        BuddySectionHeader("计划模式 · 完全访问 · 快速")
        GalleryHeader(
            ConversationHeaderState(
                title = "整理发布说明，并检查所有链接是否还能打开",
                subtitle = "Claude Code · MacBook Pro",
                connection = BuddyConnectionState.CONNECTED,
                isPlanMode = true,
                isFullAccess = true,
                isFastMode = true,
            ),
        )
        BuddySectionHeader("正在连接")
        GalleryHeader(
            ConversationHeaderState("让登录体验更流畅", "Codex · MacBook Pro · 正在连接…", BuddyConnectionState.CONNECTING),
        )
        BuddySectionHeader("已断开")
        GalleryHeader(
            ConversationHeaderState("未命名任务", "Codex · 本设备 · 已断开", BuddyConnectionState.DISCONNECTED),
        )
        BuddySectionHeader("连接失败 · 重新加载中")
        GalleryHeader(
            ConversationHeaderState(
                title = "让登录体验更流畅",
                subtitle = "Codex · MacBook Pro · 连接失败",
                connection = BuddyConnectionState.FAILED,
                isReloading = true,
            ),
        )
        BuddySectionHeader("「…」菜单")
        GalleryHeader(
            ConversationHeaderState("让登录体验更流畅", "Codex · MacBook Pro", BuddyConnectionState.CONNECTED, isPlanMode = true),
            menuOpen = true,
        )
        Box(Modifier.fillMaxWidth().height(320.dp))
    }
}

@Composable
private fun GalleryHeader(state: ConversationHeaderState, menuOpen: Boolean = false) {
    ConversationHeaderContent(
        state = state,
        actions = noopHeaderActions,
        modifier = Modifier.background(AgentBuddyTheme.background),
        menuInitiallyExpanded = menuOpen,
    )
}
