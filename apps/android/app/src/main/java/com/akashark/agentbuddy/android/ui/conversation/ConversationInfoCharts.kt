package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalTextScale
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppActivityByDayEntry
import uniffi.codex_mobile_client.AppModelUsageEntry
import uniffi.codex_mobile_client.AppTokensByThreadEntry
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun TokenUsageChart(data: List<AppTokensByThreadEntry>) {
    val textMeasurer = rememberTextMeasurer()
    val textScale = LocalTextScale.current
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(800),
        label = "tokenChartAnim",
    )
    LaunchedEffect(Unit) { animProgress = 1f }

    val maxTokens = data.maxOfOrNull { it.tokens.toLong() } ?: 1L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("各会话 token 用量", color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.caption.scaled, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        val accent = AgentBuddyTheme.accent
        val border = AgentBuddyTheme.border
        val labelColor = AgentBuddyTheme.textMuted

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((data.size * 36 + 20).dp),
        ) {
            val barHeight = 20f
            val barSpacing = 36f
            val labelWidth = size.width * 0.3f
            val chartWidth = size.width - labelWidth - 16f

            data.forEachIndexed { index, entry ->
                val y = index * barSpacing + 10f
                val tokens = entry.tokens.toLong()
                val barWidth = (tokens.toFloat() / maxTokens * chartWidth * animatedProgress).coerceAtLeast(2f)

                // Bar
                drawRoundRect(
                    color = accent.copy(alpha = 0.7f),
                    topLeft = Offset(labelWidth + 8f, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                )

                // Label
                val title = entry.threadTitle
                val labelText = if (title.length > 18) title.take(18) + "\u2026" else title
                drawText(
                    textMeasurer = textMeasurer,
                    text = labelText,
                    topLeft = Offset(0f, y + 2f),
                    style = TextStyle(color = labelColor, fontSize = (10f * textScale).sp),
                )

                // Value
                drawText(
                    textMeasurer = textMeasurer,
                    text = formatTokenCount(tokens),
                    topLeft = Offset(labelWidth + barWidth + 12f, y + 2f),
                    style = TextStyle(color = labelColor, fontSize = (9f * textScale).sp),
                )
            }
        }
    }
}

