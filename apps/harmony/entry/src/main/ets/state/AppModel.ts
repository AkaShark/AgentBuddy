import common from '@ohos.app.ability.common';
import {
  AlleycatBridge, AppAlleycatAgentInfo, AppAlleycatPairPayload, AppClient, AppStore, AppDiscoveredServer,
  AppStoreSubscription, AppAlleycatAgentWire, AppServerHealth, AppSortDirection, AppThreadSortKey, AppAgentCapabilities, AppUserInput,
  ApprovalDecisionValue, ReconnectController,
  PendingUserInputAnswer, SavedServerRecord, ServerBridge, ThreadKey, uniffiLoad
} from '@agentbuddy/core';
import { CredentialVault } from './CredentialVault';
import { ScreenState, messageViews, projectViews } from './ViewState';
import { TaskOptions } from './TaskOptions';
import { ImageAttachment, restoreMessageImages } from './ImageAttachment';
import { loadedUserIndex } from './MessageSelection';
import { savedDiscoverySource } from './HostPresentation';
import { SshConnection } from './SshConnection';
import { SshSecurity } from './SshSecurity';
import { errorMessage } from './ErrorPresentation';
import { TaskActions } from './TaskActions';
import { HomeActions } from './HomeActions';
import { PushController } from './PushController';

// Mirrors Android AppModel: Rust owns canonical state and reconciliation.
export class AppModel {
  static readonly shared = new AppModel();
  private notifications?: PushController;
  private store?: AppStore;
  private client?: AppClient;
  private bridge?: ServerBridge;
  private reconnect?: ReconnectController;
  private ssh?: SshConnection;
  private actions?: TaskActions;
  private home?: HomeActions;
  private subscription?: AppStoreSubscription;
  private vault = new CredentialVault();
  private state = new ScreenState();
  private observer?: (state: ScreenState) => void;
  private pair?: AppAlleycatPairPayload;
  private selected?: ThreadKey;
  private initializing = false;
  private refreshQueued = false;
  private refreshJob?: Promise<void>;
  private composerOptions = new Map<string, TaskOptions>();
  private composerDrafts = new Map<string, string>();
  private imageDrafts = new Map<string, ImageAttachment[]>();

  view(): ScreenState { return this.state; }
  attachNotifications(controller: PushController): void {
    this.notifications = controller;
    if (this.client && this.state.ready) controller.start(this.client);
  }
  notificationStatus(text: string): void { this.state.notificationStatus = text; this.emit(); }
  enableNotifications(): void { void this.notifications?.enable(); }
  async openNotification(key: ThreadKey): Promise<boolean> {
    if (!this.state.ready || this.state.busy) return false;
    if (!this.state.hosts.some(host => host.id === key.serverId)) {
      this.state.error = '通知对应的主机已移除，请重新配对后打开任务'; this.emit(); return true;
    }
    if (!this.state.servers.some(server => server.serverId === key.serverId && server.health === AppServerHealth.Connected)) {
      await this.reconnectHost(key.serverId);
    }
    await this.openThread(key);
    return this.selected?.serverId === key.serverId && this.selected?.threadId === key.threadId;
  }

  observe(observer: (state: ScreenState) => void): void {
    this.observer = observer;
    observer(this.state);
  }

  unobserve(): void { this.observer = undefined; }

  private emit(): void {
    this.state = Object.assign(new ScreenState(), this.state);
    this.observer?.(this.state);
  }

