package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppActivityByDayEntry
import uniffi.codex_mobile_client.AppModelUsageEntry
import uniffi.codex_mobile_client.AppServerUsageStats
import uniffi.codex_mobile_client.AppTokensByThreadEntry
import uniffi.codex_mobile_client.RateLimitSnapshot
import uniffi.codex_mobile_client.RateLimitWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Usage level for a fraction (context window, rate limits): under 60%
 * success, from 60% warning, from 80% danger. Warning and danger also carry
 * an icon, so the level never relies on colour alone.
 */
internal enum class InfoUsageLevel {
    NORMAL,
    ELEVATED,
    CRITICAL,
    ;

    val color: Color
        get() =
            when (this) {
                NORMAL -> AgentBuddyTheme.success
                ELEVATED -> AgentBuddyTheme.warning
                CRITICAL -> AgentBuddyTheme.danger
            }

    val icon: ImageVector?
        get() =
            when (this) {
                NORMAL -> null
                ELEVATED -> Icons.Outlined.WarningAmber
                CRITICAL -> Icons.Outlined.Error
            }

    companion object {
        fun of(fraction: Float): InfoUsageLevel =
            when {
                fraction >= 0.8f -> CRITICAL
                fraction >= 0.6f -> ELEVATED
                else -> NORMAL
            }
    }
}

/** Percentage in the level colour, led by the level icon when elevated. */
@Composable
internal fun InfoUsageValue(level: InfoUsageLevel, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        level.icon?.let { Icon(it, contentDescription = null, tint = level.color, modifier = Modifier.size(16.dp)) }
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
            color = level.color,
        )
    }
}

/** One-shot 0 → 1 entrance for bars and rings; instant with reduced motion. */
@Composable
private fun rememberChartEntrance(): Float {
    val reduceMotion = buddyReduceMotion
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started || reduceMotion) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(800),
        label = "infoChartEntrance",
    )
    return progress
}

/** 「服务器用量」 card: token, activity and model charts, then rate limits. */
@Composable
internal fun ServerUsageCard(
    usage: AppServerUsageStats?,
    rateLimits: RateLimitSnapshot?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().buddyCard(BuddySurfaceTone.SURFACE),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.lg),
    ) {
        Text(
            text = "服务器用量",
            style = buddyTextStyle(BuddyTextStyle.HEADING),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (usage != null) {
            if (usage.tokensByThread.isNotEmpty()) TokenUsageChart(usage.tokensByThread)
            if (usage.activityByDay.isNotEmpty()) ActivityChart(usage.activityByDay)
            if (usage.modelUsage.isNotEmpty()) ModelBreakdownChart(usage.modelUsage)
        }
        if (rateLimits != null) RateLimitGauges(rateLimits)
    }
}

@Composable
private fun ChartTitle(text: String) {
    Text(text = text, style = buddyTextStyle(BuddyTextStyle.LABEL), color = AgentBuddyTheme.textSecondary)
}

/** Labelled horizontal bar: name and value above an 8dp bar, read as one line. */
@Composable
private fun LabelledBar(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
            Text(
                text = label,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = buddyTextStyle(BuddyTextStyle.CAPTION).copy(fontFeatureSettings = "tnum"),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        UsageBar(fraction = fraction, color = color)
    }
}

@Composable
private fun TokenUsageChart(data: List<AppTokensByThreadEntry>) {
    val progress = rememberChartEntrance()
    val maxTokens = data.maxOf { it.tokens.toLong() }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
        ChartTitle("按对话统计的 token 用量")
        data.forEach { entry ->
            val tokens = entry.tokens.toLong()
            LabelledBar(
                label = entry.threadTitle.ifBlank { "未命名任务" },
                value = formatTokenCount(tokens),
                fraction = tokens.toFloat() / maxTokens * progress,
                color = AgentBuddyTheme.link,
            )
        }
    }
}

@Composable
private fun ModelBreakdownChart(data: List<AppModelUsageEntry>) {
    val progress = rememberChartEntrance()
    val maxCount = data.maxOf { it.threadCount.toInt() }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
        ChartTitle("模型用量")
        data.forEach { entry ->
            val count = entry.threadCount.toInt()
            LabelledBar(
                label = entry.model,
                value = "$count 个任务",
                fraction = count.toFloat() / maxCount * progress,
                color = AgentBuddyTheme.link,
            )
        }
    }
}

/** Turns per day as bars; the dates and a text summary carry the numbers. */
@Composable
private fun ActivityChart(data: List<AppActivityByDayEntry>) {
    val progress = rememberChartEntrance()
    val maxCount = data.maxOf { it.turnCount.toInt() }.coerceAtLeast(1)
    val total = data.sumOf { it.turnCount.toInt() }
    val formatter = remember { DateTimeFormatter.ofPattern("M/d") }
    fun dateLabel(entry: AppActivityByDayEntry) =
        Instant.ofEpochSecond(entry.dateEpoch).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
    val summary = "${data.size} 天共 $total 轮，单日最多 $maxCount 轮"
    val barColor = AgentBuddyTheme.success
    val gridColor = AgentBuddyTheme.border

    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        ChartTitle("活动时间线")
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clearAndSetSemantics { contentDescription = "活动时间线：$summary" },
        ) {
            for (i in 0..3) {
                val y = size.height * (1f - i / 3f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val slot = size.width / data.size
            val barWidth = (slot * 0.64f).coerceAtMost(28.dp.toPx())
            data.forEachIndexed { index, entry ->
                val barHeight = (entry.turnCount.toFloat() / maxCount * size.height * progress).coerceAtLeast(2f)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(Modifier.clearAndSetSemantics {}) {
            Text(
                text = dateLabel(data.first()),
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (data.size > 1) {
                Text(text = dateLabel(data.last()), style = buddyTextStyle(BuddyTextStyle.CAPTION), color = AgentBuddyTheme.textSecondary)
            }
        }
        Text(text = summary, style = buddyTextStyle(BuddyTextStyle.CAPTION), color = AgentBuddyTheme.textSecondary)
    }
}

/** 「速率限制」: one ring per window with the percentage inside and the level icon beside the label. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RateLimitGauges(rateLimits: RateLimitSnapshot) {
    val windows = listOfNotNull(rateLimits.primary?.let { "主限额" to it }, rateLimits.secondary?.let { "次限额" to it })
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
        ChartTitle("速率限制")
        if (windows.isEmpty()) {
            Text("暂无速率限制数据", style = buddyTextStyle(BuddyTextStyle.CAPTION), color = AgentBuddyTheme.textSecondary)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xl), verticalArrangement = Arrangement.spacedBy(BuddySpacing.md)) {
                windows.forEach { (label, window) -> RateLimitRing(label, window) }
            }
        }
    }
}

@Composable
private fun RateLimitRing(label: String, window: RateLimitWindow) {
    val progress = rememberChartEntrance()
    val percent = window.usedPercent.coerceIn(0, 100)
    val level = InfoUsageLevel.of(percent / 100f)
    val track = AgentBuddyTheme.surfaceSoft
    val levelColor = level.color
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label 已用 $percent%" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(72.dp)) {
                val stroke = 6.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(levelColor, -90f, 360f * percent / 100f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(
                text = "$percent%",
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
                color = AgentBuddyTheme.textPrimary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            level.icon?.let { Icon(it, contentDescription = null, tint = level.color, modifier = Modifier.size(14.dp)) }
            Text(text = label, style = buddyTextStyle(BuddyTextStyle.CAPTION), color = AgentBuddyTheme.textSecondary)
        }
    }
}
