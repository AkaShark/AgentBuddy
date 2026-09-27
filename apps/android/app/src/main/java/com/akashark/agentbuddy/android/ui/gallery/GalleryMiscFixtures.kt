package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Storage
import com.akashark.agentbuddy.android.ui.terminal.TerminalBackendOption
import com.akashark.agentbuddy.android.ui.voice.RealtimeVoiceUiState
import uniffi.codex_mobile_client.AppVoiceSessionPhase
import uniffi.codex_mobile_client.AppVoiceSpeaker
import uniffi.codex_mobile_client.AppVoiceTranscriptEntry
import uniffi.codex_mobile_client.SavedApp
import uniffi.codex_mobile_client.TerminalBackendKind

/** Fixture data for the Saved Apps, realtime voice and terminal gallery pages. */
internal object GalleryMiscFixtures {
    private fun app(id: String, title: String, minutesAgo: Long) =
        SavedApp(
            id = id,
            appId = "app-$id",
            title = title,
            width = 360.0,
            height = 480.0,
            schemaVersion = 1u,
            createdAtMs = System.currentTimeMillis() - 10 * 86_400_000L,
            updatedAtMs = System.currentTimeMillis() - minutesAgo * 60_000L,
            originThreadId = "thread-$id",
        )

    val apps: List<SavedApp> =
        listOf(
            app("1", "番茄钟", 12),
            app("2", "Weekly Budget", 180),
            app("3", "发布清单", 3 * 1440),
            app("4", "Color Picker Lab", 9 * 1440),
        )

    private fun line(id: String, speaker: AppVoiceSpeaker, text: String) =
        AppVoiceTranscriptEntry(itemId = id, speaker = speaker, text = text)

    val voice =
        RealtimeVoiceUiState(
            phase = AppVoiceSessionPhase.LISTENING,
            transcript =
                listOf(
                    line("1", AppVoiceSpeaker.USER, "帮我看看登录页为什么会闪一下"),
                    line("2", AppVoiceSpeaker.ASSISTANT, "我查了一下，状态恢复时触发了两次跳转。要我直接修吗？"),
                    line("3", AppVoiceSpeaker.USER, "修吧，顺便跑一下测试"),
                    line("4", AppVoiceSpeaker.ASSISTANT, "好的，我先改跳转逻辑，然后运行 make test。"),
                ),
            inputLevel = 0.45f,
            outputLevel = 0.2f,
            errorMessage = null,
            needsMicPermission = false,
            isSpeakerOn = true,
        )

    val voiceConnecting =
        voice.copy(
            phase = AppVoiceSessionPhase.CONNECTING,
            transcript = emptyList(),
            errorMessage = "需要麦克风权限才能进行实时语音。",
            needsMicPermission = true,
            isSpeakerOn = false,
        )

    val terminalBackends: List<TerminalBackendOption> =
        listOf(
            TerminalBackendOption(
                id = "alleycat-mbp",
                title = "MacBook Pro",
                runningLabel = "远程 Shell",
                icon = Icons.Outlined.Storage,
                alleycatNodeId = "mbp",
                supportsResize = true,
                backend = TerminalBackendKind.RemoteAlleycat(nodeId = "mbp", token = "fixture", relay = null, shell = null),
            ),
            TerminalBackendOption(
                id = "local-proot",
                title = "本地 Alpine",
                runningLabel = "本地 Alpine",
                icon = Icons.Outlined.PhoneIphone,
                supportsResize = true,
                backend = TerminalBackendKind.LocalProot(null),
            ),
        )

    const val terminalOutput =
        "dev@MacBook-Pro AgentBuddy % git status\n" +
            "On branch misc-screens\n" +
            "nothing to commit, working tree clean\n" +
            "dev@MacBook-Pro AgentBuddy % make test\n" +
            "==> Running Rust tests...\n" +
            "test result: ok. 214 passed; 0 failed\n" +
            "dev@MacBook-Pro AgentBuddy % "
}
