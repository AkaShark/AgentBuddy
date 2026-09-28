package com.akashark.agentbuddy.android.ui.gallery

import com.akashark.agentbuddy.android.ui.discovery.AlleycatPairViewState
import com.akashark.agentbuddy.android.ui.discovery.DiscoveryAgentOption
import uniffi.codex_mobile_client.AppAlleycatPairPayload
import uniffi.codex_mobile_client.AppSlingshotEnvironment

/** Fixture data for the add-host gallery pages (no runtime, no network). */
internal object GalleryConnectFixtures {
    val pairPayload = AppAlleycatPairPayload(
        v = 1u,
        nodeId = "b4f1c2d9e8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9",
        token = "fixture-token",
        relay = "https://relay.agentbuddy.app",
        hostName = "MacBook Pro",
    )

    val pairAgents = listOf(
        DiscoveryAgentOption(
            key = "codex", kind = "codex", title = "Codex", detail = "websocket",
            isBeta = false, selectable = true, selected = true,
        ),
        DiscoveryAgentOption(
            key = "claude", kind = "claude", title = "Claude Code", detail = "jsonl",
            isBeta = false, selectable = true, selected = true,
        ),
        DiscoveryAgentOption(
            key = "pi", kind = "pi", title = "Pi", detail = "jsonl",
            isBeta = true, selectable = true, selected = false,
        ),
        DiscoveryAgentOption(
            key = "amp", kind = "amp", title = "Amp", detail = "jsonl",
            isBeta = true, selectable = false, selected = false, unavailableNote = "不可用",
        ),
    )

    val pairState = AlleycatPairViewState(
        params = pairPayload,
        displayName = "MacBook Pro",
        agentOptions = pairAgents,
        hasAvailableAgents = true,
        allAgentsSelected = false,
        isLoadingAgents = false,
        parseError = null,
        agentError = null,
        connectError = null,
        isConnecting = false,
        canConnect = true,
        cameraDenied = false,
        showPaste = false,
        pasteJson = "",
    )

    val pairEmptyState = pairState.copy(
        params = null,
        displayName = "",
        agentOptions = emptyList(),
        hasAvailableAgents = false,
        canConnect = false,
        cameraDenied = true,
        showPaste = true,
        pasteJson = "{\"v\":1,\"node_id\":\"b4f1…\"",
        parseError = "配对数据无效：缺少 token 字段",
    )

    val sshAgents = listOf(
        DiscoveryAgentOption(
            key = "claude", kind = "claude", title = "Claude", detail = "可用",
            isBeta = false, selectable = true, selected = true,
        ),
        DiscoveryAgentOption(
            key = "opencode", kind = "opencode", title = "Opencode", detail = "可用",
            isBeta = true, selectable = true, selected = false,
        ),
        DiscoveryAgentOption(
            key = "pi", kind = "pi", title = "Pi", detail = "缺少 CLI",
            isBeta = true, selectable = false, selected = false,
        ),
        DiscoveryAgentOption(
            key = "codex", kind = "codex", title = "Codex", detail = "不能通过 SSH 桥接启动",
            isBeta = false, selectable = false, selected = false,
        ),
    )

    val slingshotEnvironments = listOf(
        slingshot("env-1", "MacBook Pro", online = true, busy = false, os = "macos", arch = "arm64", version = "0.44.0"),
        slingshot("env-2", "Studio Mac mini", online = true, busy = true, os = "darwin", arch = "arm64", version = "0.43.2"),
        slingshot("env-3", "build-server", online = false, busy = false, os = "linux", arch = "x86_64", version = null),
    )

    private fun slingshot(
        id: String,
        name: String,
        online: Boolean,
        busy: Boolean,
        os: String,
        arch: String?,
        version: String?,
    ) = AppSlingshotEnvironment(
        id = id,
        connectionUrl = "slingshot://$id",
        displayName = name,
        rawDisplayName = name,
        name = name,
        hostName = "$name.local",
        online = online,
        busy = busy,
        operatingSystem = os,
        architecture = arch,
        appServerVersion = version,
        lastSeenAt = null,
    )
}
