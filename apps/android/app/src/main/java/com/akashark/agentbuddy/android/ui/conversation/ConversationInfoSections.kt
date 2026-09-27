package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.connectionModeLabel
import com.akashark.agentbuddy.android.state.contextPercent
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.state.displayModelLabel
import com.akashark.agentbuddy.android.state.statusColor
import com.akashark.agentbuddy.android.state.statusLabel
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppConversationStats
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppThreadSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = AgentBuddyTheme.textMuted,
        fontSize = AgentBuddyTextStyle.caption2.scaled,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun ThreadDetailsSection(thread: AppThreadSnapshot?, isLocal: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = thread?.displayTitle ?: "未命名会话",
            color = AgentBuddyTheme.textPrimary,
            fontSize = 20f.scaled,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))

        if (thread != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = thread.displayModelLabel,
                    color = AgentBuddyTheme.accent,
                    fontSize = AgentBuddyTextStyle.code.scaled,
                    fontWeight = FontWeight.Medium,
                )
                thread.reasoningEffort?.let { effort ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = effort,
                        color = AgentBuddyTheme.textMuted,
                        fontSize = AgentBuddyTextStyle.caption2.scaled,
                        modifier = Modifier
                            .background(AgentBuddyTheme.border.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            thread.info.cwd?.let { cwd ->
                val context = androidx.compose.ui.platform.LocalContext.current
                val abbreviated = if (isLocal) {
                    com.akashark.agentbuddy.android.state.PathDisplay.display(cwd, true, context)
                } else {
                    cwd.replace(Regex("^/home/[^/]+"), "~")
                        .replace(Regex("^/Users/[^/]+"), "~")
                }
                Text(
                    text = abbreviated,
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                    fontFamily = AgentBuddyTheme.monoFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = "id: ${thread.key.threadId}",
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontFamily = AgentBuddyTheme.monoFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                thread.info.createdAt?.let { ts ->
                    InfoLabel("创建于", formatTimestamp(ts))
                }
                thread.info.updatedAt?.let { ts ->
                    InfoLabel("更新于", formatTimestamp(ts))
                }
            }
        }
    }
}

@Composable
private fun InfoLabel(label: String, value: String) {
    Column {
        Text(text = label, color = AgentBuddyTheme.textMuted, fontSize = 10f.scaled)
        Text(text = value, color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.caption.scaled)
    }
}

@Composable
internal fun ContextWindowBar(thread: AppThreadSnapshot) {
    val percent = thread.contextPercent
    val used = thread.contextTokensUsed?.toLong() ?: 0
    val window = thread.modelContextWindow?.toLong() ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("上下文窗口", color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.caption.scaled)
            Text("$percent%", color = AgentBuddyTheme.accent, fontSize = AgentBuddyTextStyle.caption.scaled, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AgentBuddyTheme.accent,
            trackColor = AgentBuddyTheme.border,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${formatTokenCount(used)} / ${formatTokenCount(window)} tokens",
            color = AgentBuddyTheme.textMuted,
            fontSize = 10f.scaled,
        )
    }
}

@Composable
internal fun StatsGrid(stats: AppConversationStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader("对话统计")
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("消息", "${stats.totalMessages}", "${stats.userMessageCount}u / ${stats.assistantMessageCount}a", Modifier.weight(1f))
            StatCard("轮次", "${stats.turnCount}", null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("命令", "${stats.commandsExecuted}", "${stats.commandsSucceeded}\u2713 / ${stats.commandsFailed}\u2717", Modifier.weight(1f))
            StatCard("已改文件", "${stats.filesChanged}", null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("MCP 调用", "${stats.mcpToolCallCount}", null, Modifier.weight(1f))
            StatCard("命令耗时", formatDuration(stats.totalCommandDurationMs), null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("差异", "+${stats.diffAdditions} / -${stats.diffDeletions}", null, Modifier.weight(1f))
            StatCard("图片", "${stats.imageCount}", null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(text = title, color = AgentBuddyTheme.textMuted, fontSize = 10f.scaled)
        Text(
            text = value,
            color = AgentBuddyTheme.textPrimary,
            fontSize = 18f.scaled,
            fontWeight = FontWeight.Bold,
            fontFamily = AgentBuddyTheme.monoFont,
        )
        if (subtitle != null) {
            Text(text = subtitle, color = AgentBuddyTheme.textSecondary, fontSize = 10f.scaled)
        }
    }
}

@Composable
internal fun ServerInfoSection(server: AppServerSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader("服务器")
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(server.statusColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = server.displayName,
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.body.scaled,
                fontWeight = FontWeight.Medium,
            )
        }

        InfoRow("主机", "${server.host}:${server.port}")
        InfoRow("模式", server.connectionModeLabel)
        InfoRow("状态", server.statusLabel)

        server.account?.let { account ->
            when (account) {
                is uniffi.codex_mobile_client.Account.Chatgpt -> {
                    InfoRow("账户", account.email)
                    InfoRow("计划", account.planType.toString())
                }
                is uniffi.codex_mobile_client.Account.ApiKey -> {
                    InfoRow("认证", "API 密钥")
                }
            }
        }

        server.availableModels?.let { models ->
            if (models.isNotEmpty()) {
                Text("可用模型", color = AgentBuddyTheme.textMuted, fontSize = 10f.scaled)
                Text(
                    text = models.joinToString(", ") { it.displayName.ifBlank { it.id } },
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = AgentBuddyTextStyle.caption2.scaled,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = AgentBuddyTheme.textMuted, fontSize = AgentBuddyTextStyle.caption2.scaled)
        Text(
            text = value,
            color = AgentBuddyTheme.textSecondary,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
            fontFamily = AgentBuddyTheme.monoFont,
        )
    }
}

// --- Utilities ---

private fun formatTimestamp(epochSeconds: Long): String {
    val now = System.currentTimeMillis()
    val ts = epochSeconds * 1000
    val diff = now - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(ts))
    }
}

internal fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format("%.1fs", ms / 1000.0)
    else -> String.format("%.1fm", ms / 60_000.0)
}