  async initialize(context: common.UIAbilityContext): Promise<void> {
    if (this.initializing || this.state.ready) return;
    this.initializing = true;
    await this.perform(async () => {
      uniffiLoad();
      this.store = new AppStore();
      this.client = new AppClient();
      this.actions = new TaskActions(this.client, this.store, action => this.perform(action), () => this.refresh());
      this.home = new HomeActions(context.filesDir + '/AgentBuddyPreferences', this.store,
        action => this.perform(action), preferences => { this.state.homePreferences = preferences; this.emit(); });
      this.home.load();
      this.bridge = new ServerBridge();
      this.reconnect = new ReconnectController();
      const security = new SshSecurity(this.vault);
      this.reconnect.setCredentialProvider(security);
      this.ssh = new SshConnection(context, security, this.bridge, this.client, async host => {
        await this.saveHost(host);
        // A list refresh failure must not undo a successfully saved connection.
        try { await this.loadSessions(); } catch (error) { this.fail(error); }
      });
      this.reconnect.setMultiClankerAndQuicEnabled(true);
      this.client.setSavedAppsDirectory(context.filesDir + '/saved-apps');
      this.client.setSlingshotCredentialsDirectory(context.filesDir + '/preferences');
      this.client.setAlleycatSecretKey(await this.vault.read('agentbuddy.iroh.v1'));
      this.state.hosts = await this.vault.loadHosts();
      this.reconnect.syncSavedServers(this.state.hosts);
      this.subscription = this.store.subscribeUpdates();
      this.state.ready = true;
      this.notifications?.start(this.client);
      void this.watch();
      await this.refresh();
      await this.reconnect.onAppBecameActive();
      await this.loadSessions();
      await this.persistIdentity();
    });
    this.initializing = false;
  }

  private async persistIdentity(): Promise<void> {
    const key = this.client?.alleycatSecretKey();
    if (key) await this.vault.write('agentbuddy.iroh.v1', key);
  }

  private async watch(): Promise<void> {
    while (this.subscription) {
      try {
        await this.subscription.nextUpdate();
        await this.refresh();
      } catch (error) {
        this.fail(error);
        break;
      }
    }
  }

  async refresh(): Promise<void> {
    if (!this.store) return;
    this.refreshQueued = true;
    if (!this.refreshJob) this.refreshJob = this.drainRefreshes();
    // Callers that need fresh UI state (for example a pagination anchor) must
    // await an in-flight refresh too, not return before its snapshot arrives.
    return this.refreshJob;
  }

  private async drainRefreshes(): Promise<void> {
    try {
      do {
        this.refreshQueued = false;
        const snapshot = await this.store!.snapshot();
        this.state.servers = snapshot.servers;
        this.state.tasks = snapshot.sessionSummaries;
        this.state.projects = projectViews(snapshot.sessionSummaries);
        this.state.approvals = snapshot.pendingApprovals;
        this.state.questions = snapshot.pendingUserInputs;
        const selected = this.selected;
        const thread = selected ? await this.store!.threadSnapshot(selected) : undefined;
        if (JSON.stringify(selected) !== JSON.stringify(this.selected)) { this.refreshQueued = true; continue; }
        this.state.thread = thread;
        this.state.messages = messageViews(this.state.thread?.hydratedConversationItems ?? [], this.state.messages);
        this.emit();
      } while (this.refreshQueued);
    } finally { this.refreshJob = undefined; }
  }

  private fail(error: unknown): void {
    this.state.error = errorMessage(error, '操作失败，请重试');
    this.emit();
  }

  async perform(action: () => Promise<void>): Promise<boolean> {
    if (this.state.busy) return false;
    this.state.busy = true; this.state.error = ''; this.emit();
    try { await action(); return true; } catch (error) { this.fail(error); return false; }
    finally { this.state.busy = false; this.emit(); void this.notifications?.drainRoute(); }
  }

  clearError(): void { this.state.error = ''; this.emit(); }

  private async loadSessions(): Promise<void> {
    const snapshot = await this.store!.snapshot();
    for (const server of snapshot.servers) {
      if (server.health !== AppServerHealth.Connected) continue;
      // Omitting limit lets Rust drain every cursor page and reconcile the full list.
      await this.client!.listThreads(server.serverId, { archived: false,
        sortKey: AppThreadSortKey.UpdatedAt, sortDirection: AppSortDirection.Desc, useStateDbOnly: false });
    }
    await this.refresh();
    await this.home?.hydratePinned(this.state.tasks, this.state.servers);
  }

  async refreshSessions(): Promise<void> { await this.perform(() => this.loadSessions()); }

  async inspectPair(payload: string): Promise<void> {
    await this.perform(async () => {
      const parser = new AlleycatBridge();
      try { this.pair = parser.parsePairPayload(payload.trim()); }
      finally { parser.destroy(); }
      this.state.agents = await this.bridge!.listAlleycatAgents(this.pair);
      await this.persistIdentity();
    });
  }

