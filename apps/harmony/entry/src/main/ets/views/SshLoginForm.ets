import common from '@ohos.app.ability.common';
import picker from '@ohos.file.picker';
import fs from '@ohos.file.fs';
import { AgentAvailabilityStatus, AppSshHostKeyMismatchKind, RemoteAgentAvailability,
  SshAuthMethodRecord, SshCredentialRecord } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { SshConnectionState } from '../state/SshConnection';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';
import { BuddyButton } from '../designsystem/BuddyButton';

@Component
export struct SshLoginForm {
  initialHost: string = '';
  initialPort: number = 22;
  initialName: string = '';
  connected: () => void = () => {};
  cancelled: () => void = () => {};
  @State host: string = '';
  @State port: string = '22';
  @State username: string = '';
  @State password: string = '';
  @State privateKey: string = '';
  @State passphrase: string = '';
  @State useKey: boolean = false;
  @State remember: boolean = false;
  @State unlockKeychain: boolean = false;
  @State localError: string = '';
  @State notice: string = '';
  @State state: SshConnectionState = new SshConnectionState();

  aboutToAppear(): void {
    this.host = this.initialHost; this.port = this.initialPort.toString();
    AppModel.shared.sshConnection()?.observe(state => {
      this.state = state;
      if (state.connected) { this.clearSecrets(); this.connected(); }
    });
  }
  aboutToDisappear(): void { this.clearSecrets(); void AppModel.shared.sshConnection()?.cancel(); }
  private clearSecrets(): void { this.password = ''; this.privateKey = ''; this.passphrase = ''; }

