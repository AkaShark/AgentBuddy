import { AppStore, AppServerHealth, AppSessionSummary, AppServerSnapshot,
  HomeSelection, MobilePreferences, ThreadKey, preferencesLoad, preferencesAddPinnedThread,
  preferencesRemovePinnedThread, preferencesAddHiddenThread, preferencesRemoveHiddenThread,
  preferencesSetHomeSelection } from '@agentbuddy/core';

// Same persistence boundary as Android SavedThreadsStore. No separate JSON format.
export class HomeActions {
  constructor(private directory: string, private store: AppStore,
    private perform: (action: () => Promise<void>) => Promise<boolean>,
    private changed: (preferences: MobilePreferences) => void) {}

  load(): void { this.changed(preferencesLoad(this.directory)); }
  rememberStarted(key: ThreadKey): void {
    preferencesAddPinnedThread(this.directory, key);
    preferencesRemoveHiddenThread(this.directory, key);
    this.load();
  }
  forgetArchived(key: ThreadKey): void {
    preferencesRemovePinnedThread(this.directory, key);
    preferencesRemoveHiddenThread(this.directory, key);
    this.load();
  }

  pin(key: ThreadKey, pinned: boolean): Promise<boolean> {
    return this.perform(async () => {
      if (pinned) {
        this.rememberStarted(key);
      } else { preferencesRemovePinnedThread(this.directory, key); }
      this.load();
      if (pinned) {
        await this.store.externalResumeThread(key, undefined);
        await this.store.loadThreadTurnsPage(key, undefined, 5);
      }
    });
  }

  hide(key: ThreadKey, hidden: boolean): Promise<boolean> {
    return this.perform(async () => {
      if (hidden) preferencesAddHiddenThread(this.directory, key);
      else preferencesRemoveHiddenThread(this.directory, key);
      this.load();
      if (hidden) await this.store.unsubscribeThread(key);
    });
  }

  select(selection: HomeSelection): Promise<boolean> {
    return this.perform(async () => {
      preferencesSetHomeSelection(this.directory, selection);
      this.load();
    });
  }

  // Resume pinned listeners without changing the selected conversation. The
  // canonical store owns listener reuse, hydration and all resulting updates.
  async hydratePinned(sessions: AppSessionSummary[], servers: AppServerSnapshot[]): Promise<void> {
    const preferences = preferencesLoad(this.directory);
    for (const key of preferences.pinnedThreads) {
      if (preferences.hiddenThreads.some(value => value.serverId === key.serverId && value.threadId === key.threadId)) continue;
      if (!servers.some(server => server.serverId === key.serverId && server.health === AppServerHealth.Connected)) continue;
      if (sessions.some(session => session.key.serverId === key.serverId && session.key.threadId === key.threadId && session.isResumed)) continue;
      try {
        await this.store.externalResumeThread(key, undefined);
        await this.store.loadThreadTurnsPage(key, undefined, 5);
      } catch (_) { /* A stale/offline pin remains visible so the user can retry or remove it. */ }
    }
  }
}
