import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { TaskOptions, runtimeModels, approvalLabel, sandboxLabel } from '../state/TaskOptions';
import { TaskOptionsForm } from './TaskOptionsForm';
import { BuddyButton } from '../designsystem/BuddyButton';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct TaskSettingsSheet {
  @Prop screen: ScreenState;
  close: () => void = () => {};
  @State name: string = '';
  @State options: TaskOptions = new TaskOptions();

  aboutToAppear(): void {
    const thread = this.screen.thread;
    if (!thread) return;
    this.name = thread.info.title || '';
    this.options = AppModel.shared.taskOptions(thread.key);
    void AppModel.shared.refreshModels(thread.key.serverId);
  }

  async save(): Promise<void> {
    const key = this.screen.thread?.key;
    if (key && await AppModel.shared.applyTaskOptions(key, this.options)) this.close();
  }

  async rename(): Promise<void> {
    const key = this.screen.thread?.key;
    if (key && await AppModel.shared.renameThread(key, this.name)) this.close();
  }

  async fork(): Promise<void> {
    const key = this.screen.thread?.key;
    if (key && await AppModel.shared.forkThread(key)) this.close();
  }

  archive(): void {
    const key = this.screen.thread?.key;
    if (!key) return;
    this.getUIContext().showAlertDialog({ title: '归档任务？', message: '任务会从当前列表移除，历史记录仍保留在电脑上。',
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: { value: '归档', action: () => {
        void AppModel.shared.archiveThread(key).then(succeeded => { if (succeeded) this.close(); });
      } } });
  }

  build() {
    Scroll() {
      Column({ space: BuddySpacing.md }) {
        Text('任务设置').fontSize(BuddyText.title).fontColor(BuddyColor.text).width('100%')
        if (this.screen.error) { Text(this.screen.error).fontColor(BuddyColor.error).width('100%') }
        Text(this.screen.thread?.info.cwd || '使用主机默认目录').fontSize(BuddyText.caption)
          .fontColor(BuddyColor.secondary).copyOption(CopyOptions.LocalDevice).width('100%')
        TextInput({ placeholder: '任务名称', text: this.name }).onChange((value: string) => { this.name = value; })
        BuddyButton({ label: '重命名', secondary: true, isEnabled: !!this.name.trim() && !this.screen.busy,
          action: () => { this.rename(); } })
        Text('主机报告的模型：' + (this.screen.thread?.model || '未提供') + '\n主机报告的推理强度：' + (this.screen.thread?.reasoningEffort || '未提供'))
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
        Text('当前审批：' + approvalLabel(this.screen.thread?.effectiveApprovalPolicy) +
          '\n当前文件权限：' + sandboxLabel(this.screen.thread?.effectiveSandboxPolicy))
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
        Text('下方选择的模型与推理强度用于下一条消息，主机的任务记录可能延后更新。')
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
        TaskOptionsForm({ options: this.options,
          currentModel: this.screen.thread?.model || '',
          models: runtimeModels(this.screen.servers.find(server => server.serverId === this.screen.thread?.key.serverId)?.availableModels ?? [],
            this.screen.thread?.agentRuntimeKind || ''),
          allowPermissions: AppModel.shared.runtimeCapabilities(this.screen.thread?.agentRuntimeKind || '')?.supportsThreadPermissionOverrides ?? false,
          lockEffort: !!AppModel.shared.runtimeCapabilities(this.screen.thread?.agentRuntimeKind || '')?.locksReasoningEffortAfterActivity && this.screen.messages.length > 0,
          changed: (options: TaskOptions) => { this.options = options; } })
        if (this.screen.thread?.activeTurnId) {
          Text('当前任务正在运行，回复完成后可应用设置。').fontColor(BuddyColor.secondary).width('100%')
        }
        BuddyButton({ label: '应用设置', isEnabled: !this.screen.busy && !this.screen.thread?.activeTurnId,
          action: () => { this.save(); } })
        Divider().color(BuddyColor.border)
        BuddyButton({ label: '从这里分叉新任务', secondary: true, isEnabled: !this.screen.busy && !this.screen.thread?.activeTurnId,
          action: () => { this.fork(); } })
        BuddyButton({ label: '归档任务', secondary: true, isEnabled: !this.screen.busy && !this.screen.thread?.activeTurnId,
          action: () => { this.archive(); } })
      }.padding(BuddySpacing.xl).width('100%')
    }.width('100%').height('100%').backgroundColor(BuddyColor.background)
  }
}
