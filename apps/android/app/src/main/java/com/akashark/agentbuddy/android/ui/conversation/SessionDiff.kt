package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent

internal data class PinnedContextData(
    val todoProgress: String?,
    val diffSummary: DiffSummary?,
    val diffSections: List<SessionDiffSection>,
)

internal data class DiffSummary(
    val additions: Int,
    val deletions: Int,
) {
    val hasChanges: Boolean
        get() = additions > 0 || deletions > 0
}

internal data class SessionDiffSection(
    val title: String,
    val diff: String,
) {
    val id: String = "$title|${diff.hashCode()}"
    val summary: DiffSummary = summarizeDiff(diff)
}

private const val MAX_STICKY_DIFF_SECTIONS = 8
private const val MAX_STICKY_DIFF_CHARACTERS = 20_000

private fun summarizeDiff(diff: String): DiffSummary {
    var additions = 0
    var deletions = 0
    diff.lineSequence().forEach { line ->
        when {
            line.startsWith("+") && !line.startsWith("+++") -> additions += 1
            line.startsWith("-") && !line.startsWith("---") -> deletions += 1
        }
    }
    return DiffSummary(additions = additions, deletions = deletions)
}

internal fun conversationPinnedContext(
    items: List<HydratedConversationItem>,
): PinnedContextData? {
    var todoProgress: String? = null
    val rawDiffSections = mutableListOf<SessionDiffSection>()
    for (i in items.indices.reversed()) {
        when (val c = items[i].content) {
            is HydratedConversationItemContent.TodoList -> {
                if (todoProgress == null) {
                    val done = c.v1.steps.count {
                        it.status == uniffi.codex_mobile_client.HydratedPlanStepStatus.COMPLETED
                    }
                    todoProgress = "$done/${c.v1.steps.size}"
                }
            }
            is HydratedConversationItemContent.FileChange -> {
                c.v1.changes.forEach { change ->
                    val diff = change.diff.trim()
                    if (diff.isBlank()) return@forEach
                    rawDiffSections += SessionDiffSection(
                        title = workspaceTitleCompat(change.path),
                        diff = diff,
                    )
                }
            }
            is HydratedConversationItemContent.TurnDiff -> {
                rawDiffSections += parseSessionDiffSections(c.v1.diff)
            }
            else -> {}
        }
    }
    val diffSections = mergeSessionDiffSections(rawDiffSections)
    val diffSummary = diffSections
        .takeIf { it.isNotEmpty() }
        ?.fold(DiffSummary(additions = 0, deletions = 0)) { acc, section ->
            DiffSummary(
                additions = acc.additions + section.summary.additions,
                deletions = acc.deletions + section.summary.deletions,
            )
        }
    return if (todoProgress != null || diffSummary != null) {
        PinnedContextData(
            todoProgress = todoProgress,
            diffSummary = diffSummary,
            diffSections = diffSections,
        )
    } else {
        null
    }
}

private fun parseSessionDiffSections(diff: String): List<SessionDiffSection> {
    val normalized = diff.trim()
    if (normalized.isBlank()) return emptyList()

    val lines = normalized.lines()
    val splitIndices = lines.mapIndexedNotNull { index, line ->
        if (line.startsWith("diff --git ")) index else null
    }

    if (splitIndices.isEmpty()) {
        return listOf(
            SessionDiffSection(
                title = diffSectionTitle(normalized),
                diff = normalized,
            ),
        )
    }

    return splitIndices.mapIndexedNotNull { offset, start ->
        val end = if (offset + 1 < splitIndices.size) splitIndices[offset + 1] else lines.size
        val chunk = lines.subList(start, end).joinToString("\n").trim()
        if (chunk.isBlank()) null else SessionDiffSection(title = diffSectionTitle(chunk), diff = chunk)
    }
}

private fun mergeSessionDiffSections(sections: List<SessionDiffSection>): List<SessionDiffSection> {
    val orderedTitles = mutableListOf<String>()
    val mergedByTitle = linkedMapOf<String, String>()
    val passthrough = mutableListOf<SessionDiffSection>()

    sections.forEach { section ->
        val title = section.title.trim()
        if (title.isBlank()) {
            passthrough += section
            return@forEach
        }

        val existing = mergedByTitle[title]
        if (existing == null) {
            orderedTitles += title
            mergedByTitle[title] = section.diff
        } else {
            mergedByTitle[title] = "$existing\n\n${section.diff}"
        }
    }

    return orderedTitles.mapNotNull { title ->
        mergedByTitle[title]?.let { SessionDiffSection(title = title, diff = it) }
    } + passthrough
}

