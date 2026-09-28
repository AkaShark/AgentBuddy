package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionPill
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySectionHeader
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyStatusPill
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import kotlinx.coroutines.delay
import uniffi.codex_mobile_client.AppConversationStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Detail card (radius 16): status, title, partner · host, then one row per detail. */
@Composable
internal fun TaskInfoHeroCard(hero: TaskInfoHero) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.detailCard),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        BuddyStatusPill(state = hero.status, title = hero.statusTitle)
        Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
            Text(
                text = hero.title,
                style = buddyTextStyle(BuddyTextStyle.TITLE),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            hero.partnerAndHost?.let {
                Text(
                    text = it,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
        }
        BuddyDivider(Modifier.padding(vertical = BuddySpacing.xxs))
        Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
            hero.model?.let { InfoDetailRow(Icons.Outlined.Memory, "模型", it) }
            hero.effort?.let { InfoDetailRow(Icons.Outlined.Speed, "推理强度", it) }
            hero.cwdDisplay?.let {
                InfoDetailRow(
                    icon = Icons.Outlined.Folder,
                    label = "工作目录",
                    value = it,
                    monospaced = true,
                    copyValue = hero.cwdFull ?: it,
                    copyLabel = "复制工作目录",
                )
            }
            InfoDetailRow(
                icon = Icons.Outlined.Tag,
                label = "任务 ID",
                value = hero.threadId,
                monospaced = true,
                copyValue = hero.threadId,
                copyLabel = "复制任务 ID",
            )
            hero.createdAt?.let { InfoDetailRow(Icons.Outlined.Schedule, "创建时间", formatInfoTimestamp(it)) }
            hero.updatedAt?.let { InfoDetailRow(Icons.Outlined.Update, "更新时间", formatInfoTimestamp(it)) }
        }
    }
}

/**
 * Icon, caption label and value; with [copyValue] a copy button that turns
 * into a checkmark for a moment after copying.
 */
@Composable
private fun InfoDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    monospaced: Boolean = false,
    copyValue: String? = null,
    copyLabel: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (copyValue != null) Modifier.heightIn(min = BuddySize.minHitTarget) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(BuddySize.icon))
        Column(
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
            )
            Text(
                text = value,
                style = buddyTextStyle(if (monospaced) BuddyTextStyle.CODE else BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (copyValue != null) {
            CopyButton(value = copyValue, label = copyLabel ?: "复制")
        }
    }
}

@Composable
private fun CopyButton(value: String, label: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(value) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    BuddyIconButton(
        icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
        contentDescription = if (copied) "已复制" else label,
        onClick = {
            clipboard.setText(AnnotatedString(value))
            copied = true
        },
        tint = if (copied) AgentBuddyTheme.success else AgentBuddyTheme.textSecondary,
    )
}

/** Context window: percent in the usage-level colour (+ icon when high), bar and token counts. */
@Composable
internal fun ContextWindowCard(usage: TaskContextUsage) {
    val fraction = (usage.usedTokens.toFloat() / usage.windowTokens.coerceAtLeast(1)).coerceIn(0f, 1f)
    val level = InfoUsageLevel.of(fraction)
    val percent = (fraction * 100).toInt()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .buddyCard(BuddySurfaceTone.SURFACE)
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "上下文窗口已用 $percent%，${formatTokenCount(usage.usedTokens)} / ${formatTokenCount(usage.windowTokens)} tokens"
                },
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "上下文窗口",
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier.weight(1f),
            )
            InfoUsageValue(level = level, text = "$percent%")
        }
        UsageBar(fraction = fraction, color = level.color)
        Row {
            Text(
                text = formatTokenCount(usage.usedTokens),
                style = buddyTextStyle(BuddyTextStyle.CAPTION).copy(fontFeatureSettings = "tnum"),
                color = AgentBuddyTheme.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${formatTokenCount(usage.windowTokens)} tokens",
                style = buddyTextStyle(BuddyTextStyle.CAPTION).copy(fontFeatureSettings = "tnum"),
                color = AgentBuddyTheme.textSecondary,
            )
        }
    }
}

