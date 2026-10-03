import { AppAlleycatAgentInfo } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';
import { BuddyButton } from '../designsystem/BuddyButton';
import { scanCore, scanBarcode } from '@kit.ScanKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { DiscoveryPanel } from './DiscoveryPanel';
import { SshLoginForm } from './SshLoginForm';

@Component
export struct AddHostSheet {
  @Prop screen: ScreenState;
  close: () => void = () => {};
  @State payload: string = '';
  @State name: string = '';
  @State url: string = '';
  @State scanError: string = '';
  @State ssh: boolean = false;
  @State sshHost: string = '';
  @State sshPort: number = 22;
  @State sshName: string = '';

  async pair(agent: AppAlleycatAgentInfo): Promise<void> {
    if (await AppModel.shared.pairAgent(agent)) this.close();
  }

  async connect(): Promise<void> {
    if (await AppModel.shared.connectUrl(this.name, this.url)) this.close();
  }

  scan(): void {
    try {
      const options: scanBarcode.ScanOptions = { scanTypes: [scanCore.ScanType.QR_CODE], enableMultiMode: false };
      const context = this.getUIContext().getHostContext();
      if (!context) return;
      scanBarcode.startScanForResult(context, options, (error: BusinessError, result: scanBarcode.ScanResult) => {
        if (error) { this.scanError = '扫码未完成，可重试或粘贴配对内容。'; return; }
        this.payload = result.originalValue;
        AppModel.shared.inspectPair(this.payload);
      });
    } catch (error) { this.scanError = '扫码未完成，可重试或粘贴配对内容。'; }
  }

  build() {
    Scroll() {
      Column({ space: BuddySpacing.md }) {
        Text('添加主机').fontSize(BuddyText.title).fontColor(BuddyColor.text).width('100%')
        if (this.screen.error) { Text(this.screen.error).fontColor(BuddyColor.error).width('100%') }
        if (this.ssh) {
          SshLoginForm({ initialHost: this.sshHost, initialPort: this.sshPort, initialName: this.sshName,
            connected: () => this.close(), cancelled: () => { this.ssh = false; } })
        } else {
        DiscoveryPanel({ busy: this.screen.busy, connected: () => this.close(), sshSelected: server => {
          this.sshHost = server.host; this.sshPort = server.sshPort ?? 22; this.sshName = server.displayName; this.ssh = true;
        } })
        Divider().color(BuddyColor.border)
        Text('在电脑搭子主机端打开配对二维码。').fontColor(BuddyColor.secondary).width('100%')
        BuddyButton({ label: '扫描配对二维码', isEnabled: !this.screen.busy, action: () => { this.scan(); } })
        if (this.scanError) { Text(this.scanError).fontColor(BuddyColor.secondary) }
        TextArea({ placeholder: '或粘贴配对内容', text: this.payload }).height(120)
          .onChange((value: string) => { this.payload = value; })
        BuddyButton({ label: this.screen.busy ? '正在连接…' : '读取主机', isEnabled: !!this.payload.trim() && !this.screen.busy,
          action: () => AppModel.shared.inspectPair(this.payload) })
        ForEach(this.screen.agents, (agent: AppAlleycatAgentInfo) => {
          BuddyButton({ label: agent.displayName + (agent.available ? '' : ' · 不可用'),
            isEnabled: agent.available && !this.screen.busy, action: () => { this.pair(agent); } })
        }, (agent: AppAlleycatAgentInfo) => agent.name)
        Divider().color(BuddyColor.border)
        Text('通过服务器地址连接').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
        TextInput({ placeholder: '主机名称', text: this.name }).onChange((value: string) => { this.name = value; })
        TextInput({ placeholder: 'ws://主机地址:端口 或 wss://…', text: this.url })
          .onChange((value: string) => { this.url = value; })
        BuddyButton({ label: '连接服务器', isEnabled: !!this.url.trim() && !this.screen.busy,
          action: () => { this.connect(); } })
        BuddyButton({ label: '手动添加 SSH 主机', secondary: true, isEnabled: !this.screen.busy,
          action: () => { this.sshHost = ''; this.sshPort = 22; this.sshName = ''; this.ssh = true; } })
        }
      }.width('100%').padding(BuddySpacing.xl)
    }.width('100%').height('100%').backgroundColor(BuddyColor.background)
  }
}