  private async importKey(): Promise<void> {
    this.localError = '';
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const uris = await new picker.DocumentViewPicker(context).select({ maxSelectNumber: 1 });
      if (!uris.length) return;
      const file = fs.openSync(uris[0], fs.OpenMode.READ_ONLY);
      try {
        if (fs.statSync(file.fd).size > 32 * 1024) throw new Error('私钥文件超过 32 KB。');
        this.privateKey = fs.readTextSync(uris[0]);
      } finally { fs.closeSync(file); }
    } catch (error) { this.localError = error instanceof Error ? error.message : '无法读取私钥文件'; }
  }

  private loadSaved(): void {
    this.localError = ''; this.notice = '';
    try {
      const value = AppModel.shared.sshConnection()?.security.readCredential(this.host.trim(), Number(this.port));
      if (!value) { this.localError = '此主机没有保存的登录信息。'; return; }
      this.username = value.username; this.password = value.password ?? ''; this.privateKey = value.privateKeyPem ?? '';
      this.passphrase = value.passphrase ?? ''; this.useKey = value.authMethod === SshAuthMethodRecord.Key;
      this.unlockKeychain = value.unlockMacosKeychain; this.remember = true;
    } catch (_) { this.localError = '无法读取此主机的登录信息，请重新输入。'; }
  }

  private forgetSaved(): void {
    const host = this.host.trim(); const port = Number(this.port);
    if (!host || !Number.isInteger(port) || port < 1 || port > 65535) {
      this.localError = '请先填写要清除的主机地址和端口。'; return;
    }
    this.getUIContext().showAlertDialog({ title: '忘记 SSH 登录与密钥？',
      message: host + ':' + port + ' 的保存密码、私钥和信任指纹将被清除。下次连接需要重新核对指纹。',
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: { value: '忘记此主机', action: () => {
        try {
          const security = AppModel.shared.sshConnection()?.security;
          if (!security) return;
          security.saveCredential(host, port, undefined); security.remove(host, port);
          AppModel.shared.sshConnection()?.clearError();
          this.clearSecrets(); this.localError = ''; this.notice = '已清除保存的 SSH 登录信息和主机密钥。';
        } catch (_) { this.localError = '未能完整清除安全存储，请重试。'; }
      } } });
  }

  private connect(): void {
    const port = Number(this.port);
    if (!this.host.trim() || !this.username.trim() || !Number.isInteger(port) || port < 1 || port > 65535) {
      this.localError = '请填写主机、用户名和 1–65535 范围内的端口。'; return;
    }
    if (this.useKey ? !this.privateKey : !this.password) { this.localError = '请输入密码或导入私钥。'; return; }
    this.localError = ''; this.notice = '';
    const credential: SshCredentialRecord = { username: this.username.trim(),
      authMethod: this.useKey ? SshAuthMethodRecord.Key : SshAuthMethodRecord.Password,
      password: this.useKey ? undefined : this.password, privateKeyPem: this.useKey ? this.privateKey : undefined,
      passphrase: this.useKey && this.passphrase ? this.passphrase : undefined,
      unlockMacosKeychain: !this.useKey && this.unlockKeychain };
    void AppModel.shared.sshConnection()?.connect(this.host, port, this.initialName, credential, this.remember);
  }

  build() {
    Column({ space: BuddySpacing.md }) {
      Text('通过 SSH 连接').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      if (this.localError || this.state.error) { Text(this.localError || this.state.error).fontColor(BuddyColor.error) }
      if (this.notice) { Text(this.notice).fontColor(BuddyColor.secondary) }
      if (this.state.busy) {
        LoadingProgress().width(32).height(32)
        Text(this.state.label).fontColor(BuddyColor.secondary)
      }
      if (this.state.mismatch) {
        Text(this.state.mismatch.kind === AppSshHostKeyMismatchKind.Changed ? '主机密钥已变化' : '确认 SSH 主机密钥')
          .fontSize(BuddyText.heading).fontColor(BuddyColor.text)
        Text(this.state.mismatch.host + ':' + this.state.mismatch.port).fontColor(BuddyColor.text)
        Text(this.state.mismatch.fingerprint).fontFamily('monospace').fontSize(BuddyText.caption)
          .fontColor(BuddyColor.text).copyOption(CopyOptions.LocalDevice)
        Text(this.state.mismatch.kind === AppSshHostKeyMismatchKind.TrustStoreUnavailable
          ? '无法读取已保存的密钥。忘记后将重新核对主机指纹。' : '请与电脑上的主机密钥指纹核对一致后再继续。')
          .fontColor(BuddyColor.secondary).fontSize(BuddyText.caption)
        BuddyButton({ label: this.state.mismatch.kind === AppSshHostKeyMismatchKind.TrustStoreUnavailable ? '忘记密钥并重新检查' : '信任此密钥并继续',
          isEnabled: !this.state.busy, action: () => { void AppModel.shared.sshConnection()?.trustAndRetry(); } })
      } else if (this.state.sessionReady) {
        Text('SSH 已连接，选择主机上的智能体').fontColor(BuddyColor.text)
        ForEach(this.state.agents, (agent: RemoteAgentAvailability) => {
          BuddyButton({ label: (AppModel.shared.sshConnection()?.label(agent.kind) ?? agent.kind) +
            (agent.status === AgentAvailabilityStatus.AgentCliMissing ? ' · 未安装' :
              agent.status === AgentAvailabilityStatus.WindowsNotYetSupported ? ' · 此主机暂不支持' : ''),
            isEnabled: !this.state.busy && agent.status === AgentAvailabilityStatus.Available &&
              (agent.kind === 'codex' || !!AppModel.shared.sshConnection()?.supportsBridge(agent)),
            action: () => { void AppModel.shared.sshConnection()?.choose(agent.kind); } })
        }, (agent: RemoteAgentAvailability) => agent.kind)
        Text('连接后将在主机启动对应的任务服务。未安装的智能体需要先在电脑上安装。')
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary)
      } else if (!this.state.busy) {
        TextInput({ placeholder: 'SSH 主机地址', text: this.host }).onChange(value => { this.host = value; })
        TextInput({ placeholder: 'SSH 端口', text: this.port }).type(InputType.Number).onChange(value => { this.port = value; })
        TextInput({ placeholder: 'SSH 用户名', text: this.username }).onChange(value => { this.username = value; })
        Button('读取保存的登录信息').fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface).onClick(() => this.loadSaved())
        Button('忘记保存的登录与密钥').fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface).onClick(() => this.forgetSaved())
        Row({ space: BuddySpacing.sm }) {
          Button('密码登录').fontColor(BuddyColor.text).backgroundColor(this.useKey ? BuddyColor.surface : BuddyColor.brand)
            .onClick(() => { this.useKey = false; })
          Button('私钥登录').fontColor(BuddyColor.text).backgroundColor(this.useKey ? BuddyColor.brand : BuddyColor.surface)
            .onClick(() => { this.useKey = true; })
        }
        if (this.useKey) {
          BuddyButton({ label: this.privateKey ? '更换 SSH 私钥文件' : '导入 SSH 私钥文件', secondary: true, action: () => { void this.importKey(); } })
          if (this.privateKey) { Text('私钥已载入').fontColor(BuddyColor.secondary) }
          TextInput({ placeholder: '私钥口令（可选）', text: this.passphrase }).type(InputType.Password)
            .onChange(value => { this.passphrase = value; })
        } else {
          TextInput({ placeholder: 'SSH 密码', text: this.password }).type(InputType.Password).onChange(value => { this.password = value; })
          Row({ space: BuddySpacing.sm }) {
            Toggle({ type: ToggleType.Switch, isOn: this.unlockKeychain }).onChange(value => { this.unlockKeychain = value; })
            Text('同时解锁 macOS 登录钥匙串').fontSize(BuddyText.caption).fontColor(BuddyColor.text)
          }
        }
        Row({ space: BuddySpacing.sm }) {
          Toggle({ type: ToggleType.Switch, isOn: this.remember }).onChange(value => { this.remember = value; })
          Text('安全保存登录信息以便重连').fontSize(BuddyText.caption).fontColor(BuddyColor.text)
        }
        BuddyButton({ label: '连接并检查智能体', action: () => this.connect() })
      }
      BuddyButton({ label: '返回连接方式', secondary: true, action: () => { this.cancelled(); } })
    }.width('100%')
  }
}
