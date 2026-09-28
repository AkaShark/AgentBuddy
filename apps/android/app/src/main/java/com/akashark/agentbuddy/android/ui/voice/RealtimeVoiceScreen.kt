package com.akashark.agentbuddy.android.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.akashark.agentbuddy.android.state.OpenAIApiKeyStore
import com.akashark.agentbuddy.android.state.VoiceRuntimeController
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

    Box(modifier = Modifier.fillMaxSize()) {
        RealtimeVoiceContent(
            state = RealtimeVoiceUiState(
                phase = phase,
                transcript = transcriptEntries,
                inputLevel = inputLevel,
                outputLevel = outputLevel,
                errorMessage = voiceSession?.lastError
                    ?: if (!hasMicPermission) "需要麦克风权限才能进行实时语音。" else null,
                needsMicPermission = !hasMicPermission && voiceSession?.lastError.isNullOrBlank(),
                isSpeakerOn = isSpeakerOn,
            ),
            listState = transcriptListState,
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
            onRequestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            handoff = voiceSession?.handoffThreadKey?.let { handoffKey ->
                { InlineHandoffView(threadKey = handoffKey, modifier = Modifier.fillMaxWidth()) }
            },
        )

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
