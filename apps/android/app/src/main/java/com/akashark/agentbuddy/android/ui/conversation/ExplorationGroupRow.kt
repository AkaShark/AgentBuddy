package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalTextScale
import com.akashark.agentbuddy.android.ui.scaled
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
    val textScale = LocalTextScale.current
    var expanded by remember { mutableStateOf(false) }
    val entries = remember(group.items) { group.explorationEntries() }
    val isActive = remember(entries) { entries.any { it.isInProgress } }
    val previewScrollState = rememberScrollState()
    val shimmerProgress by rememberInfiniteTransition(label = "exploration-header-shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "exploration-header-shimmer-progress",
    )
    val bulletSize = (6f * textScale).dp
    val bulletTopPadding = (5f * textScale).dp
    val previewHeight = (AgentBuddyTextStyle.caption * textScale * 3.6f).dp + 18.dp

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
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "▼" else "▶",
                color = AgentBuddyTheme.textMuted,
                fontSize = AgentBuddyTextStyle.caption.scaled,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = remember(entries, isActive) {
                    group.explorationSummaryText(isActive = isActive)
                },
                color = if (isActive) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                modifier = Modifier
                    .weight(1f)
                    .explorationHeaderShimmer(active = isActive, progress = shimmerProgress),
            )
        }

        if (!expanded && showsCollapsedPreview && entries.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 4.dp)
                    .heightIn(min = 56.dp, max = previewHeight)
                    .background(
                        AgentBuddyTheme.surface.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .verticalScroll(previewScrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Spacer(
                            modifier = Modifier
                                .padding(top = bulletTopPadding)
                                .width(bulletSize)
                                .height(bulletSize)
                                .background(
                                    color = if (entry.isInProgress) {
                                        AgentBuddyTheme.warning
                                    } else {
                                        AgentBuddyTheme.textMuted
                                    },
                                    shape = RoundedCornerShape(percent = 50),
                                ),
                        )
                        Text(
                            text = entry.label,
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = AgentBuddyTextStyle.caption.scaled,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else if (expanded) {
            for (entry in entries) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Spacer(
                        modifier = Modifier
                            .padding(top = bulletTopPadding)
                            .width(bulletSize)
                            .height(bulletSize)
                            .background(
                                color = if (entry.isInProgress) {
                                    AgentBuddyTheme.warning
                                } else {
                                    AgentBuddyTheme.textMuted
                                },
                                shape = RoundedCornerShape(percent = 50),
                            ),
                    )
                    Text(
                        text = entry.label,
                        color = AgentBuddyTheme.textSecondary,
                        fontSize = AgentBuddyTextStyle.caption.scaled,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

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
        if (readCount > 0) add("$readCount ${if (readCount == 1) "file" else "files"}")
        if (searchCount > 0) add("$searchCount ${if (searchCount == 1) "search" else "searches"}")
        if (listingCount > 0) add("$listingCount ${if (listingCount == 1) "listing" else "listings"}")
        if (fallbackCount > 0) add("$fallbackCount ${if (fallbackCount == 1) "step" else "steps"}")
    }

    val prefix = if (isActive) "Exploring" else "Explored"
    return if (parts.isEmpty()) {
        val count = explorationEntries().size
        "$prefix $count exploration ${if (count == 1) "step" else "steps"}"
    } else {
        "$prefix ${parts.joinToString(", ")}"
    }
}

private fun explorationActionLabel(
    action: uniffi.codex_mobile_client.HydratedCommandActionData,
    fallback: String,
): String {
    val suffix = explorationCommandSuffix(action)
    return when (action.kind) {
        HydratedCommandActionKind.READ -> {
            action.path?.let { "Read ${workspaceTitle(it)}$suffix" } ?: fallback
        }

        HydratedCommandActionKind.SEARCH -> {
            when {
                !action.query.isNullOrBlank() && !action.path.isNullOrBlank() ->
                    "Searched for ${action.query} in ${workspaceTitle(action.path!!)}$suffix"
                !action.query.isNullOrBlank() ->
                    "Searched for ${action.query}$suffix"
                else -> fallback
            }
        }

        HydratedCommandActionKind.LIST_FILES -> {
            action.path?.let { "Listed files in ${workspaceTitle(it)}$suffix" } ?: fallback
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
