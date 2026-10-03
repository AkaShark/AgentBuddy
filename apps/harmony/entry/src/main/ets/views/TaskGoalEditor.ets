import { AppThreadGoalStatus } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { parseGoalBudget } from '../state/GoalBudget';
import { goalStatusLabel } from '../state/TaskPresentation';
import { BuddyButton } from '../designsystem/BuddyButton';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct TaskGoalEditor {
  @Prop screen: ScreenState;
  @State objective: string = '';
  @State budget: string = '';
  @State localError: string = '';

  aboutToAppear(): void { this.fill(); void this.reload(); }
  private fill(): void {
    this.objective = this.screen.thread?.goal?.objective ?? '';
    this.budget = this.screen.thread?.goal?.tokenBudget?.toString() ?? '';
  }
  private async reload(): Promise<void> {
    const key = this.screen.thread?.key;
    if (!key) return;
    if (await AppModel.shared.taskActions()?.refreshGoal(key)) {
      const current = AppModel.shared.view().thread;
      if (current?.key.threadId !== key.threadId || current?.key.serverId !== key.serverId) return;
      this.objective = current.goal?.objective ?? ''; this.budget = current.goal?.tokenBudget?.toString() ?? '';
    }
  }
  private async save(status?: AppThreadGoalStatus): Promise<void> {
    const key = this.screen.thread?.key;
    if (!key) return;
    this.localError = '';
    try {
      if (!this.objective.trim()) throw new Error('请填写目标。');
      const tokenBudget = parseGoalBudget(this.budget);
      await AppModel.shared.taskActions()?.setGoal(key, { threadId: key.threadId,
        objective: this.objective.trim(), status, tokenBudget });
    } catch (error) { this.localError = error instanceof Error ? error.message : '请检查目标与预算。'; }
  }
  private status(status: AppThreadGoalStatus): void {
    const key = this.screen.thread?.key;
    if (key) void AppModel.shared.taskActions()?.setGoal(key, { threadId: key.threadId, status });
  }
  private clear(): void {
    const key = this.screen.thread?.key;
    if (!key) return;
    this.getUIContext().showAlertDialog({ title: '清除任务目标？', message: '仅清除当前目标，保留任务和聊天记录。',
      primaryButton: { value: '取消', action: () => {} }, secondaryButton: { value: '清除目标', action: () => {
        void AppModel.shared.taskActions()?.clearGoal(key).then(ok => { if (ok) { this.objective = ''; this.budget = ''; } });
      } } });
  }

  build() {
    Column({ space: BuddySpacing.md }) {
      Text('目标 · ' + goalStatusLabel(this.screen.thread?.goal?.status))
        .fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      if (this.screen.thread?.goal) {
        Text('已用 Token：' + this.screen.thread!.goal!.tokensUsed.toString() + '\n运行秒数：' +
          this.screen.thread!.goal!.timeUsedSeconds.toString()).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      }
      if (this.localError) { Text(this.localError).fontColor(BuddyColor.error).width('100%') }
      TextArea({ placeholder: '希望搭子持续完成的目标', text: this.objective }).height(140).enabled(!this.screen.busy)
        .onChange(value => { this.objective = value; })
      TextInput({ placeholder: 'Token 预算（可选）', text: this.budget }).type(InputType.Number).enabled(!this.screen.busy)
        .onChange(value => { this.budget = value; })
      Text(this.screen.thread?.goal ? '预算留空表示保持现有预算。' : '开始后主机会持续推进目标，可随时暂停。')
        .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      BuddyButton({ label: this.screen.thread?.goal ? '保存目标与预算' : '开始目标',
        isEnabled: !this.screen.busy && !!this.objective.trim(),
        action: () => { void this.save(this.screen.thread?.goal ? undefined : AppThreadGoalStatus.Active); } })
      if (!this.screen.thread?.goal) {
        BuddyButton({ label: '保存为暂停目标', secondary: true, isEnabled: !this.screen.busy && !!this.objective.trim(),
          action: () => { void this.save(AppThreadGoalStatus.Paused); } })
      } else {
        if (this.screen.thread!.goal!.status !== AppThreadGoalStatus.Complete) {
          BuddyButton({ label: this.screen.thread!.goal!.status === AppThreadGoalStatus.Active ? '暂停目标' : '恢复目标',
            secondary: true, isEnabled: !this.screen.busy, action: () => {
              this.status(this.screen.thread!.goal!.status === AppThreadGoalStatus.Active ? AppThreadGoalStatus.Paused : AppThreadGoalStatus.Active);
            } })
          BuddyButton({ label: '标记目标完成', secondary: true, isEnabled: !this.screen.busy,
            action: () => this.status(AppThreadGoalStatus.Complete) })
        }
        BuddyButton({ label: '清除目标', secondary: true, isEnabled: !this.screen.busy, action: () => this.clear() })
      }
      BuddyButton({ label: '重新读取目标', secondary: true, isEnabled: !this.screen.busy, action: () => { void this.reload(); } })
    }.width('100%')
  }
}
