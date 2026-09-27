package com.akashark.agentbuddy.android.ui.conversation

import com.akashark.agentbuddy.android.state.AppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppThreadGoal
import uniffi.codex_mobile_client.AppThreadGoalClearRequest
import uniffi.codex_mobile_client.AppThreadGoalGetRequest
import uniffi.codex_mobile_client.AppThreadGoalSetRequest
import uniffi.codex_mobile_client.AppThreadGoalStatus

data class GoalCardActions(
    val togglePause: () -> Unit,
    val markComplete: () -> Unit,
    val setObjective: (String) -> Unit,
    val setBudget: (Long?) -> Unit,
    val clear: () -> Unit,
) {
    companion object {
        val Noop = GoalCardActions(
            togglePause = {},
            markComplete = {},
            setObjective = {},
            setBudget = {},
            clear = {},
        )
    }
}

internal fun composerGoalCardActions(
    current: AppThreadGoal,
    appModel: AppModel,
    threadKey: ThreadKey,
    scope: CoroutineScope,
    onSlashError: ((String) -> Unit)?,
): GoalCardActions =
    GoalCardActions(
        togglePause = {
            scope.launch {
                val next = when (current.status) {
                    AppThreadGoalStatus.ACTIVE -> AppThreadGoalStatus.PAUSED
                    AppThreadGoalStatus.PAUSED,
                    AppThreadGoalStatus.BLOCKED,
                    AppThreadGoalStatus.USAGE_LIMITED,
                    AppThreadGoalStatus.BUDGET_LIMITED -> AppThreadGoalStatus.ACTIVE
                    AppThreadGoalStatus.COMPLETE -> return@launch
                }
                runCatching {
                    appModel.client.setThreadGoal(
                        threadKey.serverId,
                        AppThreadGoalSetRequest(
                            threadId = threadKey.threadId,
                            objective = null,
                            status = next,
                            tokenBudget = null,
                        ),
                    )
                }.onFailure { onSlashError?.invoke(it.message ?: "更新目标失败") }
            }
        },
        markComplete = {
            scope.launch {
                runCatching {
                    appModel.client.setThreadGoal(
                        threadKey.serverId,
                        AppThreadGoalSetRequest(
                            threadId = threadKey.threadId,
                            objective = null,
                            status = AppThreadGoalStatus.COMPLETE,
                            tokenBudget = null,
                        ),
                    )
                }.onFailure { onSlashError?.invoke(it.message ?: "更新目标失败") }
            }
        },
        setObjective = { objective ->
            scope.launch {
                runCatching {
                    appModel.client.setThreadGoal(
                        threadKey.serverId,
                        AppThreadGoalSetRequest(
                            threadId = threadKey.threadId,
                            objective = objective,
                            status = null,
                            tokenBudget = null,
                        ),
                    )
                }.onFailure { onSlashError?.invoke(it.message ?: "更新目标失败") }
            }
        },
        setBudget = { budget ->
            scope.launch {
                val resumeFromLimit = current.status == AppThreadGoalStatus.BUDGET_LIMITED
                    && budget != null
                    && budget > current.tokensUsed
                runCatching {
                    appModel.client.setThreadGoal(
                        threadKey.serverId,
                        AppThreadGoalSetRequest(
                            threadId = threadKey.threadId,
                            objective = null,
                            status = if (resumeFromLimit) AppThreadGoalStatus.ACTIVE else null,
                            tokenBudget = budget,
                        ),
                    )
                }.onFailure { onSlashError?.invoke(it.message ?: "更新目标失败") }
            }
        },
        clear = {
            scope.launch {
                runCatching {
                    appModel.client.clearThreadGoal(
                        threadKey.serverId,
                        AppThreadGoalClearRequest(threadId = threadKey.threadId),
                    )
                }.onFailure { onSlashError?.invoke(it.message ?: "清除目标失败") }
            }
        },
    )

internal suspend fun handleComposerGoalCommand(
    appModel: AppModel,
    threadKey: ThreadKey,
    args: String?,
    onSlashError: ((String) -> Unit)?,
) {
    val raw = args?.trim().orEmpty()
    when (raw.lowercase()) {
        "" -> {
            val current = appModel.client.getThreadGoal(
                threadKey.serverId,
                AppThreadGoalGetRequest(threadId = threadKey.threadId),
            )
            onSlashError?.invoke(current?.let(::goalSummary) ?: "此线程未设置目标。")
        }
        "pause" -> {
            appModel.client.setThreadGoal(
                threadKey.serverId,
                AppThreadGoalSetRequest(
                    threadId = threadKey.threadId,
                    objective = null,
                    status = AppThreadGoalStatus.PAUSED,
                    tokenBudget = null,
                ),
            )
        }
        "resume" -> {
            appModel.client.setThreadGoal(
                threadKey.serverId,
                AppThreadGoalSetRequest(
                    threadId = threadKey.threadId,
                    objective = null,
                    status = AppThreadGoalStatus.ACTIVE,
                    tokenBudget = null,
                ),
            )
        }
        "clear" -> {
            appModel.client.clearThreadGoal(
                threadKey.serverId,
                AppThreadGoalClearRequest(threadId = threadKey.threadId),
            )
        }
        else -> {
            appModel.client.setThreadGoal(
                threadKey.serverId,
                AppThreadGoalSetRequest(
                    threadId = threadKey.threadId,
                    objective = raw,
                    status = AppThreadGoalStatus.ACTIVE,
                    tokenBudget = null,
                ),
            )
        }
    }
}

private fun goalSummary(goal: AppThreadGoal): String {
    return buildString {
        append("目标：")
        append(goal.objective)
        append("\n状态：")
        append(goalStatusLabel(goal.status))
        append("\n已用 token：")
        append(goal.tokensUsed)
        goal.tokenBudget?.let {
            append("\ntoken 预算：")
            append(it)
        }
    }
}

private fun goalStatusLabel(status: AppThreadGoalStatus): String =
    when (status) {
        AppThreadGoalStatus.ACTIVE -> "进行中"
        AppThreadGoalStatus.PAUSED -> "已暂停"
        AppThreadGoalStatus.BLOCKED -> "已阻塞"
        AppThreadGoalStatus.USAGE_LIMITED -> "用量受限"
        AppThreadGoalStatus.BUDGET_LIMITED -> "预算受限"
        AppThreadGoalStatus.COMPLETE -> "已完成"
    }
