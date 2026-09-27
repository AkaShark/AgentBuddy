package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind

/** Title and connection-method cards at the top of [DiscoveryScreen]. */
@Composable
internal fun DiscoveryChooser(
    onPairWithAgentBuddy: () -> Unit,
    onConnectedComputers: () -> Unit,
    onSshOrCodexUrl: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "添加服务器",
                color = AgentBuddyTheme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "选择你的连接方式。",
            color = AgentBuddyTheme.textSecondary,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChooserCard(
                title = "与 搭子 配对",
                subtitle = "在 Mac 上安装 AgentBuddy 桌面版，打开「配对」页后扫码。",
                badge = "推荐",
                icon = Icons.Default.QrCodeScanner,
                supportedAgents = AgentBuddyAgents,
                isRecommended = true,
                onClick = onPairWithAgentBuddy,
            )

            ChooserCard(
                title = "已连接电脑",
                subtitle = "连接一台已使用此 ChatGPT 账号登录并运行 Codex 的电脑。",
                badge = null,
                icon = Icons.Outlined.DesktopWindows,
                supportedAgents = CodexOnlyAgents,
                isRecommended = false,
                onClick = onConnectedComputers,
            )

            ChooserCard(
                title = "SSH 或 Codex URL",
                subtitle = "通过 SSH 连接，或粘贴 ws:// codex URL。",
                badge = null,
                icon = Icons.Outlined.Terminal,
                supportedAgents = CodexOnlyAgents,
                isRecommended = false,
                onClick = onSshOrCodexUrl,
            )
        }
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
)

private val CodexOnlyAgents: List<AgentRuntimeKind> = listOf("codex")

@Composable
private fun ChooserCard(
    title: String,
    subtitle: String,
    badge: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    supportedAgents: List<AgentRuntimeKind>,
    isRecommended: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isRecommended) {
        AgentBuddyTheme.accent.copy(alpha = 0.45f)
    } else {
        AgentBuddyTheme.accent.copy(alpha = 0.18f)
    }
    val backgroundColor = if (isRecommended) {
        AgentBuddyTheme.surface.copy(alpha = 0.85f)
    } else {
        AgentBuddyTheme.surface.copy(alpha = 0.6f)
    }
    val iconBubble = AgentBuddyTheme.accent.copy(alpha = if (isRecommended) 0.16f else 0.10f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .border(
                width = if (isRecommended) 1.dp else 0.8.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(36.dp)
                    .background(iconBubble, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AgentBuddyTheme.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        color = AgentBuddyTheme.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .background(
                                    AgentBuddyTheme.accent.copy(alpha = 0.14f),
                                    RoundedCornerShape(50),
                                )
                                .border(
                                    width = 0.6.dp,
                                    color = AgentBuddyTheme.accent.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(50),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = badge,
                                color = AgentBuddyTheme.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AgentBuddyTheme.textMuted,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (supportedAgents.isNotEmpty()) {
            SupportedAgentsStrip(supportedAgents)
        }
    }
}

@Composable
private fun SupportedAgentsStrip(agents: List<AgentRuntimeKind>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "兼容",
            color = AgentBuddyTheme.textMuted,
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            agents.forEach { agent ->
                com.akashark.agentbuddy.android.ui.common.AgentIconView(
                    kind = agent,
                    sizeDp = 18,
                )
            }
        }
    }
}
