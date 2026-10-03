import { UIAbility, Want } from '@kit.AbilityKit';
import { notificationManager } from '@kit.NotificationKit';
import { pushService } from '@kit.PushKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { AppClient, AppPushPlatform, ThreadKey } from '@agentbuddy/core';

const WORKER = 'https://agentbuddy-push-proxy.aaksharker.workers.dev';

// Only native token/permission lifecycle and navigation live here. Rust owns
// signed grants, subscriptions, rotations, revocation and turn deduplication.
export class PushController {
  private client?: AppClient;
  private revision = 0;
  private pending?: ThreadKey;
  private pendingEvent = '';
  private handled: string[] = [];
  private opening = false;
  private attached = false;
  private tokenUpdated = (token: string): void => { void this.refresh(token); };

  constructor(private ability: UIAbility, private status: (text: string) => void,
    private open: (key: ThreadKey) => Promise<boolean>) {}

  start(client: AppClient): void {
    this.client = client;
    if (!this.attached) {
      try { pushService.on('tokenUpdate', this.ability, this.tokenUpdated); this.attached = true; }
      catch (error) { this.showError(error as BusinessError); }
    }
    void this.refresh();
    void this.drainRoute();
  }

  async enable(): Promise<void> {
    try { await notificationManager.requestEnableNotification(this.ability.context); }
    catch (error) { this.showError(error as BusinessError); }
    await this.refresh();
  }

  async refresh(updatedToken?: string): Promise<void> {
    const client = this.client;
    if (!client) return;
    const revision = ++this.revision;
    try {
      const enabled = await notificationManager.isNotificationEnabled();
      if (revision !== this.revision) return;
      if (!enabled) {
        client.setPushRegistration(undefined);
        this.status('通知未开启，可在系统设置中允许通知');
        return;
      }
      const token = updatedToken ?? await pushService.getToken();
      if (revision !== this.revision) return;
      client.setPushRegistration({ platform: AppPushPlatform.Harmony, token, workerBaseUrl: WORKER });
      this.status('通知已开启');
    } catch (error) {
      if (revision !== this.revision) return;
      // A transient token fetch failure must not discard an existing valid
      // registration. A denied permission is handled explicitly above.
      this.showError(error as BusinessError);
    }
  }

  private showError(error: BusinessError): void {
    const code = error.code;
    this.status(code === 1000900012 ? '推送服务尚未开通，请完成应用配置' :
      code === 1000900010 ? '推送应用标识无效，请检查签名与应用配置' :
      `通知暂不可用（${code ?? '未知错误'}），可稍后重试`);
  }

  receive(want: Want): void {
    const parameters = want.parameters;
    const serverId = parameters?.['agentbuddy.notification.serverId'];
    const threadId = parameters?.['agentbuddy.notification.threadId'];
    const eventId = parameters?.['agentbuddy.notification.eventId'];
    if (typeof serverId !== 'string' || !/^alleycat:[0-9a-f]{64}$/.test(serverId) ||
      typeof threadId !== 'string' || !threadId.length || threadId.length > 128 || /[\x00-\x1f\x7f]/.test(threadId) ||
      typeof eventId !== 'string' || !/^evt_[0-9a-f]{32}$/.test(eventId) || this.handled.includes(eventId)) return;
    this.pending = { serverId, threadId }; this.pendingEvent = eventId;
    void this.drainRoute();
  }

  async drainRoute(): Promise<void> {
    if (!this.client || !this.pending || this.opening) return;
    const key = this.pending, event = this.pendingEvent;
    this.opening = true;
    try {
      if (await this.open(key)) {
        this.handled = this.handled.slice(-31).concat(event);
        if (this.pendingEvent === event) this.pending = undefined;
      }
    } catch {
      this.status('通知任务暂时无法打开，请稍后重试');
    } finally {
      this.opening = false;
      // A second tap can arrive while the first navigation is awaiting Rust.
      // Retry only a newer event here; the same failed event waits for resume.
      if (this.pending && this.pendingEvent !== event) void this.drainRoute();
    }
  }

  detach(): void {
    ++this.revision;
    if (this.attached) {
      try { pushService.off('tokenUpdate', this.tokenUpdated); } catch { /* Ability is being destroyed. */ }
    }
    this.attached = false;
    this.client = undefined;
  }
}
