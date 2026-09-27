package com.akashark.agentbuddy.android.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.akashark.agentbuddy.android.state.OpenAIApiKeyStore
import com.akashark.agentbuddy.android.state.VoiceRuntimeController
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.rememberStickyFollowTail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.Account
import uniffi.codex_mobile_client.AppVoiceSessionPhase
import uniffi.codex_mobile_client.AppRefreshAccountRequest
import uniffi.codex_mobile_client.ThreadKey

@Composable
fun RealtimeVoiceScreen(
    threadKey: ThreadKey,
    onBack: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val apiKeyStore = remember(context) { OpenAIApiKeyStore(context.applicationContext) }
    val voiceController = remember { VoiceRuntimeController.shared }
    val activeSession by voiceController.activeVoiceSession.collectAsState()
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val voiceSession = snapshot?.voiceSession
    val phase = voiceSession?.phase ?: AppVoiceSessionPhase.CONNECTING
    val inputLevel = activeSession?.inputLevel ?: 0f
    val outputLevel = activeSession?.outputLevel ?: 0f
    val transcriptEntries = remember(voiceSession?.transcriptEntries) {
        voiceSession?.transcriptEntries?.filter { it.text.trim().isNotEmpty() }.orEmpty()
    }
    val transcriptListState = rememberLazyListState()
    val shouldFollowTail = rememberStickyFollowTail(
        listState = transcriptListState,
        resetKey = threadKey,
        bufferItems = 1,
    )

    var hasCheckedAuth by remember { mutableStateOf(false) }
    var hasStartedRealtime by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyError by remember { mutableStateOf<String?>(null) }
    var isSavingKey by remember { mutableStateOf(false) }
    var hasStoredApiKey by remember { mutableStateOf(apiKeyStore.hasStoredKey()) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
    }

    val server = remember(snapshot, threadKey) {
        snapshot?.servers?.firstOrNull { it.serverId == threadKey.serverId }
    }
    val needsApiKey = hasCheckedAuth && server?.isLocal == true && !hasStoredApiKey
    val phaseColor = voicePhaseColor(phase)
    val transcriptTailSignature = remember(transcriptEntries) {
        var hash = 17
        transcriptEntries.takeLast(4).forEach { entry ->
            hash = 31 * hash + entry.hashCode()
        }
        hash = 31 * hash + transcriptEntries.size
        hash
    }

    LaunchedEffect(threadKey) {
        try {
            appModel.client.refreshAccount(
                threadKey.serverId,
                AppRefreshAccountRequest(refreshToken = false),
            )
            appModel.refreshSnapshot()
            apiKeyError = null
        } catch (e: Exception) {
            apiKeyError = e.localizedMessage
        }
        hasStoredApiKey = apiKeyStore.hasStoredKey()
        hasCheckedAuth = true
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(hasStoredApiKey, hasCheckedAuth, hasMicPermission) {
        if (hasCheckedAuth && hasStoredApiKey && hasMicPermission && !hasStartedRealtime) {
            hasStartedRealtime = true
            voiceController.startVoiceOnThread(appModel, threadKey)
        }
    }

    LaunchedEffect(threadKey, transcriptTailSignature) {
        if (shouldFollowTail && transcriptEntries.isNotEmpty()) {
            transcriptListState.scrollToItem(transcriptEntries.lastIndex)
        }
    }

    DisposableEffect(threadKey, appModel, voiceController) {
        onDispose {
            scope.launch {
                voiceController.stopVoiceSessionIfActive(appModel, threadKey)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgentBuddyTheme.background),
    ) {
        VoiceEdgeGlow(
            intensity = voiceGlowIntensity(
                phase = phase,
                inputLevel = inputLevel,
                outputLevel = outputLevel,
            ),
            phase = phase,
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            TranscriptContent(
                entries = transcriptEntries,
                phase = phase,
                phaseColor = phaseColor,
                inputLevel = inputLevel,
                outputLevel = outputLevel,
                listState = transcriptListState,
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )

            voiceSession?.handoffThreadKey?.let { handoffKey ->
                InlineHandoffView(
                    threadKey = handoffKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            val footerError = voiceSession?.lastError ?: if (!hasMicPermission) "需要麦克风权限" else null
            if (!footerError.isNullOrBlank()) {
                Text(
                    text = footerError,
                    color = AgentBuddyTheme.danger,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            BottomControls(
                isSpeakerOn = isSpeakerOn,
                onToggleSpeaker = {
                    val next = !isSpeakerOn
                    isSpeakerOn = next
                    voiceController.setSpeakerEnabled(next)
                },
                onEnd = {
                    scope.launch {
                        voiceController.stopActiveVoiceSession(appModel)
                        onBack()
                    }
                },
                modifier = Modifier.padding(bottom = 40.dp),
            )
        }

        if (needsApiKey) {
            RealtimeApiKeyPrompt(
                apiKey = apiKey,
                apiKeyError = apiKeyError,
                isSavingKey = isSavingKey,
                onApiKeyChange = { apiKey = it },
                onSave = {
                    val trimmedKey = apiKey.trim()
                    if (trimmedKey.isEmpty() || isSavingKey) return@RealtimeApiKeyPrompt
                    if (server?.isLocal != true) {
                        apiKeyError = "API 密钥仅保存在本地服务器上。"
                        return@RealtimeApiKeyPrompt
                    }

                    isSavingKey = true
                    apiKeyError = null
                    hasStartedRealtime = true

                    scope.launch {
                        try {
                            apiKeyStore.save(trimmedKey)
                            if (server?.account is Account.ApiKey) {
                                appModel.client.logoutAccount(threadKey.serverId)
                            }
                            voiceController.stopActiveVoiceSession(appModel)
                            appModel.restartLocalServer()
                            hasStoredApiKey = apiKeyStore.hasStoredKey()
                            if (!hasStoredApiKey) {
                                hasStartedRealtime = false
                                apiKeyError = "API 密钥未能本地保存。"
                                return@launch
                            }
                            delay(150)
                            voiceController.startVoiceOnThread(appModel, threadKey)
                            apiKey = ""
                        } catch (e: Exception) {
                            hasStartedRealtime = false
                            apiKeyError = e.localizedMessage ?: "保存 API 密钥失败"
                        } finally {
                            isSavingKey = false
                        }
                    }
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
