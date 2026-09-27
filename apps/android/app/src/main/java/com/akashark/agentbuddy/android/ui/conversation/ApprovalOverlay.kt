package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.runtime.Composable
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.approvals.PendingApprovalBannerHost
import uniffi.codex_mobile_client.AppStore
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.PendingUserInputRequest

/**
 * App-level approval surface. No longer a blocking full-screen scrim: it shows
 * a compact top banner for requests whose conversation is not on screen
 * (tapping opens that conversation, closing only hides the banner). Requests
 * for the open conversation render inline above its composer
 * (`ConversationApprovalStack`), and user-input requests render only there
 * too, so nothing is shown twice. The signature is kept for the app shell.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ApprovalOverlay(
    approvals: List<PendingApproval>,
    userInputs: List<PendingUserInputRequest>,
    appStore: AppStore,
    onDismissUserInput: ((String) -> Unit)? = null,
) {
    PendingApprovalBannerHost(
        appModel = LocalAppModel.current,
        approvals = approvals,
    )
}
