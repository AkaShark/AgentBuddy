package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.ApprovalKind

/** Asks before a session-wide grant; the scope is spelled out, nothing is pre-selected. */
@Composable
internal fun ApprovalSessionConfirmDialog(
    kind: ApprovalKind,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ApprovalSessionConfirmPanel(
            kind = kind,
            onConfirm = onConfirm,
            onCancel = onDismiss,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

/** Dialog body, also rendered inline by the DEBUG gallery. */
@Composable
fun ApprovalSessionConfirmPanel(
    kind: ApprovalKind,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.confirmCard, padding = BuddySpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        Text(
            text = ApprovalCopy.SESSION_CONFIRM_TITLE,
            style = buddyTextStyle(BuddyTextStyle.HEADING),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = ApprovalCopy.sessionScopeExplanation(kind),
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textSecondary,
        )
        AdaptiveButtonPair(
            secondary = { modifier ->
                BuddyButton(
                    text = ApprovalCopy.CANCEL,
                    onClick = onCancel,
                    modifier = modifier,
                    kind = BuddyButtonKind.SECONDARY,
                    fullWidth = false,
                )
            },
            primary = { modifier ->
                BuddyButton(
                    text = ApprovalCopy.SESSION_CONFIRM_ACTION,
                    onClick = onConfirm,
                    modifier = modifier,
                    kind = BuddyButtonKind.PRIMARY,
                    fullWidth = false,
                )
            },
        )
    }
}
