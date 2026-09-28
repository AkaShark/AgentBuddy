package com.akashark.agentbuddy.android.ui.gallery

import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.PendingApproval

/** Fixture approvals for the DEBUG gallery (no host, no network). */
internal object GalleryApprovalFixtures {
    const val HOST = "MacBook Pro"

    fun approval(
        id: String,
        kind: ApprovalKind,
        command: String? = null,
        path: String? = null,
        grantRoot: String? = null,
        cwd: String? = "/Users/me/Projects/AgentBuddy",
        reason: String? = null,
    ) = PendingApproval(
        id = id,
        serverId = "gallery",
        kind = kind,
        threadId = "gallery-thread",
        turnId = "turn-1",
        itemId = null,
        command = command,
        path = path,
        grantRoot = grantRoot,
        cwd = cwd,
        reason = reason,
    )

    val command = approval(
        id = "cmd",
        kind = ApprovalKind.COMMAND,
        command = "make test",
        reason = "运行测试，确认登录与退出都正常。",
    )

    val longCommand = approval(
        id = "long",
        kind = ApprovalKind.COMMAND,
        command = listOf(
            "set -euo pipefail",
            "cd apps/android",
            "./gradlew :app:testDebugUnitTest --tests 'com.akashark.agentbuddy.android.ui.approvals.*' --info",
            "adb shell settings put system font_scale 2.0",
            "adb shell am start -n com.akashark.agentbuddy.android/.MainActivity --es mint_gallery approvals",
            "sleep 4",
            "adb exec-out screencap -p > /tmp/approvals.png",
            "adb shell settings put system font_scale 1.0",
            "echo done",
        ).joinToString("\n"),
    )

    val fileChange = approval(
        id = "file",
        kind = ApprovalKind.FILE_CHANGE,
        path = "/Users/me/Projects/AgentBuddy/src/login/LoginFlow.kt",
        reason = "修复页面跳转时的闪烁。",
    )

    val fileChangePaths = listOf(
        "/Users/me/Projects/AgentBuddy/src/login/LoginFlow.kt",
        "/Users/me/Projects/AgentBuddy/src/login/SessionRestore.kt",
        "/Users/me/Projects/AgentBuddy/src/login/LoginFlowTest.kt",
    )

    val permissions = approval(
        id = "perm",
        kind = ApprovalKind.PERMISSIONS,
        grantRoot = "/Users/me/Projects",
        reason = "需要读取上级目录里的共享配置。",
        cwd = null,
    )

    val queue = listOf(command, fileChange, permissions)
}
