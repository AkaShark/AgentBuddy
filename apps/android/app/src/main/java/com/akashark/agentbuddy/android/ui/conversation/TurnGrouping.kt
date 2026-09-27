package com.akashark.agentbuddy.android.ui.conversation

import uniffi.codex_mobile_client.HydratedCommandActionKind
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.AppMessagePhase

/**
 * A group of conversation items belonging to the same turn.
 */
data class TranscriptTurn(
    val id: String,
    val turnId: String?,
    val items: List<HydratedConversationItem>,
    val isActiveTurn: Boolean,
    val isCollapsedByDefault: Boolean,
) {
    val userPrompt: String?
        get() = items.firstOrNull { it.content is HydratedConversationItemContent.User }
            ?.let { (it.content as HydratedConversationItemContent.User).v1.text }

    val assistantSnippet: String?
        get() = (
            items.firstOrNull {
                when (val content = it.content) {
                    is HydratedConversationItemContent.Assistant ->
                        content.v1.phase == AppMessagePhase.FINAL_ANSWER
                    is HydratedConversationItemContent.CodeReview -> true
                    else -> false
                }
            }
                ?: items.lastOrNull {
                    it.content is HydratedConversationItemContent.Assistant ||
                        it.content is HydratedConversationItemContent.CodeReview
                }
            )?.let {
                when (val content = it.content) {
                    is HydratedConversationItemContent.Assistant -> content.v1.text
                    is HydratedConversationItemContent.CodeReview ->
                        content.v1.findings.firstOrNull()?.title ?: "代码审查"
                    else -> null
                }
            }
            ?.take(120)

    val commandCount: Int
        get() = items.count { it.content is HydratedConversationItemContent.CommandExecution }

    val fileChangeCount: Int
        get() = items.count { it.content is HydratedConversationItemContent.FileChange }

    val totalDurationMs: Long
        get() = items.sumOf {
            when (val c = it.content) {
                is HydratedConversationItemContent.CommandExecution -> c.v1.durationMs ?: 0L
                else -> 0L
            }
        }
}

/**
 * Groups a flat list of hydrated items into UI turns with the same boundary rules
 * as iOS: explicit user turn boundaries split turns, and streaming tails merge
 * back into the live turn instead of rendering as separate groups.
 */
fun buildTranscriptTurns(
    items: List<HydratedConversationItem>,
    isStreaming: Boolean,
    expandedRecentTurnCount: Int,
): List<TranscriptTurn> {
    if (items.isEmpty()) return emptyList()

    val groupedItems = mergeConsecutiveExplorationGroups(
        mergeTrailingStreamingGroups(groupItems(items), isStreaming),
    )
    val collapseBoundary = maxOf(0, groupedItems.size - expandedRecentTurnCount)
    val lastIndex = groupedItems.lastIndex

    return groupedItems.mapIndexed { index, turnItems ->
        val turnId = turnItems.firstNotNullOfOrNull { it.sourceTurnId }
        TranscriptTurn(
            id = turnIdentifier(turnItems, index),
            turnId = turnId,
            items = turnItems,
            isActiveTurn = isStreaming && index == lastIndex,
            isCollapsedByDefault = index < collapseBoundary,
        )
    }
}

private fun groupItems(items: List<HydratedConversationItem>): List<List<HydratedConversationItem>> {
    val groups = mutableListOf<List<HydratedConversationItem>>()
    var current = mutableListOf<HydratedConversationItem>()
    var currentSourceTurnId: String? = null
    for (item in items) {
        val startsNewTurn =
            current.isNotEmpty() && (
                item.isFromUserTurnBoundary ||
                    (
                        item.sourceTurnId != null &&
                            currentSourceTurnId != null &&
                            item.sourceTurnId != currentSourceTurnId
                        )
                )

        if (startsNewTurn) {
            groups += current.toList()
            current = mutableListOf()
        }
        current += item

        currentSourceTurnId = when {
            currentSourceTurnId == null -> item.sourceTurnId
            current.size == 1 -> current.firstOrNull()?.sourceTurnId
            else -> currentSourceTurnId
        }
    }

    if (current.isNotEmpty()) {
        groups += current.toList()
    }

    return groups
}

