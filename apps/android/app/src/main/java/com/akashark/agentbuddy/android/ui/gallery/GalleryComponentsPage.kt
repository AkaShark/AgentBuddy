package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChip
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChipTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionPill
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyContextChip
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyListRow
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyPageHeader
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySectionHeader
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyStatusLabel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyStatusPill
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyWordmark
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Every design-system component in its main states. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GalleryComponentsPage() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BuddySpacing.xl, vertical = BuddySpacing.md),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.lg),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
            BuddyWordmark(modifier = Modifier.weight(1f))
            BuddyIconButton(Icons.Outlined.Search, "搜索", onClick = {})
            BuddyIconButton(Icons.Outlined.Settings, "设置", onClick = {}, tone = BuddyIconButtonTone.SOFT)
        }
        BuddyPageHeader(
            eyebrow = "你的想法，正在向前",
            title = "让想法，向前一步。",
            subtitle = "1 个任务进行中，1 个等待你确认。",
        )

        BuddySectionHeader("按钮", count = 6)
        BuddyButton("允许一次", onClick = {}, trailingIcon = Icons.Outlined.ArrowUpward)
        BuddyButton("扫码连接", onClick = {}, kind = BuddyButtonKind.BRAND, icon = Icons.Outlined.QrCodeScanner)
        Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
            BuddyButton("拒绝", onClick = {}, kind = BuddyButtonKind.SECONDARY, modifier = Modifier.weight(1f))
            BuddyButton("提交中", onClick = {}, isLoading = true, modifier = Modifier.weight(1f))
        }
        BuddyButton("在这台主机开始任务", onClick = {}, kind = BuddyButtonKind.SOFT)
        Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
            BuddyButton("全部任务", onClick = {}, kind = BuddyButtonKind.QUIET, fullWidth = false)
            BuddyButton("删除", onClick = {}, kind = BuddyButtonKind.DESTRUCTIVE, fullWidth = false)
            BuddyButton("不可用", onClick = {}, enabled = false, fullWidth = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
            BuddyIconButton(Icons.Outlined.Add, "添加", onClick = {}, tone = BuddyIconButtonTone.SOFT)
            BuddyIconButton(Icons.Outlined.Laptop, "主机", onClick = {}, tone = BuddyIconButtonTone.SURFACE)
            BuddyIconButton(Icons.Outlined.ArrowUpward, "发送", onClick = {}, tone = BuddyIconButtonTone.ACTION, diameter = 40.dp)
            BuddyIconButton(Icons.Outlined.ArrowUpward, "发送", onClick = {}, tone = BuddyIconButtonTone.ACTION, enabled = false)
        }

        BuddySectionHeader("状态")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            BuddyTaskState.entries.forEach { BuddyStatusPill(it) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs), verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
            BuddyConnectionState.entries.forEach { BuddyConnectionPill(it) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs), verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
            BuddyChip("AgentBuddy", icon = Icons.Outlined.Folder)
            BuddyChip("Codex", tone = BuddyChipTone.OUTLINE)
            BuddyContextChip("Codex", onClick = {}, trailingIcon = Icons.Outlined.KeyboardArrowDown)
            BuddyContextChip("离线", onClick = {}, enabled = false)
        }

        BuddySectionHeader("卡片")
        Column(Modifier.fillMaxWidth().buddyCard(BuddySurfaceTone.BRAND), verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
            BuddyStatusLabel(BuddyTaskState.RUNNING, tint = AgentBuddyTheme.onBrand)
            Text("让登录体验更流畅", style = buddyTextStyle(BuddyTextStyle.TITLE), color = AgentBuddyTheme.onBrand)
            Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                BuddyChip("AgentBuddy", tone = BuddyChipTone.ON_BRAND)
                BuddyChip("Codex", tone = BuddyChipTone.ON_BRAND)
            }
        }
        Column(Modifier.fillMaxWidth().buddyCard(BuddySurfaceTone.SURFACE)) {
            BuddyListRow(
                title = "运行登录流程测试",
                subtitle = "AgentBuddy · 等待你的确认",
                onClick = {},
                tile = { BuddyIconTile(BuddyTileContent.Symbol(BuddyTaskState.AWAITING_APPROVAL.icon), fill = AgentBuddyTheme.warningSurface, foreground = AgentBuddyTheme.warning) },
            )
            BuddyDivider()
            BuddyListRow(
                title = "Personal",
                subtitle = "3 个任务 · 昨天更新",
                onClick = {},
                tile = { BuddyIconTile(BuddyTileContent.Initial("P")) },
            )
        }
        BuddyBanner(BuddyBannerTone.WARNING, "与 MacBook Pro 的连接已断开，草稿会保留。", actionTitle = "重新连接", onAction = {})
        BuddyBanner(BuddyBannerTone.DANGER, "提交失败，请重试。")
        BuddyBanner(BuddyBannerTone.SUCCESS, "已允许本次操作。")
        BuddyEmptyState(
            icon = Icons.Outlined.Laptop,
            title = "还没有连接主机",
            message = "在电脑上打开搭子，扫描配对二维码即可连接。",
            actionTitle = "扫码连接",
            actionIcon = Icons.Outlined.QrCodeScanner,
            actionKind = BuddyButtonKind.BRAND,
            onAction = {},
        )
    }
}
