import { AppReviewTarget } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { BuddyButton } from '../designsystem/BuddyButton';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct TaskReviewForm {
  @Prop screen: ScreenState;
  started: () => void = () => {};
  @State kind: number = 0;
  @State branch: string = '';
  @State commit: string = '';
  @State instructions: string = '';
  private labels: string[] = ['未提交的改动', '对比指定分支', '指定提交', '自定义审查'];

  private valid(): boolean { return this.kind === 0 || !![this.branch, this.commit, this.instructions][this.kind - 1]?.trim(); }
  private async start(): Promise<void> {
    const key = this.screen.thread?.key;
    if (!key || !this.valid()) return;
    let target: AppReviewTarget = { tag: 'UncommittedChanges' };
    if (this.kind === 1) target = { tag: 'BaseBranch', branch: this.branch.trim() };
    else if (this.kind === 2) target = { tag: 'Commit', sha: this.commit.trim(), title: undefined };
    else if (this.kind === 3) target = { tag: 'Custom', instructions: this.instructions.trim() };
    if (await AppModel.shared.taskActions()?.review(key, target)) this.started();
  }

  build() {
    Column({ space: BuddySpacing.md }) {
      Text('代码审查').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      Text(this.screen.thread?.info.cwd || '使用任务目录').fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      Select(this.labels.map(value => ({ value }))).selected(this.kind).value(this.labels[this.kind]).width('100%')
        .onSelect((index: number) => { this.kind = index; })
      if (this.kind === 1) { TextInput({ placeholder: '基准分支名称', text: this.branch }).onChange(value => { this.branch = value; }) }
      if (this.kind === 2) { TextInput({ placeholder: '提交 SHA', text: this.commit }).onChange(value => { this.commit = value; }) }
      if (this.kind === 3) {
        TextArea({ placeholder: '审查范围和要求', text: this.instructions }).height(140).onChange(value => { this.instructions = value; })
      }
      Text('审查结果会显示在当前任务中。').fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      BuddyButton({ label: '开始审查', isEnabled: this.valid() && !this.screen.busy && !this.screen.thread?.activeTurnId,
        action: () => { void this.start(); } })
    }.width('100%')
  }
}