  async pairAgent(agent: AppAlleycatAgentInfo): Promise<boolean> {
    if (!this.pair) return false;
    const pair = this.pair;
    return this.perform(async () => {
      const id = `alleycat:${pair.nodeId}`;
      const name = pair.hostName ?? '我的电脑';
      await this.bridge!.connectRemoteOverAlleycat(id, name, pair, agent.name, [agent.name], agent.wire);
      await this.saveHost({ id, name, hostname: name, port: 0, codexPorts: [], source: 'manual',
        hasCodexServer: true, rememberedByUser: true, alleycatNodeId: pair.nodeId,
        alleycatToken: pair.token, alleycatRelay: pair.relay, alleycatAgentName: agent.name,
        alleycatAgentWire: agent.wire === AppAlleycatAgentWire.Websocket ? 'websocket' : 'jsonl' });
      this.pair = undefined; this.state.agents = [];
      await this.persistIdentity();
      await this.loadSessions();
    });
  }

  async connectUrl(name: string, url: string): Promise<boolean> {
    return this.perform(async () => {
      const id = 'remote:' + Date.now().toString();
      await this.bridge!.connectRemoteUrlServer(id, name.trim() || '远程主机', url.trim());
      // Address parsing and validation stay in ServerBridge; use its canonical snapshot.
      const snapshot = await this.store!.snapshot();
      const server = snapshot.servers.find(value => value.serverId === id);
      if (!server) throw new Error('连接后未收到主机状态');
      await this.saveHost({ id, name: server.displayName, hostname: server.host, port: server.port,
        codexPorts: [server.port], source: 'manual', hasCodexServer: true,
        rememberedByUser: true, websocketUrl: url.trim() });
      await this.loadSessions();
    });
  }

  async connectDiscovered(server: AppDiscoveredServer, port: number): Promise<boolean> {
    return this.perform(async () => {
      await this.bridge!.connectRemoteServer(server.id, server.displayName, server.host, port);
      await this.saveHost({ id: server.id, name: server.displayName, hostname: server.host, port,
        codexPorts: server.codexPorts, sshPort: server.sshPort, source: savedDiscoverySource(server.source),
        hasCodexServer: true, rememberedByUser: true, preferredConnectionMode: 'directCodex', preferredCodexPort: port });
      await this.loadSessions();
    });
  }

  private async saveHost(host: SavedServerRecord): Promise<void> {
    const hosts = this.state.hosts.filter(value => value.id !== host.id).concat(host);
    await this.vault.saveHosts(hosts);
    this.state.hosts = hosts;
    this.reconnect!.syncSavedServers(hosts);
  }

  async reconnectHost(id: string): Promise<void> {
    await this.perform(async () => {
      const result = await this.reconnect!.reconnectServer(id);
      if (!result.success) throw new Error(result.errorMessage || '连接失败，请重新添加主机或检查登录信息。');
      await this.loadSessions();
    });
  }

  sshConnection(): SshConnection | undefined { return this.ssh; }
  taskActions(): TaskActions | undefined { return this.actions; }
  homeActions(): HomeActions | undefined { return this.home; }
  setHomeView(scope: number, query: string): void {
    this.state.homeScope = scope; this.state.homeQuery = query; this.emit();
  }

  async refreshAccount(serverId: string): Promise<void> {
    await this.perform(async () => {
      // Keep account information visible even when a host does not expose usage.
      await this.client!.refreshAccount(serverId, { refreshToken: false });
      await this.refresh();
      await this.client!.refreshRateLimits(serverId);
      await this.refresh();
    });
  }

  async forgetHost(id: string): Promise<void> {
    await this.perform(async () => {
      const hosts = this.state.hosts.filter(host => host.id !== id);
      await this.vault.saveHosts(hosts);
      this.bridge!.disconnectServer(id);
      await this.ssh?.disconnect(id);
      this.state.hosts = hosts;
      this.reconnect!.syncSavedServers(hosts);
      await this.refresh();
    });
  }

  async openThread(key: ThreadKey): Promise<void> {
    await this.perform(async () => {
      await this.store!.externalResumeThread(key, undefined);
      this.selected = await this.client!.readThread(key.serverId, { threadId: key.threadId, includeTurns: false });
      this.store!.setActiveThread(this.selected);
      await this.store!.loadThreadTurnsPage(this.selected, undefined, 5);
      await this.refresh();
    });
  }

  closeThread(): void {
    this.selected = undefined; this.store?.setActiveThread(undefined);
    this.state.thread = undefined; this.state.messages = []; this.emit();
  }