private fun mergeTrailingStreamingGroups(
    groups: List<List<HydratedConversationItem>>,
    isStreaming: Boolean,
): List<List<HydratedConversationItem>> {
    if (!isStreaming || groups.size <= 1) return groups

    val liveTurnStartIndex = groups.indexOfLast { containsLiveTurnBoundary(it) }
    if (liveTurnStartIndex == -1 || liveTurnStartIndex >= groups.lastIndex) return groups

    val mergedLiveTurn = groups.subList(liveTurnStartIndex, groups.size).flatten()
    return buildList {
        addAll(groups.subList(0, liveTurnStartIndex))
        add(mergedLiveTurn)
    }
}

private fun mergeConsecutiveExplorationGroups(
    groups: List<List<HydratedConversationItem>>,
): List<List<HydratedConversationItem>> {
    val merged = mutableListOf<List<HydratedConversationItem>>()
    val explorationBuffer = mutableListOf<HydratedConversationItem>()

    fun flushExplorationBuffer() {
        if (explorationBuffer.isEmpty()) return
        merged += explorationBuffer.toList()
        explorationBuffer.clear()
    }

    groups.forEach { group ->
        if (group.isExplorationGroup()) {
            explorationBuffer += group
        } else {
            flushExplorationBuffer()
            merged += group
        }
    }

    flushExplorationBuffer()
    return merged
}

private fun containsLiveTurnBoundary(items: List<HydratedConversationItem>): Boolean {
    return items.any { item ->
        item.isFromUserTurnBoundary || item.content is HydratedConversationItemContent.User
    }
}

private fun List<HydratedConversationItem>.isExplorationGroup(): Boolean {
    return isNotEmpty() && all { item ->
        val content = item.content as? HydratedConversationItemContent.CommandExecution
        content?.v1?.isPureExploration() == true
    }
}

private fun turnIdentifier(items: List<HydratedConversationItem>, ordinal: Int): String {
    val first = items.firstOrNull() ?: return "turn-$ordinal"
    val sourceTurnId = items.firstNotNullOfOrNull { it.sourceTurnId }
    return if (sourceTurnId != null) {
        "turn-$sourceTurnId-${first.id}"
    } else {
        "turn-${first.id}"
    }
}

/**
 * Groups consecutive CommandExecution items with empty/null output
 * into a collapsed "Explored N locations" row.
 */
data class ExplorationGroup(
    val id: String,
    val items: List<HydratedConversationItem>,
)

/**
 * Detects exploration groups in a list of items within a single turn.
 * Returns a mixed list of either individual items or exploration groups.
 */
sealed class TimelineEntry {
    data class Single(val item: HydratedConversationItem) : TimelineEntry()
    data class Exploration(val group: ExplorationGroup) : TimelineEntry()
}

fun buildTimelineEntries(
    items: List<HydratedConversationItem>,
    isLive: Boolean,
): List<TimelineEntry> {
    val result = mutableListOf<TimelineEntry>()
    var explorationRun = mutableListOf<HydratedConversationItem>()

    fun flushExploration() {
        if (explorationRun.isEmpty()) return
        if (isLive || explorationRun.size > 1) {
            val id = explorationRun.firstOrNull()?.id ?: "exploration"
            result.add(TimelineEntry.Exploration(ExplorationGroup(id = "exploration-$id", items = explorationRun.toList())))
        } else {
            explorationRun.forEach { result.add(TimelineEntry.Single(it)) }
        }
        explorationRun = mutableListOf()
    }

    for (item in items) {
        val content = item.content
        if (content is HydratedConversationItemContent.CommandExecution &&
            content.v1.isPureExploration()
        ) {
            explorationRun.add(item)
        } else {
            flushExploration()
            result.add(TimelineEntry.Single(item))
        }
    }
    flushExploration()
    return result
}

private fun uniffi.codex_mobile_client.HydratedCommandExecutionData.isPureExploration(): Boolean {
    if (actions.isEmpty()) return false
    return actions.all { action ->
        when (action.kind) {
            HydratedCommandActionKind.READ,
            HydratedCommandActionKind.SEARCH,
            HydratedCommandActionKind.LIST_FILES,
            -> true

            HydratedCommandActionKind.UNKNOWN -> false
        }
    }
}
