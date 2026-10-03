import { AppServerSnapshot, AppServerHealth, AgentRuntimeInfo } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';
import { BuddyButton } from '../designsystem/BuddyButton';
import { TaskOptionsForm } from './TaskOptionsForm';
import { TaskOptions, runtimeModels } from '../state/TaskOptions';

@Component
export struct NewTaskSheet {
  @Prop screen: ScreenState;
  initialPath: string = '';
  initialServer: string = '';
  close: () => void = () => {};
  @State serverId: string = '';
  @State path: string = '';
  @State prompt: string = '';
  @State runtime: string = '';
  @State options: TaskOptions = new TaskOptions();
  @State advanced: boolean = false;
  @State creating: boolean = false;

  aboutToAppear(): void {
    this.path = this.initialPath;
    const connected = this.screen.servers.filter(server => server.health === AppServerHealth.Connected);
    this.selectServer(this.initialServer || (connected.length === 1 ? connected[0].serverId : ''));
  }

  private selectServer(id: string): void {
    this.serverId = id; this.options = new TaskOptions();
    const agents = this.screen.servers.find(server => server.serverId === id)?.agentRuntimes.filter(agent => agent.available) ?? [];
    this.runtime = agents.length === 1 ? agents[0].kind : '';
    if (id) void AppModel.shared.refreshModels(id);
  }

  async start(): Promise<void> {
    this.creating = true;
    try {
      if (await AppModel.shared.startThread(this.serverId, this.path, this.runtime, this.prompt, this.options)) this.close();
    } finally { this.creating = false; }
  }

  build() {
    Scroll() {
      Column({ space: BuddySpacing.md }) {
        Text('新任务').fontSize(BuddyText.title).fontColor(BuddyColor.text).width('100%')
        if (this.screen.error) { Text(this.screen.error).fontColor(BuddyColor.error).width('100%') }
        Text('选择主机').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
        if (this.screen.servers.length === 0) {
          Text('请先添加并连接一台主机。').fontColor(BuddyColor.secondary)
        }
        ForEach(this.screen.servers, (server: AppServerSnapshot) => {
          BuddyButton({ label: (this.serverId === server.serverId ? '✓ ' : '') + server.displayName,
            secondary: this.serverId !== server.serverId,
            isEnabled: server.health === AppServerHealth.Connected,
            action: () => this.selectServer(server.serverId) })
          if (this.serverId === server.serverId) {
            ForEach(server.agentRuntimes, (agent: AgentRuntimeInfo) => {
              BuddyButton({ label: (this.runtime === agent.kind ? '✓ ' : '') + agent.displayName,
                secondary: this.runtime !== agent.kind, isEnabled: agent.available,
                action: () => { this.runtime = agent.kind; this.options = new TaskOptions(); } })
            }, (agent: AgentRuntimeInfo) => agent.kind + ':' + (this.runtime === agent.kind))
          }
        }, (server: AppServerSnapshot) => server.serverId + ':' + (this.serverId === server.serverId) + ':' + server.health)
        TextInput({ placeholder: '电脑上的项目目录（可选）', text: this.path })
          .onChange((value: string) => { this.path = value; })
        TextArea({ placeholder: '想让搭子做什么？', text: this.prompt }).height(160)
          .onChange((value: string) => { this.prompt = value; })
        Button(this.advanced ? '收起任务设置' : '模型与权限设置').backgroundColor(BuddyColor.surface).fontColor(BuddyColor.text)
          .onClick(() => { this.advanced = !this.advanced; })
        if (this.advanced) {
          TaskOptionsForm({ options: this.options,
            models: runtimeModels(this.screen.servers.find(server => server.serverId === this.serverId)?.availableModels ?? [], this.runtime),
            allowPermissions: AppModel.shared.runtimeCapabilities(this.runtime)?.supportsThreadPermissionOverrides ?? false,
            changed: (options: TaskOptions) => { this.options = options; } })
        }
        BuddyButton({ label: this.creating ? '正在创建…' : '开始任务', isEnabled: !!this.serverId && !this.screen.busy,
          action: () => { this.start(); } })
      }.padding(BuddySpacing.xl).width('100%')
    }.width('100%').height('100%').backgroundColor(BuddyColor.background)
  }
}
