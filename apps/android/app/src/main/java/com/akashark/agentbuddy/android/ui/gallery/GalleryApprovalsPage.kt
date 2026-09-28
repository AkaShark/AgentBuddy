package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.approvals.ApprovalCard
import com.akashark.agentbuddy.android.ui.approvals.ApprovalFailure
import com.akashark.agentbuddy.android.ui.approvals.ApprovalOutcomeKind
import com.akashark.agentbuddy.android.ui.approvals.ApprovalResultCard
import com.akashark.agentbuddy.android.ui.approvals.ApprovalSessionConfirmPanel
import com.akashark.agentbuddy.android.ui.approvals.ApprovalStackContent
import com.akashark.agentbuddy.android.ui.approvals.PendingApprovalBannerContent
import com.akashark.agentbuddy.android.ui.approvals.approvalBannerDetail
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySectionHeader
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.ApprovalDecisionValue
import uniffi.codex_mobile_client.ApprovalKind

/** Every approval card state, stacked on one scrolling page. */
@Composable
fun GalleryApprovalsPage() {
    val f = GalleryApprovalFixtures
    ChromeGalleryScroll {
        BuddySectionHeader("命令 · 待处理")
        ApprovalCard(approval = f.command, hostName = f.HOST, submittingDecision = null, failure = null, onDecision = {})

        BuddySectionHeader("长命令 · 已折叠")
        ApprovalCard(approval = f.longCommand, hostName = f.HOST, submittingDecision = null, failure = null, onDecision = {})

        BuddySectionHeader("修改文件")
        ApprovalCard(
            approval = f.fileChange,
            hostName = f.HOST,
            submittingDecision = null,
            failure = null,
            onDecision = {},
            filePaths = f.fileChangePaths,
            onViewDiff = {},
        )

        BuddySectionHeader("扩大访问权限")
        ApprovalCard(approval = f.permissions, hostName = f.HOST, submittingDecision = null, failure = null, onDecision = {})

        BuddySectionHeader("提交中")
        ApprovalCard(
            approval = f.command,
            hostName = f.HOST,
            submittingDecision = ApprovalDecisionValue.ACCEPT,
            failure = null,
            onDecision = {},
        )

        BuddySectionHeader("提交失败")
        ApprovalCard(
            approval = f.command,
            hostName = f.HOST,
            submittingDecision = null,
            failure = ApprovalFailure("连接已断开，等搭子重连后再试。", ApprovalDecisionValue.ACCEPT),
            onDecision = {},
        )

        BuddySectionHeader("本会话都允许 · 确认")
        ApprovalSessionConfirmPanel(kind = ApprovalKind.COMMAND, onConfirm = {}, onCancel = {})

        BuddySectionHeader("多个请求")
        var selected by remember { mutableIntStateOf(0) }
        ApprovalStackContent(
            pending = f.queue,
            selectedIndex = selected,
            hostName = f.HOST,
            submitting = emptyMap(),
            failures = emptyMap(),
            outcome = null,
            onSelect = { selected = it },
            onDecision = { _, _ -> },
            onDismissOutcome = {},
            maxCardHeight = 2000.dp,
        )

        BuddySectionHeader("结果")
        ApprovalOutcomeKind.entries.forEach { kind ->
            ApprovalResultCard(kind = kind, onDismiss = {})
        }
    }
}

/** App-level banner for requests of a conversation that is not on screen. */
@Composable
fun GalleryApprovalBannerPage() {
    ChromeGalleryScroll {
        PendingApprovalBannerContent(
            count = 1,
            detail = approvalBannerDetail(GalleryApprovalFixtures.HOST, "让登录体验更流畅"),
            actionTitle = "去处理",
            onOpen = {},
            onClose = {},
        )
        PendingApprovalBannerContent(
            count = 3,
            detail = approvalBannerDetail("本设备", "整理发布说明，并检查所有链接是否还能打开"),
            actionTitle = "去处理",
            onOpen = {},
            onClose = {},
        )
        BuddySectionHeader("在当前页处理（实时语音、小游戏）")
        PendingApprovalBannerContent(
            count = 1,
            detail = approvalBannerDetail(GalleryApprovalFixtures.HOST, "让登录体验更流畅"),
            actionTitle = "收起",
            onOpen = {},
            onClose = {},
        )
        ApprovalCard(
            approval = GalleryApprovalFixtures.command,
            hostName = GalleryApprovalFixtures.HOST,
            submittingDecision = null,
            failure = null,
            onDecision = {},
        )
        Box(Modifier.fillMaxWidth().height(BuddySpacing.xl))
        Text(
            text = "横幅不遮挡页面，关闭只隐藏横幅，不等于拒绝。",
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textSecondary,
        )
    }
}

@Composable
internal fun ChromeGalleryScroll(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.md),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        content()
    }
}
