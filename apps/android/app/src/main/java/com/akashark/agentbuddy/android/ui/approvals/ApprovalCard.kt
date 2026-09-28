package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.ApprovalDecisionValue
import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.PendingApproval

/** 1-based position of the shown request plus paging callbacks. */
data class ApprovalPosition(
    val index: Int,
    val total: Int,
    val onPrevious: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
)

/**
 * Inline confirmation card (radius 20, warning surface). It shows exactly
 * what will run and where, offers 「拒绝」/「允许一次」 as the decision, keeps
 * the session-wide grant as a quiet secondary choice behind a confirmation,
 * and blocks repeat taps while a decision is in flight.
 */
@Composable
fun ApprovalCard(
    approval: PendingApproval,
    hostName: String?,
    submittingDecision: ApprovalDecisionValue?,
    failure: ApprovalFailure?,
    onDecision: (ApprovalDecisionValue) -> Unit,
    modifier: Modifier = Modifier,
    position: ApprovalPosition? = null,
    filePaths: List<String> = emptyList(),
    formatPath: (String) -> String = ::abbreviateHomePath,
    onViewDiff: (() -> Unit)? = null,
    sessionConfirmInitiallyOpen: Boolean = false,
) {
    val isSubmitting = submittingDecision != null
    var showSessionConfirm by remember(approval.id) { mutableStateOf(sessionConfirmInitiallyOpen) }
    val decide: (ApprovalDecisionValue) -> Unit = { decision -> if (!isSubmitting) onDecision(decision) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.WARNING, shape = BuddyShapes.confirmCard, padding = BuddySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        if (position != null && position.total > 1) {
            ApprovalPager(position)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = AgentBuddyTheme.warning,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = ApprovalCopy.title(approval.kind),
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.warning,
                modifier = Modifier.semantics { heading() },
            )
        }
        ApprovalDetails(
            approval = approval,
            hostName = hostName,
            filePaths = filePaths,
            formatPath = formatPath,
            onViewDiff = onViewDiff,
        )
        failure?.let {
            BuddyBanner(
                tone = BuddyBannerTone.DANGER,
                message = ApprovalCopy.failure(it.message),
                actionTitle = if (isSubmitting) null else ApprovalCopy.RETRY,
                onAction = { decide(it.decision) },
            )
        }
        ApprovalDecisionButtons(
            submittingDecision = submittingDecision,
            onDeny = { decide(ApprovalDecisionValue.DECLINE) },
            onAllowOnce = { decide(ApprovalDecisionValue.ACCEPT) },
        )
        ApprovalSecondaryActions(
            enabled = !isSubmitting,
            isSessionSubmitting = submittingDecision == ApprovalDecisionValue.ACCEPT_FOR_SESSION,
            onAllowForSession = { showSessionConfirm = true },
            onStopTask = if (approval.kind.offersStopTask) ({ decide(ApprovalDecisionValue.CANCEL) }) else null,
        )
    }

    if (showSessionConfirm) {
        ApprovalSessionConfirmDialog(
            kind = approval.kind,
            onConfirm = {
                showSessionConfirm = false
                decide(ApprovalDecisionValue.ACCEPT_FOR_SESSION)
            },
            onDismiss = { showSessionConfirm = false },
        )
    }
}

@Composable
private fun ApprovalPager(position: ApprovalPosition) {
    BuddyChromeTypeLimit {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ApprovalCopy.position(position.index, position.total),
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = AgentBuddyTheme.warning,
                modifier = Modifier.weight(1f),
            )
            BuddyIconButton(
                icon = Icons.Outlined.ChevronLeft,
                contentDescription = "上一个请求",
                onClick = { position.onPrevious?.invoke() },
                enabled = position.onPrevious != null,
                tint = AgentBuddyTheme.warning,
            )
            BuddyIconButton(
                icon = Icons.Outlined.ChevronRight,
                contentDescription = "下一个请求",
                onClick = { position.onNext?.invoke() },
                enabled = position.onNext != null,
                tint = AgentBuddyTheme.warning,
            )
        }
    }
}

/**
 * 「拒绝」 and 「允许一次」 side by side when both labels fit on one line;
 * stacked (primary first) at large text sizes instead of wrapping.
 */
@Composable
private fun ApprovalDecisionButtons(
    submittingDecision: ApprovalDecisionValue?,
    onDeny: () -> Unit,
    onAllowOnce: () -> Unit,
) {
    val isSubmitting = submittingDecision != null
    val deny: @Composable (Modifier) -> Unit = { modifier ->
        BuddyButton(
            text = ApprovalCopy.DENY,
            onClick = onDeny,
            modifier = modifier,
            kind = BuddyButtonKind.SECONDARY,
            enabled = !isSubmitting,
            isLoading = submittingDecision == ApprovalDecisionValue.DECLINE,
            fullWidth = false,
        )
    }
    val allow: @Composable (Modifier) -> Unit = { modifier ->
        BuddyButton(
            text = ApprovalCopy.ALLOW_ONCE,
            onClick = onAllowOnce,
            modifier = modifier,
            kind = BuddyButtonKind.PRIMARY,
            trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward,
            enabled = !isSubmitting,
            isLoading = submittingDecision == ApprovalDecisionValue.ACCEPT,
            fullWidth = false,
        )
    }
    AdaptiveButtonPair(secondary = deny, primary = allow)
}

/**
 * Whether 「改为停止任务」 means what it says for this kind. The host has no
 * cancel for a permissions request: Rust answers CANCEL there with an empty
 * grant, exactly like 「拒绝」, so the option would promise a stop that never
 * happens.
 */
internal val ApprovalKind.offersStopTask: Boolean
    get() = this == ApprovalKind.COMMAND || this == ApprovalKind.FILE_CHANGE

/** Session grant entry and, when [onStopTask] is set, the 「…」 menu with 「改为停止任务」. */
@Composable
private fun ApprovalSecondaryActions(
    enabled: Boolean,
    isSessionSubmitting: Boolean,
    onAllowForSession: () -> Unit,
    onStopTask: (() -> Unit)?,
) {
    BuddyChromeTypeLimit {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ApprovalInlineAction(
                title = if (isSessionSubmitting) "正在发送…" else ApprovalCopy.SESSION_ENTRY,
                icon = null,
                onClick = onAllowForSession,
                enabled = enabled,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            if (onStopTask != null) StopTaskMenu(enabled = enabled, onStopTask = onStopTask)
        }
    }
}

@Composable
private fun StopTaskMenu(enabled: Boolean, onStopTask: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        BuddyIconButton(
            icon = Icons.Outlined.MoreHoriz,
            contentDescription = ApprovalCopy.MORE_CHOICES,
            onClick = { showMenu = true },
            enabled = enabled,
            tint = AgentBuddyTheme.warning,
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        ApprovalCopy.STOP_TASK_INSTEAD,
                        style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                        color = AgentBuddyTheme.danger,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Outlined.StopCircle, contentDescription = null, tint = AgentBuddyTheme.danger)
                },
                onClick = {
                    showMenu = false
                    onStopTask()
                },
            )
        }
    }
}
