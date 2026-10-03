import { AppClient, AppStore, AppCollaborationModePreset, AppModeKind, AppReviewTarget,
  AppThreadGoalSetRequest, ThreadKey } from '@agentbuddy/core';

// UI action plumbing only. Rust owns plan/goal transitions and the message queue.
export class TaskActions {
  constructor(private client: AppClient, private store: AppStore,
    private perform: (action: () => Promise<void>) => Promise<boolean>,
    private refresh: () => Promise<void>) {}

  private run(action: () => Promise<void>): Promise<boolean> {
    return this.perform(async () => { await action(); await this.refresh(); });
  }

  modes(serverId: string): Promise<AppCollaborationModePreset[]> { return this.client.listCollaborationModes(serverId); }
  setMode(key: ThreadKey, mode: AppModeKind): Promise<boolean> {
    return this.run(() => this.store.setThreadCollaborationMode(key, mode));
  }
  implementPlan(key: ThreadKey): Promise<boolean> { return this.run(() => this.store.implementPlan(key)); }
  dismissPlan(key: ThreadKey): Promise<boolean> {
    return this.run(async () => { this.store.dismissPlanImplementationPrompt(key); });
  }
  refreshGoal(key: ThreadKey): Promise<boolean> {
    return this.run(async () => { await this.client.getThreadGoal(key.serverId, { threadId: key.threadId }); });
  }
  setGoal(key: ThreadKey, request: AppThreadGoalSetRequest): Promise<boolean> {
    return this.run(async () => { await this.client.setThreadGoal(key.serverId, request); });
  }
  clearGoal(key: ThreadKey): Promise<boolean> {
    return this.run(async () => { await this.client.clearThreadGoal(key.serverId, { threadId: key.threadId }); });
  }
  review(key: ThreadKey, target: AppReviewTarget): Promise<boolean> {
    return this.run(() => this.client.startReview(key.serverId, { threadId: key.threadId, target }));
  }
  deleteQueued(key: ThreadKey, id: string): Promise<boolean> {
    return this.run(() => this.store.deleteQueuedFollowUp(key, id));
  }
  steerQueued(key: ThreadKey, id: string): Promise<boolean> {
    return this.run(() => this.store.steerQueuedFollowUp(key, id));
  }
}
