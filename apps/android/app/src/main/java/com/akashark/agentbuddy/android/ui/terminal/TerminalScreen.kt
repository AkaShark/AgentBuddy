package com.akashark.agentbuddy.android.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.core.bridge.GhosttyRendererBridge
import com.akashark.agentbuddy.android.state.ActiveTerminalRegistry
import com.akashark.agentbuddy.android.state.AndroidProotBootstrap
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.TerminalSessionController
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
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
            .background(Color.Black)
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

        controller.errorMessage?.let { message ->
            Text(
                text = message,
                color = AgentBuddyTheme.danger,
                fontFamily = AgentBuddyTheme.monoFont,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        controller.sshTrustChallenge?.let { challenge ->
            TextButton(
                onClick = controller::trustUnknownSshHostAndRetry,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(34.dp),
            ) {
                Text(
                    text = "信任 ${challenge.fingerprint}",
                    color = Color.Black,
                    fontFamily = AgentBuddyTheme.monoFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .background(AgentBuddyTheme.accent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
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
