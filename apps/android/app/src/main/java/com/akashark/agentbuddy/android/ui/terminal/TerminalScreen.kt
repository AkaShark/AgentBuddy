package com.akashark.agentbuddy.android.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.core.bridge.GhosttyRendererBridge
import com.akashark.agentbuddy.android.state.ActiveTerminalRegistry
import com.akashark.agentbuddy.android.state.AndroidProotBootstrap
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.TerminalSessionController
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.discovery.SshHostKeyChangedDialog
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    cwd: String? = null,
    preferredAlleycatNodeId: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val controller = remember { TerminalSessionController(scope) }
    val prootState by AndroidProotBootstrap.state.collectAsState()
    val rendererStatus = remember { GhosttyRendererBridge.status() }
    var nativeRendererAvailable by remember {
        mutableStateOf(rendererStatus.canCreateAndroidSurface)
    }
    val backendOptions = remember(cwd, prootState) { loadBackendOptions(context, cwd, prootState) }
    var selectedBackendId by remember(preferredAlleycatNodeId) { mutableStateOf<String?>(null) }
    val selectedBackend = backendOptions.firstOrNull { it.id == selectedBackendId }
        ?: backendOptions.firstOrNull()
    var terminalGridSize by remember { mutableStateOf(TerminalGridSize(cols = 80, rows = 24)) }
    var showConfigSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TerminalConfigPrefs.initialize(context)
    }

    val currentTerminalConfig = remember(
        TerminalConfigPrefs.fontSize,
        TerminalConfigPrefs.theme,
        TerminalConfigPrefs.cursorBlink,
    ) {
        TerminalConfigPrefs.currentConfig()
    }

    LaunchedEffect(backendOptions, preferredAlleycatNodeId) {
        if (backendOptions.none { it.id == selectedBackendId }) {
            selectedBackendId = initialBackendId(backendOptions, preferredAlleycatNodeId)
        }
    }

    LaunchedEffect(selectedBackend?.id) {
        selectedBackend?.let { controller.switchBackend(it.backend) }
    }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalChrome.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        TerminalHeader(
            phase = controller.phase,
            exitCode = controller.exitCode,
            selectedBackend = selectedBackend,
            backendOptions = backendOptions,
            onSelectBackend = { option ->
                selectedBackendId = option.id
            },
            onBack = onBack,
            onConfigClick = { showConfigSheet = true },
        )

        controller.errorMessage?.let { message -> TerminalErrorLine(message) }
        controller.sshTrustChallenge?.let { challenge ->
            TerminalTrustButton(
                fingerprint = challenge.fingerprint,
                onClick = controller::trustUnknownSshHostAndRetry,
            )
        }
        controller.sshHostKeyChange?.let { change ->
            SshHostKeyChangedDialog(
                mismatch = change.mismatch,
                onDismiss = controller::dismissHostKeyChange,
                onConfirm = controller::retryAfterHostKeyChange,
            )
        }

        TerminalOutputPane(
            controller = controller,
            rendererStatus = rendererStatus,
            nativeRendererAvailable = nativeRendererAvailable,
            onNativeRendererUnavailable = { nativeRendererAvailable = false },
            prootState = prootState,
            selectedBackend = selectedBackend,
            terminalGridSize = terminalGridSize,
            onTerminalGridSizeChanged = { terminalGridSize = it },
            density = density,
            terminalConfig = currentTerminalConfig,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        val appSnapshot by AppModel.shared.snapshot.collectAsState()
        val activeThreadKey = appSnapshot?.activeThread
        TerminalAccessoryRow(
            controller = controller,
            canSendToAssistant = activeThreadKey != null &&
                (nativeRendererAvailable || controller.output.isNotEmpty()),
            onSendToAssistant = {
                val key = activeThreadKey ?: return@TerminalAccessoryRow
                val selection = ActiveTerminalRegistry.readSelection()
                val fallback = controller.output.trim()
                val payload = (selection?.takeIf { it.isNotEmpty() } ?: fallback)
                if (payload.isEmpty()) return@TerminalAccessoryRow
                scope.launch {
                    runCatching {
                        ActiveTerminalRegistry.sendTextToAssistant(
                            store = AppModel.shared.store,
                            threadKey = key,
                            selection = payload,
                        )
                    }
                }
            },
        )
    }

    if (showConfigSheet) {
        TerminalConfigSheet(
            context = context,
            onDismiss = { showConfigSheet = false },
        )
    }
}

@Composable
private fun TerminalErrorLine(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalChrome.background)
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = TerminalChrome.danger,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Text(
            text = message,
            style = buddyTextStyle(BuddyTextStyle.CODE),
            color = TerminalChrome.danger,
        )
    }
}

/** Trust an unknown SSH host key: brand pill with the fingerprint, 48dp tall target. */
@Composable
private fun TerminalTrustButton(fingerprint: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = BuddySpacing.md)
            .heightIn(min = BuddySize.minHitTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "信任 $fingerprint",
            style = buddyTextStyle(BuddyTextStyle.CODE),
            color = TerminalChrome.onBrand,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(TerminalChrome.brand, CircleShape)
                .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs),
        )
    }
}
