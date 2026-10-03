import { ModelInfo, ReasoningEffort, ReasoningEffortOption } from '@agentbuddy/core';
import { TaskOptions, approvalOptions, sandboxOptions, effortLabel } from '../state/TaskOptions';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct TaskOptionsForm {
  @Prop options: TaskOptions;
  @Prop models: ModelInfo[] = [];
  @Prop currentModel: string = '';
  @Prop allowPermissions: boolean = false;
  @Prop lockEffort: boolean = false;
  changed: (options: TaskOptions) => void = () => {};

  private changeModel(index: number): void {
    const next = new TaskOptions(this.options);
    next.model = index > 0 ? this.models[index - 1].model : '';
    next.effort = index > 0 ? this.models[index - 1].defaultReasoningEffort : undefined;
    this.changed(next);
  }

  private efforts(): ReasoningEffortOption[] {
    return this.models.find(model => model.model === (this.options.model || this.currentModel))?.supportedReasoningEfforts ?? [];
  }

  private modelChoices(): SelectOption[] {
    const choices: SelectOption[] = [{ value: '沿用当前模型' }];
    return choices.concat(this.models.map((model: ModelInfo): SelectOption => ({ value: model.displayName })));
  }

  private effortChoices(): SelectOption[] {
    const choices: SelectOption[] = [{ value: '沿用当前强度' }];
    return choices.concat(this.efforts().map((option: ReasoningEffortOption): SelectOption => ({ value: effortLabel(option.reasoningEffort) })));
  }

  build() {
    Column({ space: BuddySpacing.sm }) {
      Text('模型').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      Select(this.modelChoices())
        .selected(this.models.findIndex((model: ModelInfo) => model.model === this.options.model) + 1)
        .value(this.models.find((model: ModelInfo) => model.model === this.options.model)?.displayName || '沿用当前模型')
        .onSelect((index: number) => this.changeModel(index)).width('100%')
      if (this.models.length === 0) {
        Text('暂无可选模型，使用主机当前模型。').fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      }
      if (this.efforts().length > 0 && !this.lockEffort) {
        Text('推理强度').fontSize(BuddyText.body).fontColor(BuddyColor.text).width('100%')
        Select(this.effortChoices())
          .selected(this.efforts().findIndex((option: ReasoningEffortOption) => option.reasoningEffort === this.options.effort) + 1)
          .value(this.options.effort === undefined ? '沿用当前强度' : effortLabel(this.options.effort))
          .onSelect((index: number) => {
            const next = new TaskOptions(this.options);
            next.effort = index > 0 ? this.efforts()[index - 1].reasoningEffort : undefined;
            this.changed(next);
          }).width('100%')
      }
      if (this.lockEffort) {
        Text('此运行时在任务开始后锁定推理强度。').fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      }
      if (this.allowPermissions) {
        Text('审批').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
        Select(approvalOptions.map(option => ({ value: option.label })))
          .selected(Math.max(0, approvalOptions.findIndex(option => option.value?.tag === this.options.approval?.tag)))
          .value(approvalOptions.find(option => option.value?.tag === this.options.approval?.tag)?.label ?? approvalOptions[0].label)
          .onSelect((index: number) => {
            const next = new TaskOptions(this.options); next.approval = approvalOptions[index].value;
            this.changed(next);
          }).width('100%')
        Text('文件权限').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
        Select(sandboxOptions.map(option => ({ value: option.label })))
          .selected(Math.max(0, sandboxOptions.findIndex(option => option.value === this.options.sandbox)))
          .value(sandboxOptions.find(option => option.value === this.options.sandbox)?.label ?? sandboxOptions[0].label)
          .onSelect((index: number) => {
            const next = new TaskOptions(this.options); next.sandbox = sandboxOptions[index].value;
            this.changed(next);
          }).width('100%')
        Text('权限变更会应用到此任务。完全访问允许修改项目外的文件；从不询问会跳过审批。')
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      }
    }.width('100%')
  }
}