@Composable
internal fun ActivityChart(data: List<AppActivityByDayEntry>) {
    val textMeasurer = rememberTextMeasurer()
    val textScale = LocalTextScale.current
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(800),
        label = "activityChartAnim",
    )
    LaunchedEffect(Unit) { animProgress = 1f }

    val maxCount = data.maxOfOrNull { it.turnCount.toInt() } ?: 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("活动时间线", color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.caption.scaled, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        val accent = AgentBuddyTheme.accent
        val border = AgentBuddyTheme.border
        val labelColor = AgentBuddyTheme.textMuted

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val chartHeight = size.height - 20f
            val barWidth = (size.width / data.size.coerceAtLeast(1)).coerceAtMost(40f)
            val gap = 4f

            // Grid lines
            for (i in 0..3) {
                val y = chartHeight * (1f - i / 4f)
                drawLine(border, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
            }

            data.forEachIndexed { index, entry ->
                val x = index * barWidth + gap
                val count = entry.turnCount.toInt()
                val barH = (count.toFloat() / maxCount * chartHeight * animatedProgress).coerceAtLeast(2f)
                val y = chartHeight - barH

                drawRoundRect(
                    color = accent,
                    topLeft = Offset(x, y),
                    size = Size(barWidth - gap * 2, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                )
            }

            // X-axis labels (first and last)
            if (data.isNotEmpty()) {
                val fmt = java.time.format.DateTimeFormatter.ofPattern("M/d")
                val firstDate = Instant.ofEpochSecond(data.first().dateEpoch)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                drawText(
                    textMeasurer = textMeasurer,
                    text = firstDate.format(fmt),
                    topLeft = Offset(0f, chartHeight + 4f),
                    style = TextStyle(color = labelColor, fontSize = (9f * textScale).sp),
                )
                if (data.size > 1) {
                    val lastDate = Instant.ofEpochSecond(data.last().dateEpoch)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    val lastLabel = lastDate.format(fmt)
                    val measured = textMeasurer.measure(lastLabel, TextStyle(fontSize = (9f * textScale).sp))
                    drawText(
                        textMeasurer = textMeasurer,
                        text = lastLabel,
                        topLeft = Offset(size.width - measured.size.width, chartHeight + 4f),
                        style = TextStyle(color = labelColor, fontSize = (9f * textScale).sp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ModelBreakdownChart(data: List<AppModelUsageEntry>) {
    val textMeasurer = rememberTextMeasurer()
    val textScale = LocalTextScale.current
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(800),
        label = "modelChartAnim",
    )
    LaunchedEffect(Unit) { animProgress = 1f }

    val total = data.sumOf { it.threadCount.toInt() }.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("模型明细", color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.caption.scaled, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        val accent = AgentBuddyTheme.accent
        val labelColor = AgentBuddyTheme.textMuted
        val colors = listOf(
            AgentBuddyTheme.accent,
            AgentBuddyTheme.info,
            AgentBuddyTheme.violet,
            AgentBuddyTheme.amber,
            AgentBuddyTheme.teal,
            AgentBuddyTheme.olive,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((data.size * 32 + 8).dp),
        ) {
            data.forEachIndexed { index, entry ->
                val y = index * 32f + 4f
                val count = entry.threadCount.toInt()
                val ratio = count.toFloat() / total
                val barWidth = size.width * 0.6f * ratio * animatedProgress
                val color = colors[index % colors.size]

                drawRoundRect(
                    color = color.copy(alpha = 0.7f),
                    topLeft = Offset(0f, y),
                    size = Size(barWidth.coerceAtLeast(4f), 20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                )

                val label = "${entry.model} ($count)"
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(barWidth + 8f, y + 2f),
                    style = TextStyle(color = labelColor, fontSize = (10f * textScale).sp),
                )
            }
        }
    }
}

@Composable
internal fun RateLimitGauge(rateLimits: uniffi.codex_mobile_client.RateLimitSnapshot) {
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(1000),
        label = "rateLimitAnim",
    )
    LaunchedEffect(Unit) { animProgress = 1f }

    val primaryPercent = rateLimits.primary?.usedPercent ?: 0
    val secondaryPercent = rateLimits.secondary?.usedPercent ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("速率限制", color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.caption.scaled, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        val accent = AgentBuddyTheme.accent
        val warning = AgentBuddyTheme.warning
        val danger = AgentBuddyTheme.danger
        val border = AgentBuddyTheme.border

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Primary gauge
            GaugeArc(
                label = "主要",
                percent = primaryPercent,
                animatedProgress = animatedProgress,
                accent = accent,
                warning = warning,
                danger = danger,
                border = border,
            )
            // Secondary gauge
            GaugeArc(
                label = "次要",
                percent = secondaryPercent,
                animatedProgress = animatedProgress,
                accent = accent,
                warning = warning,
                danger = danger,
                border = border,
            )
        }
    }
}

@Composable
private fun GaugeArc(
    label: String,
    percent: Int,
    animatedProgress: Float,
    accent: Color,
    warning: Color,
    danger: Color,
    border: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val gaugeColor = when {
            percent > 80 -> danger
            percent > 50 -> warning
            else -> accent
        }

        Canvas(modifier = Modifier.size(80.dp)) {
            val strokeW = 8f
            val arcSize = size.minDimension - strokeW
            val topLeft = Offset(strokeW / 2, strokeW / 2)

            // Background arc
            drawArc(
                color = border,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )

            // Filled arc
            drawArc(
                color = gaugeColor,
                startAngle = 135f,
                sweepAngle = 270f * (percent / 100f) * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$percent%",
            color = AgentBuddyTheme.textPrimary,
            fontSize = 16f.scaled,
            fontWeight = FontWeight.Bold,
            fontFamily = AgentBuddyTheme.monoFont,
        )
        Text(text = label, color = AgentBuddyTheme.textMuted, fontSize = 10f.scaled)
    }
}
