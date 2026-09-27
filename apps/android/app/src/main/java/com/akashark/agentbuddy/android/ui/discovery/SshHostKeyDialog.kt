package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppSshHostKeyMismatch
import uniffi.codex_mobile_client.AppSshHostKeyMismatchKind

/**
 * An SSH connect refused because the server's host key no longer matches the
 * key pinned on this device, or because that saved key could not be read
 * (typed Rust [AppSshHostKeyMismatch]). Carries what is needed to retry the
 * same login after the user trusts the new key or forgets the unreadable one.
 */
internal data class SshHostKeyChangePrompt(
    val server: SavedServer,
    val credential: SavedSshCredential,
    val rememberCredentials: Boolean,
    val mismatch: AppSshHostKeyMismatch,
)

/**
 * Confirmation for a changed SSH host key. "信任新密钥" pins exactly the
 * displayed fingerprint before calling [onConfirm], so the caller's retry
 * only succeeds if the server still presents that key (otherwise it prompts
 * again). When the saved key could not be read
 * ([AppSshHostKeyMismatchKind.TRUST_STORE_UNAVAILABLE]), "忘记已保存的主机密钥"
 * removes it before [onConfirm], so the retry treats the host as new.
 */
@Composable
internal fun SshHostKeyChangedDialog(
    mismatch: AppSshHostKeyMismatch,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val port = mismatch.port.toInt()
    val hostDisplay = if (port == 22) mismatch.host else "${mismatch.host}:$port"
    val savedKeyUnreadable = mismatch.kind == AppSshHostKeyMismatchKind.TRUST_STORE_UNAVAILABLE
    MintAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Outlined.GppMaybe,
        iconTint = AgentBuddyTheme.danger,
        title = if (savedKeyUnreadable) "无法读取已保存的 SSH 主机密钥" else "SSH 主机密钥已变更",
        message = if (savedKeyUnreadable) {
            "本设备为 $hostDisplay 保存的 SSH 主机密钥无法读取，因此连接已被拒绝。\n\n" +
                "忘记已保存的密钥即可重新连接；服务器当前的密钥将作为新密钥保存。"
        } else {
            "$hostDisplay 的 SSH 主机密钥与本设备保存的不一致。服务器重装后会出现这种情况，" +
                "但也可能意味着有人正在拦截连接。\n\n仅在你预期到此变更时才信任新密钥。"
        },
        confirmTitle = if (savedKeyUnreadable) "忘记已保存的主机密钥" else "信任新密钥",
        confirmKind = BuddyButtonKind.DESTRUCTIVE,
        onConfirm = {
            if (savedKeyUnreadable) {
                appModel.sshTrustStore.unpin(mismatch.host, mismatch.port)
            } else {
                appModel.sshTrustStore.pin(mismatch.host, mismatch.port, mismatch.fingerprint)
            }
            onConfirm()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.control)
                .padding(BuddySpacing.sm),
        ) {
            Text(
                text = if (savedKeyUnreadable) "服务器指纹" else "新指纹",
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
            )
            Text(
                text = mismatch.fingerprint,
                style = buddyTextStyle(BuddyTextStyle.CODE),
                color = AgentBuddyTheme.textPrimary,
            )
        }
    }
}
