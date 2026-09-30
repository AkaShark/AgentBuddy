package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.AgentIconView
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import com.akashark.agentbuddy.android.ui.common.runtimeLabel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChevron
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * 「添加主机」 chooser of [DiscoveryScreen]: title, explanation and one Mint card
 * per connection method. QR pairing is the recommended option (brand tile
 * and a 「推荐」 tag); [wakingHostName] shows while a manual connect is waking
 * the host and probing its ports.
 */
@Composable
internal fun DiscoveryChooser(
    onPairWithAgentBuddy: () -> Unit,
    onConnectedComputers: () -> Unit,
    onSshOrCodexUrl: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    wakingHostName: String? = null,
) {
    DiscoverySheetScaffold(
        modifier = modifier,
        contentSpacing = BuddySpacing.sm,
        header = {
            DiscoverySheetHeader(
                title = "添加主机",
                actionTitle = "关闭",
                onAction = onClose,
                subtitle = "选择你的连接方式。任务会在那台电脑上运行，你在这里随时跟进。",
            )
        },
    ) {
        if (wakingHostName != null) {
            BuddyBanner(
                tone = BuddyBannerTone.INFO,
                icon = Icons.Outlined.PowerSettingsNew,
                message = "正在唤醒 $wakingHostName 并等待它响应，最多需要 18 秒。",
            )
        }
        ChooserCard(
            title = "扫描电脑上的二维码",
            subtitle = "在电脑上打开搭子，扫描它显示的配对二维码。",
            icon = Icons.Outlined.QrCodeScanner,
            supportedAgents = AgentBuddyAgents,
            isRecommended = true,
            onClick = onPairWithAgentBuddy,
        )
        ChooserCard(
            title = "已连接的电脑",
            subtitle = "登录同一 ChatGPT 账号并运行 Codex 的电脑。",
            icon = Icons.Outlined.DesktopWindows,
            supportedAgents = CodexOnlyAgents,
            isRecommended = false,
            onClick = onConnectedComputers,
        )
        ChooserCard(
            title = "SSH 或地址",
            subtitle = "通过 SSH 登录，或输入 ws:// Codex 地址。",
            icon = Icons.Outlined.Terminal,
            supportedAgents = CodexOnlyAgents,
            isRecommended = false,
            onClick = onSshOrCodexUrl,
        )
    }
}

/**
 * Canonical agent list shown on the agentbuddy chooser card. Mirrors
 * the splash carousel order so cold-start branding stays consistent.
 * New agents added in the alleycat manifest still surface on connected
 * hosts via the real metadata store; this list only seeds the
 * pre-pair preview.
 */
private val AgentBuddyAgents: List<AgentRuntimeKind> = listOf(
    "codex",
    "pi",
    "amp",
    "opencode",
    "claude",
    "droid",
    "hermes",
    "devin",
    "grok",
    "mfcli",
)

private val CodexOnlyAgents: List<AgentRuntimeKind> = listOf("codex")

@Composable
private fun ChooserCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    supportedAgents: List<AgentRuntimeKind>,
    isRecommended: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuddyShapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .buddyCard(BuddySurfaceTone.SURFACE),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            BuddyIconTile(
                content = BuddyTileContent.Symbol(icon),
                fill = if (isRecommended) AgentBuddyTheme.brand else AgentBuddyTheme.surfaceSoft,
                foreground = if (isRecommended) AgentBuddyTheme.onBrand else AgentBuddyTheme.textPrimary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
            ) {
                if (isRecommended) {
                    RecommendedTag()
                }
                Text(
                    text = title,
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = AgentBuddyTheme.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
            BuddyChevron(Modifier.padding(top = BuddySpacing.sm))
        }
        if (supportedAgents.isNotEmpty()) {
            SupportedAgentsStrip(supportedAgents)
        }
    }
}

@Composable
private fun RecommendedTag() {
    BuddyChromeTypeLimit {
        Text(
            text = "推荐",
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.SemiBold),
            color = AgentBuddyTheme.onBrand,
            modifier = Modifier
                .background(AgentBuddyTheme.brand, CircleShape)
                .padding(horizontal = BuddySpacing.xs, vertical = 2.dp),
        )
    }
}

/** 「兼容」 caption followed by one small chip (icon + name) per agent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportedAgentsStrip(agents: List<AgentRuntimeKind>) {
    BuddyChromeTypeLimit {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "兼容",
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = AgentBuddyTheme.textSecondary,
                modifier = Modifier
                    .heightIn(min = 28.dp)
                    .padding(top = 5.dp, end = 2.dp),
            )
            agents.forEach { agent -> AgentChip(agent) }
        }
    }
}

@Composable
private fun AgentChip(agent: AgentRuntimeKind) {
    Row(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.control)
            .padding(start = 4.dp, end = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics {}) {
            AgentIconView(kind = agent, sizeDp = 20)
        }
        Text(
            text = agent.runtimeLabel,
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = AgentBuddyTheme.textPrimary,
            maxLines = 1,
        )
    }
}