private fun diffSectionTitle(diff: String): String {
    diff.lineSequence().forEach { line ->
        when {
            line.startsWith("diff --git ") -> {
                return stripDiffPathPrefix(line.substringAfterLast(' '))
            }
            line.startsWith("+++ ") -> {
                val path = line.removePrefix("+++ ")
                if (path != "/dev/null") return stripDiffPathPrefix(path)
            }
            line.startsWith("--- ") -> {
                val path = line.removePrefix("--- ")
                if (path != "/dev/null") return stripDiffPathPrefix(path)
            }
        }
    }
    return ""
}

private fun stripDiffPathPrefix(path: String): String {
    return when {
        path.startsWith("a/") || path.startsWith("b/") -> path.drop(2)
        else -> path
    }
}

private fun workspaceTitleCompat(path: String): String {
    return path.trimEnd('/').substringAfterLast('/').ifBlank { path }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionDiffSheet(
    sections: List<SessionDiffSection>,
    onDismiss: () -> Unit,
) {
    var collapsedSectionIds by remember(sections) {
        mutableStateOf(sections.mapTo(linkedSetOf()) { it.id })
    }
    val totalSummary = remember(sections) {
        sections.fold(DiffSummary(additions = 0, deletions = 0)) { acc, section ->
            DiffSummary(
                additions = acc.additions + section.summary.additions,
                deletions = acc.deletions + section.summary.deletions,
            )
        }
    }
    val useStickyHeaders = remember(sections) {
        sections.size <= MAX_STICKY_DIFF_SECTIONS &&
            sections.sumOf { it.diff.length } <= MAX_STICKY_DIFF_CHARACTERS
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+${totalSummary.additions}",
                    color = AgentBuddyTheme.success,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BerkeleyMono,
                )
                Text(
                    text = "-${totalSummary.deletions}",
                    color = AgentBuddyTheme.danger,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BerkeleyMono,
                )
            }
        }

        sections.forEach { section ->
            if (section.title.isNotEmpty()) {
                if (useStickyHeaders) {
                    stickyHeader(key = "header-${section.id}") {
                        SessionDiffSectionHeader(
                            section = section,
                            expanded = !collapsedSectionIds.contains(section.id),
                        ) {
                            collapsedSectionIds =
                                linkedSetOf<String>().apply {
                                    addAll(collapsedSectionIds)
                                    if (contains(section.id)) {
                                        remove(section.id)
                                    } else {
                                        add(section.id)
                                    }
                                }
                        }
                    }
                } else {
                    item(key = "header-${section.id}") {
                        SessionDiffSectionHeader(
                            section = section,
                            expanded = !collapsedSectionIds.contains(section.id),
                        ) {
                            collapsedSectionIds =
                                linkedSetOf<String>().apply {
                                    addAll(collapsedSectionIds)
                                    if (contains(section.id)) {
                                        remove(section.id)
                                    } else {
                                        add(section.id)
                                    }
                                }
                        }
                    }
                }
            }

            item(key = "body-${section.id}") {
                if (section.title.isEmpty() || !collapsedSectionIds.contains(section.id)) {
                    SyntaxHighlightedDiffBlock(
                        diff = section.diff,
                        titleHint = section.title.ifEmpty { null },
                        fontSize = AgentBuddyTextStyle.caption.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("完成", color = AgentBuddyTheme.accent)
                }
            }
        }
    }
}

@Composable
private fun SessionDiffSectionHeader(
    section: SessionDiffSection,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.background)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.title.uppercase(),
            color = AgentBuddyTheme.textSecondary,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "+${section.summary.additions}",
            color = AgentBuddyTheme.success,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            fontWeight = FontWeight.SemiBold,
            fontFamily = BerkeleyMono,
        )
        Text(
            text = "-${section.summary.deletions}",
            color = AgentBuddyTheme.danger,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            fontWeight = FontWeight.SemiBold,
            fontFamily = BerkeleyMono,
        )
        Text(
            text = if (expanded) "▲" else "▼",
            color = AgentBuddyTheme.textMuted,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            fontWeight = FontWeight.Bold,
        )
    }
}
