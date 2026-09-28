package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.PathDisplay
import com.akashark.agentbuddy.android.ui.conversation.SessionDiffSection
import com.akashark.agentbuddy.android.ui.conversation.SessionDiffSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import uniffi.codex_mobile_client.ApprovalDecisionValue
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.HydratedFileChangeEntryData
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.ThreadKey

/** Share of the screen height the inline card may take before it scrolls inside. */
private const val APPROVAL_MAX_SCREEN_FRACTION = 0.45f

/**
 * Approvals for the open conversation, above the composer. One request at a
 * time with 「第 N 个，共 M 个」 and paging; the latest outcome replaces the
 * card once the queue for this thread is empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationApprovalStack(
    appModel: AppModel,
    threadKey: ThreadKey,
    items: List<HydratedConversationItem>,
    modifier: Modifier = Modifier,
) {
    val coordinator = rememberApprovalCoordinator(appModel)
    RegisterApprovalStackVisible(threadKey)
    val snapshot by appModel.snapshot.collectAsState()
    val context = LocalContext.current
    val pending = remember(snapshot?.pendingApprovals, threadKey) {
        snapshot?.pendingApprovals.orEmpty().filter { it.isAnswerableOnPhone && it.belongsTo(threadKey) }
    }
    val server = snapshot?.servers?.firstOrNull { it.serverId == threadKey.serverId }
    val isLocal = server?.isLocal == true
    var selectedId by remember(threadKey) { mutableStateOf<String?>(null) }
    var lastIndex by remember(threadKey) { mutableIntStateOf(0) }
    val index = resolveApprovalIndex(pending.map { it.id }, selectedId, lastIndex)
    var diffSections by remember(threadKey) { mutableStateOf<List<SessionDiffSection>?>(null) }

    ApprovalStackContent(
        pending = pending,
        selectedIndex = index,
        hostName = hostDisplayName(server?.displayName),
        submitting = coordinator.lockedDecisions,
        failures = coordinator.failures,
        outcome = coordinator.outcome(threadKey)?.kind,
        onSelect = { next ->
            pending.getOrNull(next)?.let {
                selectedId = it.id
                lastIndex = next
            }
        },
        onDecision = { approval, decision -> submitApprovalDecision(appModel, coordinator, approval, decision) },
        onDismissOutcome = { coordinator.dismissOutcome(threadKey) },
        modifier = modifier,
        filePathsFor = { approval -> fileChangesFor(approval, items).map { it.path } },
        onViewDiffFor = { approval ->
            val sections = fileChangesFor(approval, items).mapNotNull { it.toDiffSection() }
            if (sections.isEmpty()) null else ({ diffSections = sections })
        },
        formatPath = { path -> PathDisplay.display(path, isLocal, context) },
    )

    diffSections?.let { sections ->
        BuddyBottomSheet(onDismissRequest = { diffSections = null }) {
            SessionDiffSheet(sections = sections, onDismiss = { diffSections = null })
        }
    }
}

private sealed interface StackSlot {
    val key: String

    data class Card(val approval: PendingApproval, val index: Int, val total: Int) : StackSlot {
        override val key: String get() = "card:${approval.id}"
    }

    data class Outcome(val kind: ApprovalOutcomeKind) : StackSlot {
        override val key: String get() = "outcome:$kind"
    }

    data object Empty : StackSlot {
        override val key: String get() = "empty"
    }
}

/** Stateless stack (also used by the DEBUG gallery). */
@Composable
fun ApprovalStackContent(
    pending: List<PendingApproval>,
    selectedIndex: Int,
    hostName: String?,
    submitting: Map<String, ApprovalDecisionValue>,
    failures: Map<String, ApprovalFailure>,
    outcome: ApprovalOutcomeKind?,
    onSelect: (Int) -> Unit,
    onDecision: (PendingApproval, ApprovalDecisionValue) -> Unit,
    onDismissOutcome: () -> Unit,
    modifier: Modifier = Modifier,
    filePathsFor: (PendingApproval) -> List<String> = { emptyList() },
    onViewDiffFor: (PendingApproval) -> (() -> Unit)? = { null },
    formatPath: (String) -> String = ::abbreviateHomePath,
    maxCardHeight: Dp = (LocalConfiguration.current.screenHeightDp * APPROVAL_MAX_SCREEN_FRACTION).dp,
) {
    val slot: StackSlot = when {
        pending.isNotEmpty() -> {
            val index = selectedIndex.coerceIn(0, pending.lastIndex)
            StackSlot.Card(pending[index], index, pending.size)
        }
        outcome != null -> StackSlot.Outcome(outcome)
        else -> StackSlot.Empty
    }
    val reduceMotion = buddyReduceMotion
    AnimatedContent(
        targetState = slot,
        contentKey = { it.key },
        transitionSpec = {
            fadeIn(BuddyMotion.STATE.spec(reduceMotion)) togetherWith fadeOut(BuddyMotion.STATE.spec(reduceMotion))
        },
        modifier = modifier.fillMaxWidth(),
        label = "approvalStack",
    ) { current ->
        when (current) {
            is StackSlot.Card -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xxs)
                    .heightIn(max = maxCardHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                val approval = current.approval
                ApprovalCard(
                    approval = approval,
                    hostName = hostName,
                    submittingDecision = submitting[approval.id],
                    failure = failures[approval.id],
                    onDecision = { decision -> onDecision(approval, decision) },
                    position = ApprovalPosition(
                        index = current.index + 1,
                        total = current.total,
                        onPrevious = if (current.index > 0) ({ onSelect(current.index - 1) }) else null,
                        onNext = if (current.index < current.total - 1) ({ onSelect(current.index + 1) }) else null,
                    ),
                    filePaths = filePathsFor(approval),
                    formatPath = formatPath,
                    onViewDiff = onViewDiffFor(approval),
                )
            }
            is StackSlot.Outcome -> ApprovalResultCard(
                kind = current.kind,
                onDismiss = onDismissOutcome,
                modifier = Modifier.padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xxs),
            )
            StackSlot.Empty -> Box(Modifier.fillMaxWidth())
        }
    }
}

/** Index of the request to show: the selected one while it is pending, otherwise the nearest to where it was. */
internal fun resolveApprovalIndex(pendingIds: List<String>, selectedId: String?, lastIndex: Int): Int {
    if (pendingIds.isEmpty()) return 0
    val found = selectedId?.let { pendingIds.indexOf(it) } ?: -1
    return if (found >= 0) found else lastIndex.coerceIn(0, pendingIds.lastIndex)
}

/** File changes of the timeline item this request is about, if the timeline has it. */
private fun fileChangesFor(
    approval: PendingApproval,
    items: List<HydratedConversationItem>,
): List<HydratedFileChangeEntryData> {
    val itemId = approval.itemId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val content = items.firstOrNull { it.id == itemId }?.content as? HydratedConversationItemContent.FileChange
    return content?.v1?.changes.orEmpty()
}

private fun HydratedFileChangeEntryData.toDiffSection(): SessionDiffSection? {
    val text = diff.trim()
    if (text.isEmpty()) return null
    val title = path.trimEnd('/').substringAfterLast('/').ifBlank { path }
    return SessionDiffSection(title = title, diff = text)
}
