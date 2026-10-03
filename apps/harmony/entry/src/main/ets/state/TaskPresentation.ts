import { AppModeKind, AppThreadGoalStatus, AppQueuedFollowUpKind, AppPlanStepStatus } from '@agentbuddy/core';

export function modeLabel(mode?: AppModeKind): string { return mode === AppModeKind.Plan ? '计划' : '默认'; }
export function goalStatusLabel(status?: AppThreadGoalStatus): string {
  switch (status) {
    case AppThreadGoalStatus.Active: return '进行中';
    case AppThreadGoalStatus.Paused: return '已暂停';
    case AppThreadGoalStatus.Blocked: return '已阻塞';
    case AppThreadGoalStatus.UsageLimited: return '用量受限';
    case AppThreadGoalStatus.BudgetLimited: return '预算受限';
    case AppThreadGoalStatus.Complete: return '已完成';
    default: return '尚未设置';
  }
}
export function queueLabel(kind: AppQueuedFollowUpKind): string {
  switch (kind) {
    case AppQueuedFollowUpKind.PendingSteer: return '正在插入当前回复';
    case AppQueuedFollowUpKind.RetryingSteer: return '正在重试插入';
    default: return '等待发送';
  }
}
export function planStepMark(status: AppPlanStepStatus): string {
  switch (status) {
    case AppPlanStepStatus.Completed: return '✓';
    case AppPlanStepStatus.InProgress: return '●';
    default: return '○';
  }
}
