package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppOperationStatus
import uniffi.codex_mobile_client.HydratedCommandActionKind
import uniffi.codex_mobile_client.HydratedConversationItemContent

private data class ExplorationDisplayEntry(
    val id: String,
    val label: String,
    val isInProgress: Boolean,
)

/**
 * Renders an exploration group as a collapsible summary.
 */
@Composable
fun ExplorationGroupRow(
    group: ExplorationGroup,
    showsCollapsedPreview: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val entries = remember(group.items) { group.explorationEntries() }
    val isActive = remember(entries) { entries.any { it.isInProgress } }
    val previewScrollState = rememberScrollState()
    val reduceMotion = buddyReduceMotion
    // The header shimmer only runs while exploring, and never with reduced motion.
    val shimmerProgress = if (isActive && !reduceMotion) rememberExplorationShimmerProgress() else 0f
    val labelStyle = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal)
    val density = LocalDensity.current
    val labelLineHeight = with(density) { labelStyle.fontSize.toDp() } * LabelLineHeightRatio
    val bulletSize = with(density) { labelStyle.fontSize.toDp() } * 0.4f
    val bulletTopPadding = (labelLineHeight - bulletSize) / 2
    val previewHeight = labelLineHeight * 3.6f + 18.dp

    LaunchedEffect(entries, previewScrollState.maxValue, expanded, showsCollapsedPreview) {
        if (expanded || !showsCollapsedPreview || previewScrollState.maxValue <= 0) return@LaunchedEffect
        previewScrollState.animateScrollTo(previewScrollState.maxValue)
    }

    LaunchedEffect(showsCollapsedPreview) {
        if (!showsCollapsedPreview) {
            expanded = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .timelineDetailCard()
            .animateContentSize(animationSpec = BuddyMotion.STATE.spec(reduceMotion)),
    ) {
        TimelineCardHeader(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            leading = {
                TimelineStatusGlyph(
                    status = if (isActive) AppOperationStatus.IN_PROGRESS else AppOperationStatus.COMPLETED,
                )
            },
        ) {
            Text(
                text = remember(entries, isActive) {
                    group.explorationSummaryText(isActive = isActive)
                },
                style = labelStyle,
                color = AgentBuddyTheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .explorationHeaderShimmer(active = isActive && !reduceMotion, progress = shimmerProgress),
            )
        }

        if (!expanded && showsCollapsedPreview && entries.isNotEmpty()) {
            val previewFill = timelineCodeFill(nested = true)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = BuddySpacing.md, end = BuddySpacing.md, bottom = BuddySpacing.sm)
                    .heightIn(min = 56.dp, max = previewHeight)
                    .clip(timelineCodeShape(nested = true))
                    .background(previewFill)
                    .drawWithContent {
                        drawContent()
                        // Soft top fade over entries that scrolled away.
                        val fadeHeight = BuddySpacing.lg.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(previewFill, previewFill, Color.Transparent),
                                endY = fadeHeight,
                            ),
                            size = Size(size.width, fadeHeight),
                        )
                    }
                    .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs)
                    .verticalScroll(previewScrollState),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
            ) {
                entries.forEach { entry ->
                    ExplorationEntryLine(
                        entry = entry,
                        style = labelStyle,
                        bulletSize = bulletSize,
                        bulletTopPadding = bulletTopPadding,
                        singleLine = true,
                    )
                }
            }
        } else if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = BuddySpacing.md, end = BuddySpacing.md, bottom = BuddySpacing.sm),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            ) {
                for (entry in entries) {
                    ExplorationEntryLine(
                        entry = entry,
                        style = labelStyle,
                        bulletSize = bulletSize,
                        bulletTopPadding = bulletTopPadding,
                        singleLine = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplorationEntryLine(
    entry: ExplorationDisplayEntry,
    style: TextStyle,
    bulletSize: Dp,
    bulletTopPadding: Dp,
    singleLine: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(
            modifier = Modifier
                .padding(top = bulletTopPadding)
                .size(bulletSize)
                .background(
                    color = if (entry.isInProgress) AgentBuddyTheme.warning else AgentBuddyTheme.textSecondary,
                    shape = CircleShape,
                ),
        )
        Text(
            text = entry.label,
            style = style,
            color = AgentBuddyTheme.textSecondary,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun rememberExplorationShimmerProgress(): Float {
    val progress by rememberInfiniteTransition(label = "exploration-header-shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "exploration-header-shimmer-progress",
    )
    return progress
}

private const val LabelLineHeightRatio = 20f / 14f

private fun ExplorationGroup.explorationEntries(): List<ExplorationDisplayEntry> {
    return items.flatMap { item ->
        val content = item.content as? HydratedConversationItemContent.CommandExecution ?: return@flatMap emptyList()
        val data = content.v1
        val isInProgress = data.status == AppOperationStatus.PENDING || data.status == AppOperationStatus.IN_PROGRESS
        if (data.actions.isEmpty()) {
            listOf(
                ExplorationDisplayEntry(
                    id = "${item.id}-command",
                    label = data.command,
                    isInProgress = isInProgress,
                ),
            )
        } else {
            data.actions.mapIndexed { index, action ->
                ExplorationDisplayEntry(
                    id = "${item.id}-$index",
                    label = explorationActionLabel(action, data.command),
                    isInProgress = isInProgress,
                )
            }
        }
    }
}

private fun ExplorationGroup.explorationSummaryText(isActive: Boolean): String {
    var readCount = 0
    var searchCount = 0
    var listingCount = 0
    var fallbackCount = 0

    items.forEach { item ->
        val content = item.content as? HydratedConversationItemContent.CommandExecution ?: return@forEach
        val data = content.v1
        if (data.actions.isEmpty()) {
            fallbackCount += 1
            return@forEach
        }
        data.actions.forEach { action ->
            when (action.kind) {
                HydratedCommandActionKind.READ -> readCount += 1
                HydratedCommandActionKind.SEARCH -> searchCount += 1
                HydratedCommandActionKind.LIST_FILES -> listingCount += 1
                HydratedCommandActionKind.UNKNOWN -> fallbackCount += 1
            }
        }
    }

    val parts = buildList {
        if (readCount > 0) add("$readCount 个文件")
        if (searchCount > 0) add("$searchCount 次搜索")
        if (listingCount > 0) add("$listingCount 次列出目录")
        if (fallbackCount > 0) add("$fallbackCount 个步骤")
    }

    val prefix = if (isActive) "正在检查" else "已检查"
    return if (parts.isEmpty()) {
        val count = explorationEntries().size
        "$prefix $count 个步骤"
    } else {
        "$prefix ${parts.joinToString("、")}"
    }
}

private fun explorationActionLabel(
    action: uniffi.codex_mobile_client.HydratedCommandActionData,
    fallback: String,
): String {
    val suffix = explorationCommandSuffix(action)
    return when (action.kind) {
        HydratedCommandActionKind.READ -> {
            action.path?.let { "读取 ${workspaceTitle(it)}$suffix" } ?: fallback
        }

        HydratedCommandActionKind.SEARCH -> {
            when {
                !action.query.isNullOrBlank() && !action.path.isNullOrBlank() ->
                    "在 ${workspaceTitle(action.path!!)} 中搜索 ${action.query}$suffix"
                !action.query.isNullOrBlank() ->
                    "搜索 ${action.query}$suffix"
                else -> fallback
            }
        }

        HydratedCommandActionKind.LIST_FILES -> {
            action.path?.let { "列出 ${workspaceTitle(it)} 中的文件$suffix" } ?: fallback
        }

        HydratedCommandActionKind.UNKNOWN -> fallback
    }
}

private fun explorationCommandSuffix(
    action: uniffi.codex_mobile_client.HydratedCommandActionData,
): String {
    val command = action.command.trim()
    if (!command.endsWith(")")) return ""
    val start = command.lastIndexOf(" (")
    return if (start >= 0) command.substring(start) else ""
}

private fun workspaceTitle(path: String): String {
    val normalized = path.replace('\\', '/').trimEnd('/')
    val lastSegment = normalized.substringAfterLast('/', normalized)
    return if (lastSegment.isBlank()) path else lastSegment
}

private fun Modifier.explorationHeaderShimmer(active: Boolean, progress: Float): Modifier {
    if (!active) return this
    return drawWithContent {
        drawContent()
        val width = size.width
        val shimmerWidth = width * 0.35f
        val startX = (width + shimmerWidth) * progress - shimmerWidth
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.3f),
                    Color.Transparent,
                ),
                startX = startX,
                endX = startX + shimmerWidth,
            ),
            topLeft = Offset.Zero,
            size = size,
            blendMode = BlendMode.SrcAtop,
        )
    }
}