  async loadOlderTurns(): Promise<void> {
    const thread = this.state.thread;
    if (!thread?.olderTurnsCursor) return;
    await this.perform(async () => {
      await this.store!.loadThreadTurnsPage(thread.key, thread.olderTurnsCursor, 5);
      await this.refresh();
    });
  }

  runtimeCapabilities(runtime: string): AppAgentCapabilities | undefined {
    return this.client?.agentMetadata(runtime)?.capabilities;
  }

  async refreshModels(serverId: string): Promise<void> {
    await this.perform(async () => {
      await this.client!.refreshModels(serverId, { includeHidden: false });
      await this.refresh();
    });
  }

  taskOptions(key: ThreadKey): TaskOptions {
    return new TaskOptions(this.composerOptions.get(JSON.stringify(key)));
  }

  composerDraft(key: ThreadKey): string { return this.composerDrafts.get(JSON.stringify(key)) ?? ''; }

  saveComposerDraft(key: ThreadKey, text: string): void {
    if (text) this.composerDrafts.set(JSON.stringify(key), text);
    else this.composerDrafts.delete(JSON.stringify(key));
  }

  composerImages(key: ThreadKey): ImageAttachment[] { return this.imageDrafts.get(JSON.stringify(key)) ?? []; }

  saveComposerImages(key: ThreadKey, images: ImageAttachment[]): void {
    if (images.length) this.imageDrafts.set(JSON.stringify(key), images.slice());
    else this.imageDrafts.delete(JSON.stringify(key));
  }

  async imageBytes(serverId: string, path: string): Promise<Uint8Array> {
    if (!this.client) throw new Error('尚未连接主机');
    return (await this.client.resolveImageView(serverId, path)).bytes;
  }

  async applyTaskOptions(key: ThreadKey, options: TaskOptions): Promise<boolean> {
    return this.perform(async () => {
      const thread = await this.store!.threadSnapshot(key);
      if (thread?.activeTurnId) throw new Error('请等待当前回复完成后再修改任务设置。');
      await this.client!.resumeThread(key.serverId, { threadId: key.threadId,
        model: options.model || undefined, approvalPolicy: options.approval, sandbox: options.sandbox,
        persistExtendedHistory: true, excludeTurns: true });
      // Permission changes are now authoritative on the server. Only retain
      // explicit composer model/effort selections, never copy effective state.
      const next = new TaskOptions(); next.model = options.model; next.effort = options.effort;
      this.composerOptions.set(JSON.stringify(key), next);
      await this.refresh();
    });
  }

  async renameThread(key: ThreadKey, name: string): Promise<boolean> {
    if (!name.trim()) return false;
    return this.perform(async () => {
      await this.client!.renameThread(key.serverId, { threadId: key.threadId, name: name.trim() });
      await this.client!.readThread(key.serverId, { threadId: key.threadId, includeTurns: false });
      await this.loadSessions();
    });
  }

  async forkThread(key: ThreadKey): Promise<boolean> {
    return this.perform(async () => {
      this.selected = await this.client!.forkThread(key.serverId, { threadId: key.threadId,
        persistExtendedHistory: true, excludeTurns: false });
      this.home?.rememberStarted(this.selected);
      this.store!.setActiveThread(this.selected);
      // Match Android: the fork response already contains its inherited turns.
      await this.refresh();
    });
  }

  async actOnMessage(key: ThreadKey, id: string, edit: boolean): Promise<boolean> {
    return this.perform(async () => {
      let thread = await this.store!.threadSnapshot(key);
      const original = thread?.hydratedConversationItems.find(item => item.id === id);
      if (original?.content.tag !== 'User') throw new Error('消息已更新，请重新选择。');
      const images = edit ? await restoreMessageImages(original.content.value0.imageDataUris,
        path => this.imageBytes(key.serverId, path)) : [];
      // Image recovery can await host I/O. Resolve the index again afterward.
      thread = await this.store!.threadSnapshot(key);
      const index = loadedUserIndex(thread?.hydratedConversationItems ?? [], id);
      if (index < 0) throw new Error('消息已更新，请重新选择。');
      if (edit) {
        const text = await this.store!.editMessage(key, index);
        this.saveComposerDraft(key, text); this.saveComposerImages(key, images);
      } else {
        const options = this.taskOptions(key);
        this.selected = await this.store!.forkThreadFromMessage(key, index, {
          cwd: thread?.info.cwd, model: options.model || undefined, persistExtendedHistory: true });
        this.composerOptions.set(JSON.stringify(this.selected), options);
        this.home?.rememberStarted(this.selected);
      }
      await this.refresh();
    });
  }

