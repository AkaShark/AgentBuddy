import { AppDiscoveredServer } from '@agentbuddy/core';
import { NetworkDiscovery, DiscoveryState, discoveryLabel } from '../state/NetworkDiscovery';
import { BuddyColor, BuddySpacing, BuddyText, BuddyRadius } from '../designsystem/BuddyTokens';
import { BuddyButton } from '../designsystem/BuddyButton';
import { AppModel } from '../state/AppModel';

@Component
export struct DiscoveryPanel {
  @Prop busy: boolean = false;
  @State discovery: DiscoveryState = new DiscoveryState();
  connected: () => void = () => {};
  sshSelected: (server: AppDiscoveredServer) => void = () => {};
  private controller = new NetworkDiscovery();

  aboutToAppear(): void {
    this.controller.observe(state => { this.discovery = state; });
    this.scan();
  }
  aboutToDisappear(): void { this.controller.dispose(); }
  private scan(): void {
    const context = this.getUIContext().getHostContext();
    if (context) void this.controller.start(context);
  }

  build() {
    Column({ space: BuddySpacing.sm }) {
      Row({ space: BuddySpacing.sm }) {
        Text('附近的主机').fontSize(BuddyText.heading).fontColor(BuddyColor.text).layoutWeight(1)
        Button(this.discovery.scanning ? '停止发现' : '重新发现')
          .fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .onClick(() => { if (this.discovery.scanning) this.controller.stop(); else this.scan(); })
      }.width('100%')
      if (this.discovery.scanning) {
        Progress({ value: Math.round(this.discovery.progress * 100), total: 100 }).width('100%')
        Text(this.discovery.label).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary)
      }
      if (this.discovery.error) { Text(this.discovery.error).fontColor(BuddyColor.error) }
      if (!this.discovery.scanning && !this.discovery.servers.length) {
        Text('未发现主机。请确认电脑与手机在同一网络，或使用二维码、服务器地址添加。')
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary)
      }
      ForEach(this.discovery.servers, (server: AppDiscoveredServer) => {
        Column({ space: BuddySpacing.sm }) {
          Text(server.displayName).fontColor(BuddyColor.text).fontSize(BuddyText.body).width('100%')
          Text(server.host + ' · ' + discoveryLabel(server.source) + (server.reachable ? '' : ' · 暂未响应'))
            .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
          ForEach(server.codexPorts, (port: number) => {
            BuddyButton({ label: '连接任务服务 · ' + port, isEnabled: !this.busy,
              action: () => { void AppModel.shared.connectDiscovered(server, port).then(ok => { if (ok) this.connected(); }); } })
          }, (port: number) => port.toString())
          if (server.sshPort) {
            BuddyButton({ label: '通过 SSH 连接 · ' + server.sshPort, secondary: true, isEnabled: !this.busy,
              action: () => this.sshSelected(server) })
          }
        }.padding(BuddySpacing.md).width('100%').backgroundColor(BuddyColor.surface).borderRadius(BuddyRadius.card)
      }, (server: AppDiscoveredServer) => server.id + ':' + server.host + ':' + server.codexPorts.join(',') + ':' +
        server.reachable + ':' + server.displayName + ':' + server.sshPort + ':' + server.source)
    }.width('100%')
  }
}
