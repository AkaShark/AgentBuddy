package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.SshAuthMethod
import com.akashark.agentbuddy.android.ui.discovery.AlleycatPairCallbacks
import com.akashark.agentbuddy.android.ui.discovery.AlleycatPairContent
import com.akashark.agentbuddy.android.ui.discovery.DiscoveryChooser
import com.akashark.agentbuddy.android.ui.discovery.ManualConnectionMode
import com.akashark.agentbuddy.android.ui.discovery.ManualEntryContent
import com.akashark.agentbuddy.android.ui.discovery.QrScannerContent
import com.akashark.agentbuddy.android.ui.discovery.SlingshotComputersContent
import com.akashark.agentbuddy.android.ui.discovery.SshAgentPickerContent
import com.akashark.agentbuddy.android.ui.discovery.SshLoginContent
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing

/** Sheet content rendered on the page background, below the status bar. */
@Composable
internal fun GallerySheetFrame(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = BuddySpacing.md),
        content = content,
    )
}

private val noPairActions = AlleycatPairCallbacks(
    onCancel = {},
    onScan = {},
    onOpenSettings = {},
    onTogglePaste = {},
    onPasteJsonChange = {},
    onPasteFromClipboard = {},
    onParse = {},
    onDisplayNameChange = {},
    onToggleAllAgents = {},
    onAgentToggle = { _, _ -> },
    onConnect = {},
)

@Composable
internal fun GalleryDiscoveryPage(wakingHostName: String? = null) {
    GallerySheetFrame {
        DiscoveryChooser(
            onPairWithAgentBuddy = {},
            onConnectedComputers = {},
            onSshOrCodexUrl = {},
            onClose = {},
            wakingHostName = wakingHostName,
        )
    }
}

@Composable
internal fun GalleryPairPage(empty: Boolean = false) {
    GallerySheetFrame {
        AlleycatPairContent(
            state = if (empty) GalleryConnectFixtures.pairEmptyState else GalleryConnectFixtures.pairState,
            actions = noPairActions,
        )
    }
}

@Composable
internal fun GalleryPairScanPage() {
    GallerySheetFrame {
        QrScannerContent(onCancel = {}) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCode2,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(120.dp),
                )
            }
        }
    }
}

@Composable
internal fun GallerySshLoginPage() {
    GallerySheetFrame {
        SshLoginContent(
            serverName = "Studio Mac mini",
            hostDisplay = "192.168.1.24:2222",
            username = "sharker",
            onUsernameChange = {},
            authMethod = SshAuthMethod.PASSWORD,
            onAuthMethodChange = {},
            password = "hunter22",
            onPasswordChange = {},
            isPasswordVisible = false,
            onTogglePasswordVisible = {},
            privateKey = "",
            onPrivateKeyChange = {},
            passphrase = "",
            onPassphraseChange = {},
            unlockMacosKeychain = true,
            onUnlockMacosKeychainChange = {},
            rememberCredentials = true,
            onRememberCredentialsChange = {},
            isConnecting = false,
            canConnect = true,
            errorMessage = "Authentication failed (password)",
            onCancel = {},
            onConnect = {},
        )
    }
}

@Composable
internal fun GalleryManualEntryPage() {
    GallerySheetFrame {
        ManualEntryContent(
            mode = ManualConnectionMode.SSH,
            onModeChange = {},
            codexUrl = "",
            onCodexUrlChange = {},
            host = "studio.local",
            onHostChange = {},
            sshPort = "22x",
            onSshPortChange = {},
            wakeMac = "",
            onWakeMacChange = {},
            errorMessage = "SSH 端口必须是有效数字。",
            onCancel = {},
            onSubmit = {},
        )
    }
}

@Composable
internal fun GallerySshAgentsPage() {
    GallerySheetFrame {
        SshAgentPickerContent(
            serverName = "Studio Mac mini",
            host = "studio.local",
            options = GalleryConnectFixtures.sshAgents,
            isConnecting = false,
            canConnect = true,
            errorMessage = null,
            onToggle = { _, _ -> },
            onConnect = {},
            onUseCodex = {},
            onCancel = {},
        )
    }
}

@Composable
internal fun GallerySlingshotPage() {
    GallerySheetFrame {
        SlingshotComputersContent(
            environments = GalleryConnectFixtures.slingshotEnvironments,
            loading = false,
            error = null,
            onCancel = {},
            onRefresh = {},
            onSelect = {},
        )
    }
}