/** 8dp capsule bar on the soft track. */
@Composable
internal fun UsageBar(
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(AgentBuddyTheme.surfaceSoft, CircleShape),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

/** 「对话统计」: two-column grid of stat tiles. */
@Composable
internal fun ConversationStatsSection(stats: AppConversationStats) {
    val tiles =
        listOf(
            StatTile("消息", "${stats.totalMessages}", "用户 ${stats.userMessageCount} · 助手 ${stats.assistantMessageCount}"),
            StatTile("轮次", "${stats.turnCount}", null),
            StatTile("命令", "${stats.commandsExecuted}", "成功 ${stats.commandsSucceeded} · 失败 ${stats.commandsFailed}"),
            StatTile("改动文件", "${stats.filesChanged}", null),
            StatTile("差异", "+${stats.diffAdditions} / -${stats.diffDeletions}", null),
            StatTile("MCP 调用", "${stats.mcpToolCallCount}", null),
            StatTile("执行时长", formatDuration(stats.totalCommandDurationMs), null),
            StatTile("图片", "${stats.imageCount}", null),
        )
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
        BuddySectionHeader(title = "对话统计")
        tiles.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
            ) {
                pair.forEach { tile -> StatTileView(tile, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class StatTile(val title: String, val value: String, val detail: String?)

@Composable
private fun StatTileView(tile: StatTile, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .buddyCard(BuddySurfaceTone.SOFT, shape = BuddyShapes.detailCard, padding = BuddySpacing.md)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = tile.value,
            style = buddyTextStyle(BuddyTextStyle.TITLE).copy(fontFeatureSettings = "tnum"),
            color = AgentBuddyTheme.textPrimary,
        )
        Text(
            text = tile.title,
            style = buddyTextStyle(BuddyTextStyle.LABEL),
            color = AgentBuddyTheme.textSecondary,
        )
        tile.detail?.let {
            Text(
                text = it,
                style = buddyTextStyle(BuddyTextStyle.CAPTION).copy(fontFeatureSettings = "tnum"),
                color = AgentBuddyTheme.textSecondary,
            )
        }
    }
}

/** 「服务器」: label / value rows, connection state as dot + text, first 8 models. */
@Composable
internal fun ServerInfoCard(server: TaskInfoServer) {
    Column(
        modifier = Modifier.fillMaxWidth().buddyCard(BuddySurfaceTone.SURFACE),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        Text(
            text = "服务器",
            style = buddyTextStyle(BuddyTextStyle.HEADING),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        ServerInfoRow("名称") { InfoValueText(server.name) }
        ServerInfoRow("地址") { InfoValueText(server.address, monospaced = true) }
        ServerInfoRow("模式") { InfoValueText(server.mode) }
        ServerInfoRow("状态") {
            BuddyConnectionPill(state = server.connection, title = server.connectionTitle, filled = false)
        }
        when {
            server.accountEmail != null ->
                ServerInfoRow("账户") {
                    Column(horizontalAlignment = Alignment.End) {
                        InfoValueText(server.accountEmail)
                        server.planLabel?.let {
                            Text(it, style = buddyTextStyle(BuddyTextStyle.CAPTION), color = AgentBuddyTheme.textSecondary)
                        }
                    }
                }
            server.usesApiKey -> ServerInfoRow("认证") { InfoValueText("API 密钥") }
        }
        if (server.models.isNotEmpty()) {
            BuddyDivider(Modifier.padding(vertical = BuddySpacing.xxs))
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
            ) {
                Text("可用模型", style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal), color = AgentBuddyTheme.textSecondary)
                server.models.take(8).forEach { InfoValueText(it, alignEnd = false) }
                if (server.models.size > 8) {
                    Text(
                        text = "还有 ${server.models.size - 8} 个",
                        style = buddyTextStyle(BuddyTextStyle.CAPTION),
                        color = AgentBuddyTheme.textSecondary,
                    )
                }
            }
        }
    }
}

/** Label on the leading edge, value on the trailing edge; the value wraps instead of shrinking. */
@Composable
private fun ServerInfoRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal), color = AgentBuddyTheme.textSecondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) { value() }
    }
}

@Composable
private fun InfoValueText(text: String, monospaced: Boolean = false, alignEnd: Boolean = true) {
    Text(
        text = text,
        style = if (monospaced) buddyTextStyle(BuddyTextStyle.CODE) else buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
        color = AgentBuddyTheme.textPrimary,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
    )
}

// --- Formatting ---

internal fun formatInfoTimestamp(epochSeconds: Long): String {
    val ts = epochSeconds * 1000
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ts))
    }
}

internal fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
    else -> String.format(Locale.US, "%.1fm", ms / 60_000.0)
}
