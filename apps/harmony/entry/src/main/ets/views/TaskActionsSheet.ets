import { AppCollaborationModePreset, AppModeKind, AppPlanStep, AppQueuedFollowUpPreview, AppQueuedFollowUpKind } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { errorMessage } from '../state/ErrorPresentation';
import { modeLabel, queueLabel, planStepMark } from '../state/TaskPresentation';
import { TaskGoalEditor } from './TaskGoalEditor';
import { TaskReviewForm } from './TaskReviewForm';
import { BuddyButton } from '../designsystem/BuddyButton';
import { BuddyColor, BuddySpacing, BuddyRadius, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct TaskActionsSheet {
  @Prop screen: ScreenState;
  initialTab: number = 0;
  close: () => void = () => {};
  @State tab: number = 0;
  @State modes: AppCollaborationModePreset[] = [];
  @State loading: boolean = false;
  @State localError: string = '';

  aboutToAppear(): void { this.tab = this.initialTab; void this.loadModes(); }
  private async loadModes(): Promise<void> {
    const key = this.screen.thread?.key;
    if (!key || this.screen.thread?.agentRuntimeKind !== 'codex') return;
    this.loading = true;
    try { this.modes = await AppModel.shared.taskActions()?.modes(key.serverId) ?? []; }
    catch (error) { this.localError = errorMessage(error, '无法读取协作模式。'); }
    finally { this.loading = false; }
  }
  private async mode(mode: AppModeKind): Promise<void> {
    const key = this.screen.thread?.key;
    if (key) await AppModel.shared.taskActions()?.setMode(key, mode);
  }
  private async plan(implement: boolean): Promise<void> {
    const key = this.screen.thread?.key;
    if (!key) return;
    const actions = AppModel.shared.taskActions();
    if (implement ? await actions?.implementPlan(key) : await actions?.dismissPlan(key)) this.close();
  }
  private async queued(item: AppQueuedFollowUpPreview, steer: boolean): Promise<void> {
    const key = this.screen.thread?.key;
    if (!key) return;
    if (steer) await AppModel.shared.taskActions()?.steerQueued(key, item.id);
    else await AppModel.shared.taskActions()?.deleteQueued(key, item.id);
  }

  build() {
    Column({ space: BuddySpacing.md }) {
      Text('任务操作').fontSize(BuddyText.title).fontColor(BuddyColor.text).width('100%')
      Row({ space: BuddySpacing.xs }) {
        ForEach(['协作', '目标', '审查', '队列'], (label: string, index: number) => {
          Button(label).layoutWeight(1).fontSize(BuddyText.body)
            .fontColor(this.tab === index ? BuddyColor.onBrand : BuddyColor.text)
            .backgroundColor(this.tab === index ? BuddyColor.brand : BuddyColor.surface)
            .onClick(() => { this.tab = index; })
        })
      }.width('100%')
      if (this.screen.error || this.localError) { Text(this.screen.error || this.localError).fontColor(BuddyColor.error).width('100%') }
      Scroll() {
        Column({ space: BuddySpacing.md }) {
          if (this.tab < 3 && this.screen.thread?.agentRuntimeKind !== 'codex') {
            Text('此智能体尚未提供这些任务操作。').fontColor(BuddyColor.secondary).width('100%')
          } else if (this.tab === 0) {
            Text('当前模式：' + modeLabel(this.screen.thread?.collaborationMode)).fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
            if (this.loading) { LoadingProgress() }
            ForEach(this.modes, (preset: AppCollaborationModePreset) => {
              BuddyButton({ label: '切换到' + modeLabel(preset.kind) + '模式',
                secondary: this.screen.thread?.collaborationMode !== preset.kind,
                isEnabled: !this.screen.busy && !this.screen.thread?.activeTurnId,
                action: () => { void this.mode(preset.kind); } })
            }, (preset: AppCollaborationModePreset) => preset.kind.toString())
            Text('计划模式用于讨论方案；确认计划后再开始执行。').fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
            if (this.screen.thread?.activePlanProgress) {
              Text('计划进度').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
              ForEach(this.screen.thread?.activePlanProgress?.plan ?? [], (step: AppPlanStep) => {
                Text(planStepMark(step.status) + ' ' + step.step).fontColor(BuddyColor.text).width('100%')
              }, (step: AppPlanStep) => step.step + ':' + step.status)
            }
            if (this.screen.thread?.pendingPlanImplementationPrompt) {
              BuddyButton({ label: '开始执行计划', isEnabled: !this.screen.busy && !this.screen.thread?.activeTurnId,
                action: () => { void this.plan(true); } })
              BuddyButton({ label: '暂不执行计划', secondary: true, isEnabled: !this.screen.busy, action: () => { void this.plan(false); } })
            }
          } else if (this.tab === 1) { TaskGoalEditor({ screen: this.screen }) }
          else if (this.tab === 2) { TaskReviewForm({ screen: this.screen, started: () => this.close() }) }
          else {
            Text('待发消息').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
            if (!this.screen.thread?.queuedFollowUps.length) { Text('当前没有排队消息。').fontColor(BuddyColor.secondary).width('100%') }
            ForEach(this.screen.thread?.queuedFollowUps ?? [], (item: AppQueuedFollowUpPreview) => {
              Column({ space: BuddySpacing.sm }) {
                Text(queueLabel(item.kind)).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
                Text(item.text).fontColor(BuddyColor.text).width('100%').copyOption(CopyOptions.LocalDevice)
                BuddyButton({ label: '立即插入当前回复', isEnabled: !this.screen.busy && !!this.screen.thread?.activeTurnId && item.kind === AppQueuedFollowUpKind.Message,
                  action: () => { void this.queued(item, true); } })
                BuddyButton({ label: '删除待发消息', secondary: true, isEnabled: !this.screen.busy,
                  action: () => { void this.queued(item, false); } })
              }.padding(BuddySpacing.md).width('100%').backgroundColor(BuddyColor.surface).borderRadius(BuddyRadius.card)
            }, (item: AppQueuedFollowUpPreview) => item.id + ':' + item.kind + ':' + item.text)
          }
        }.width('100%')
      }.layoutWeight(1).width('100%')
    }.padding(BuddySpacing.xl).width('100%').height('100%').backgroundColor(BuddyColor.background)
  }
}
