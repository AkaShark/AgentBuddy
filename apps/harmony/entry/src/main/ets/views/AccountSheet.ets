import { Account, AppServerHealth, AppServerSnapshot, AppRateLimitsForRuntime, PlanType,
  RateLimitSnapshot, RateLimitWindow } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { BuddyButton } from '../designsystem/BuddyButton';
import { BuddyColor, BuddySpacing, BuddyRadius, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct AccountSheet {
  @Prop screen: ScreenState;
  initialServerId: string = '';
  @State serverId: string = '';

  aboutToAppear(): void {
    this.serverId = this.initialServerId || this.screen.servers[0]?.serverId || '';
    if (this.serverId) void AppModel.shared.refreshAccount(this.serverId);
  }
  private server(): AppServerSnapshot | undefined { return this.screen.servers.find(server => server.serverId === this.serverId); }
  private account(): Account | undefined { return this.server()?.account; }
  private status(): string {
    const account = this.account();
    return account?.tag === 'Chatgpt' ? '已登录 ChatGPT' : account?.tag === 'ApiKey' ? 'API 密钥已配置' : '未登录';
  }
  private email(): string { const account = this.account(); return account?.tag === 'Chatgpt' ? account.email : ''; }
  private plan(): string { const account = this.account(); return account?.tag === 'Chatgpt' ? planLabel(account.planType) : ''; }

  @Builder window(label: string, value: RateLimitWindow) {
    Column({ space: BuddySpacing.xs }) {
      Text(label + (value.windowDurationMins ? ' · ' + durationLabel(value.windowDurationMins) : ''))
        .fontSize(BuddyText.body).fontColor(BuddyColor.text).width('100%')
      Text('剩余 ' + Math.max(0, Math.min(100, 100 - value.usedPercent)).toFixed(0) + '%')
        .fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      Progress({ value: Math.max(0, Math.min(100, 100 - value.usedPercent)), total: 100 }).width('100%')
      if (value.resetsAt) {
        Text('重置时间：' + new Date(Number(value.resetsAt) * 1000).toLocaleString())
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      }
    }.width('100%')
  }

  @Builder limits(label: string, value: RateLimitSnapshot) {
    Column({ space: BuddySpacing.md }) {
      Text(label + (value.limitName ? ' · ' + value.limitName : ''))
        .fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      if (value.primary) { this.window('主要额度', value.primary) }
      if (value.secondary) { this.window('次要额度', value.secondary) }
      if (!value.primary && !value.secondary) {
        Text('主机未提供用量窗口。').fontColor(BuddyColor.secondary).width('100%')
      }
      if (value.credits) {
        Text(value.credits.unlimited ? '额度：不限量' : '额度余额：' + (value.credits.balance ?? '未提供'))
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      }
    }.width('100%').padding(BuddySpacing.md).backgroundColor(BuddyColor.surface).borderRadius(BuddyRadius.card)
  }

  build() {
    Scroll() {
      Column({ space: BuddySpacing.md }) {
        Text('账户与用量').fontSize(BuddyText.title).fontColor(BuddyColor.text).width('100%')
        Select(this.screen.servers.map(server => ({ value: server.displayName })))
          .selected(this.screen.servers.findIndex(server => server.serverId === this.serverId))
          .value(this.server()?.displayName || '选择主机').width('100%').enabled(!this.screen.busy)
          .onSelect((index: number) => {
            this.serverId = this.screen.servers[index].serverId;
            void AppModel.shared.refreshAccount(this.serverId);
          })
        if (this.screen.error) { Text(this.screen.error).fontColor(BuddyColor.error).width('100%') }
        Column({ space: BuddySpacing.sm }) {
          Text(this.status()).fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
          if (this.email()) { Text(this.email()).fontColor(BuddyColor.text).width('100%') }
          if (this.plan()) { Text(this.plan()).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%') }
          Text('账户和 API 密钥由远程主机管理，需要切换账户时请在电脑端操作。')
            .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
        }.padding(BuddySpacing.md).width('100%').backgroundColor(BuddyColor.surface).borderRadius(BuddyRadius.card)
        BuddyButton({ label: this.screen.busy ? '正在刷新…' : '刷新账户与用量',
          isEnabled: !this.screen.busy && this.server()?.health === AppServerHealth.Connected,
          action: () => { void AppModel.shared.refreshAccount(this.serverId); } })
        if (this.server()?.rateLimitsByRuntime.length) {
          ForEach(this.server()?.rateLimitsByRuntime ?? [], (entry: AppRateLimitsForRuntime) => {
            this.limits(entry.runtimeKind, entry.rateLimits)
          }, (entry: AppRateLimitsForRuntime) => this.serverId + ':' + entry.runtimeKind + ':' + entry.rateLimits.primary?.usedPercent + ':' +
            entry.rateLimits.secondary?.usedPercent + ':' + entry.rateLimits.primary?.resetsAt?.toString() + ':' +
            entry.rateLimits.secondary?.resetsAt?.toString() + ':' + entry.rateLimits.credits?.balance + ':' +
            entry.rateLimits.credits?.unlimited + ':' + entry.rateLimits.primary?.windowDurationMins?.toString() + ':' +
            entry.rateLimits.secondary?.windowDurationMins?.toString() + ':' + entry.rateLimits.limitName)
        } else if (this.server()?.rateLimits) { this.limits('主机用量', this.server()!.rateLimits!) }
        else { Text('主机尚未提供额度信息。').fontColor(BuddyColor.secondary).width('100%') }
      }.width('100%').padding(BuddySpacing.xl)
    }.width('100%').height('100%').backgroundColor(BuddyColor.background)
  }
}

function durationLabel(minutes: bigint): string {
  const value = Number(minutes);
  if (value % 1440 === 0) return (value / 1440).toString() + ' 天';
  if (value % 60 === 0) return (value / 60).toString() + ' 小时';
  return value.toString() + ' 分钟';
}

function planLabel(plan: PlanType): string {
  switch (plan) {
    case PlanType.Free: return 'Free';
    case PlanType.Go: return 'Go';
    case PlanType.Plus: return 'Plus';
    case PlanType.Pro: return 'Pro';
    case PlanType.Team: return 'Team';
    case PlanType.Business: return 'Business';
    case PlanType.Enterprise: return 'Enterprise';
    case PlanType.Edu: return 'Edu';
    default: return '订阅类型未知';
  }
}