  async archiveThread(key: ThreadKey): Promise<boolean> {
    return this.perform(async () => {
      await this.client!.archiveThread(key.serverId, { threadId: key.threadId });
      this.home?.forgetArchived(key);
      if (this.selected?.serverId === key.serverId && this.selected?.threadId === key.threadId) this.closeThread();
      this.composerOptions.delete(JSON.stringify(key));
      this.composerDrafts.delete(JSON.stringify(key));
      this.imageDrafts.delete(JSON.stringify(key));
      await this.loadSessions();
    });
  }

  async startThread(serverId: string, cwd: string, runtime: string, text: string, options: TaskOptions): Promise<boolean> {
    return this.perform(async () => {
      this.selected = await this.client!.startThread(serverId, { cwd: cwd.trim() || undefined,
        agentRuntimeKind: runtime || undefined, model: options.model || undefined,
        approvalPolicy: options.approval, sandbox: options.sandbox, persistExtendedHistory: true });
      this.home?.rememberStarted(this.selected);
      this.store!.setActiveThread(this.selected);
      const next = new TaskOptions(); next.model = options.model; next.effort = options.effort;
      this.composerOptions.set(JSON.stringify(this.selected), next);
      // If the first turn fails, still show the created thread with its unsent
      // draft. Retrying should send to that thread, not create a duplicate.
      this.saveComposerDraft(this.selected, text);
      try {
        if (text.trim()) await this.sendTurn(text);
        this.saveComposerDraft(this.selected, '');
      } finally { await this.refresh(); }
    });
  }

  private async sendTurn(text: string, images: ImageAttachment[] = []): Promise<void> {
    const key = this.selected;
    if (!key) return;
    const options = this.taskOptions(key);
    const thread = await this.store!.threadSnapshot(key);
    const locked = thread && this.runtimeCapabilities(thread.agentRuntimeKind)?.locksReasoningEffortAfterActivity &&
      thread.hydratedConversationItems.length > 0;
    const input: AppUserInput[] = [];
    if (text.trim()) input.push({ tag: 'Text', text: text.trim(), textElements: [] });
    for (const attachment of images) input.push({ tag: 'Image', url: attachment.dataUri });
    await this.store!.startTurn(key, { threadId: key.threadId, input,
      model: options.model || undefined, effort: locked ? undefined : options.effort });
  }

  async send(text: string, images: ImageAttachment[] = []): Promise<boolean> {
    if ((!text.trim() && !images.length) || !this.selected) return false;
    return this.perform(async () => { await this.sendTurn(text, images); await this.refresh(); });
  }

  async interrupt(): Promise<void> {
    const thread = this.state.thread;
    if (!thread?.activeTurnId) return;
    await this.perform(async () => {
      await this.client!.interruptTurn(thread.key.serverId, {
        threadId: thread.key.threadId, turnId: thread.activeTurnId! });
    });
  }

  async approve(id: string, accept: boolean): Promise<void> {
    await this.perform(async () => {
      await this.store!.respondToApproval(id, accept ? ApprovalDecisionValue.Accept : ApprovalDecisionValue.Decline);
      await this.refresh();
    });
  }

  async answer(id: string, answers: PendingUserInputAnswer[]): Promise<boolean> {
    return this.perform(async () => {
      await this.store!.respondToUserInput(id, answers);
      await this.refresh();
    });
  }

  resume(): void {
    void this.notifications?.refresh();
    if (!this.state.ready) return;
    void this.perform(async () => { await this.reconnect!.onAppBecameActive(); await this.loadSessions(); });
  }

  background(): void { this.reconnect?.onAppEnteredBackground(); }

  detach(): void {
    this.observer = undefined;
    this.reconnect?.onAppEnteredBackground();
    // The singleton owns the native runtime for the process lifetime. An Ability can be
    // destroyed and recreated in the same process; keep its one subscription alive.
  }
}
