import { AppAskForApproval, AppSandboxMode, AppSandboxPolicy, ModelInfo, ReasoningEffort } from '@agentbuddy/core';

// User selections for a form/next turn only. Effective permissions, models and
// thread state continue to come from Rust snapshots after the server responds.
export class TaskOptions {
  model: string = '';
  effort?: ReasoningEffort;
  approval?: AppAskForApproval;
  sandbox?: AppSandboxMode;

  constructor(other?: TaskOptions) {
    if (!other) return;
    this.model = other.model; this.effort = other.effort;
    this.approval = other.approval; this.sandbox = other.sandbox;
  }
}

export class ApprovalOption {
  constructor(public label: string, public value?: AppAskForApproval) {}
}
export const approvalOptions: ApprovalOption[] = [
  new ApprovalOption('沿用当前设置'),
  new ApprovalOption('不信任：执行操作前询问', { tag: 'UnlessTrusted' }),
  new ApprovalOption('失败时询问', { tag: 'OnFailure' }),
  new ApprovalOption('请求提权时询问', { tag: 'OnRequest' }),
  new ApprovalOption('从不询问', { tag: 'Never' })
];

export class SandboxOption {
  constructor(public label: string, public value?: AppSandboxMode) {}
}
export const sandboxOptions: SandboxOption[] = [
  new SandboxOption('沿用当前设置'),
  new SandboxOption('只读', AppSandboxMode.ReadOnly),
  new SandboxOption('工作区写入', AppSandboxMode.WorkspaceWrite),
  new SandboxOption('完全访问', AppSandboxMode.DangerFullAccess)
];

export function approvalLabel(value?: AppAskForApproval): string {
  if (!value) return '主机尚未报告';
  return approvalOptions.find(option => option.value?.tag === value.tag)?.label ?? '自定义审批';
}

export function sandboxLabel(value?: AppSandboxPolicy): string {
  switch (value?.tag) {
    case 'ReadOnly': return '只读';
    case 'WorkspaceWrite': return '工作区写入';
    case 'DangerFullAccess': return '完全访问';
    case 'ExternalSandbox': return '外部沙箱';
    default: return '主机尚未报告';
  }
}

export function effortLabel(value: ReasoningEffort): string {
  switch (value) {
    case ReasoningEffort.None: return '无';
    case ReasoningEffort.Minimal: return '极低';
    case ReasoningEffort.Low: return '低';
    case ReasoningEffort.Medium: return '中';
    case ReasoningEffort.High: return '高';
    case ReasoningEffort.XHigh: return '极高';
    case ReasoningEffort.Max: return '最高';
  }
}

export function runtimeModels(models: ModelInfo[], runtime: string): ModelInfo[] {
  return models.filter(model => !model.hidden && model.agentRuntimeKind === runtime);
}
