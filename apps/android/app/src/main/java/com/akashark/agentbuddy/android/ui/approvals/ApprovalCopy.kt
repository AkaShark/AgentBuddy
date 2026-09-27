package com.akashark.agentbuddy.android.ui.approvals

import uniffi.codex_mobile_client.ApprovalKind

/**
 * Wording per approval kind, kept in one place so the inline card, the
 * banner and the result card describe the same request the same way.
 * Matches the iOS zh-Hans strings (`ApprovalCopy.swift`).
 */
object ApprovalCopy {
    fun title(kind: ApprovalKind): String =
        when (kind) {
            ApprovalKind.COMMAND -> "允许运行这条命令？"
            ApprovalKind.FILE_CHANGE -> "允许修改这些文件？"
            ApprovalKind.PERMISSIONS -> "允许扩大访问权限？"
            ApprovalKind.MCP_ELICITATION -> "工具需要你的输入"
        }

    fun question(kind: ApprovalKind, hostName: String?): String {
        val host = hostName?.takeIf { it.isNotBlank() } ?: "你的电脑"
        return when (kind) {
            ApprovalKind.COMMAND -> "搭子想在 $host 上运行一条命令。"
            ApprovalKind.FILE_CHANGE -> "搭子想修改 $host 上的文件。"
            ApprovalKind.PERMISSIONS -> "搭子请求在 $host 上获得更大的访问范围。"
            ApprovalKind.MCP_ELICITATION -> "请在 $host 上处理这个请求。"
        }
    }

    fun sessionScopeExplanation(kind: ApprovalKind): String =
        when (kind) {
            ApprovalKind.COMMAND -> "本次会话结束前，这个任务里的同类命令将不再询问。你仍可随时停止任务。"
            ApprovalKind.FILE_CHANGE -> "本次会话结束前，这个任务里的文件修改将不再询问。你仍可随时停止任务。"
            ApprovalKind.PERMISSIONS, ApprovalKind.MCP_ELICITATION -> "该访问权限在本任务的剩余会话内保持授予。"
        }

    fun outcomeTitle(kind: ApprovalOutcomeKind): String =
        when (kind) {
            ApprovalOutcomeKind.ALLOWED_ONCE -> "已允许本次操作"
            ApprovalOutcomeKind.ALLOWED_FOR_SESSION -> "已在本会话允许"
            ApprovalOutcomeKind.DENIED -> "已拒绝"
            ApprovalOutcomeKind.ABORTED -> "已请求停止任务"
            ApprovalOutcomeKind.RESOLVED_ELSEWHERE -> "已在别处处理"
        }

    fun outcomeDetail(kind: ApprovalOutcomeKind): String =
        when (kind) {
            ApprovalOutcomeKind.ALLOWED_ONCE, ApprovalOutcomeKind.ALLOWED_FOR_SESSION ->
                "搭子正在继续。完成后，你会在这里看到结果。"
            ApprovalOutcomeKind.DENIED -> "已告诉搭子不要这样做，它会换一种方式。"
            ApprovalOutcomeKind.ABORTED -> "主机正在停止这个任务。"
            ApprovalOutcomeKind.RESOLVED_ELSEWHERE -> "这个请求已在其他设备上处理，或已不再等待。"
        }

    /** 「第 N 个，共 M 个」 (1-based). */
    fun position(index: Int, total: Int): String = "第 $index 个，共 $total 个"

    fun pendingCount(count: Int): String = "有 $count 个请求等待你确认"

    fun failure(message: String): String = "决定没有发送成功：$message"

    const val DENY = "拒绝"
    const val ALLOW_ONCE = "允许一次"
    const val SESSION_ENTRY = "本会话都允许…"
    const val SESSION_CONFIRM_TITLE = "本会话都允许？"
    const val SESSION_CONFIRM_ACTION = "本会话都允许"
    const val CANCEL = "取消"
    const val RETRY = "重试"
    const val MORE_CHOICES = "更多选项"
    const val STOP_TASK_INSTEAD = "改为停止任务"
    const val VIEW_DIFF = "查看差异"
}

/** Server display name for UI; the stored "This Device" sentinel reads 「本设备」. */
fun hostDisplayName(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return if (trimmed == "This Device") "本设备" else trimmed
}
