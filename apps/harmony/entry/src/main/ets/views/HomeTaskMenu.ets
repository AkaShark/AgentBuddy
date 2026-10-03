import { HomeTaskItem, taskKey } from '../state/HomeTaskList';
import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { BuddyButton } from '../designsystem/BuddyButton';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct HomeTaskMenu {
  item: HomeTaskItem = new HomeTaskItem();
  @Prop screen: ScreenState;
  close: () => void = () => {};
  @State name: string = '';
  aboutToAppear(): void { this.name = this.item.placeholder ? '' : this.item.title; }
  private pinned(): boolean { return this.screen.homePreferences.pinnedThreads.some(key => taskKey(key) === taskKey(this.item.key)); }
  private hidden(): boolean { return this.screen.homePreferences.hiddenThreads.some(key => taskKey(key) === taskKey(this.item.key)); }
  private active(): boolean { return this.screen.tasks.find(task => taskKey(task.key) === taskKey(this.item.key))?.hasActiveTurn ?? this.item.active; }
  private async pin(): Promise<void> {
    if (await AppModel.shared.homeActions()?.pin(this.item.key, !this.pinned())) this.close();
  }
  private async hide(): Promise<void> {
    if (await AppModel.shared.homeActions()?.hide(this.item.key, !this.hidden())) this.close();
  }
  private async rename(): Promise<void> {
    if (await AppModel.shared.renameThread(this.item.key, this.name)) this.close();
  }
  private async fork(): Promise<void> {
    if (await AppModel.shared.forkThread(this.item.key)) this.close();
  }
  private archive(): void {
    this.getUIContext().showAlertDialog({ title: '归档任务？', message: '历史记录仍保留在电脑上。',
      primaryButton: { value: '取消', action: () => {} }, secondaryButton: { value: '归档', action: () => {
        void AppModel.shared.archiveThread(this.item.key).then(ok => { if (ok) this.close(); });
      } } });
  }
  build() {
    Scroll() {
      Column({ space: BuddySpacing.md }) {
        Text('任务管理').fontSize(BuddyText.title).fontColor(BuddyColor.text).width('100%')
        Text(this.item.title).fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
        Text(this.item.host).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
        if (this.screen.error) { Text(this.screen.error).fontColor(BuddyColor.error).width('100%') }
        BuddyButton({ label: this.pinned() ? '取消固定' : '固定到常用', secondary: true,
          isEnabled: !this.screen.busy, action: () => { void this.pin(); } })
        BuddyButton({ label: this.hidden() ? '恢复显示' : '隐藏任务', secondary: true,
          isEnabled: !this.screen.busy, action: () => { void this.hide(); } })
        Text('固定和隐藏仅影响这台手机的列表。隐藏不会归档任务，可在“已隐藏”中恢复。')
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
        if (!this.item.placeholder) {
          Divider().color(BuddyColor.border)
          TextInput({ placeholder: '任务名称', text: this.name }).onChange(value => { this.name = value; })
          BuddyButton({ label: '重命名', secondary: true, isEnabled: !this.screen.busy && !!this.name.trim(),
            action: () => { void this.rename(); } })
          BuddyButton({ label: '分叉新任务', secondary: true, isEnabled: !this.screen.busy && !this.active(),
            action: () => { void this.fork(); } })
          BuddyButton({ label: '归档任务', secondary: true, isEnabled: !this.screen.busy && !this.active(),
            action: () => this.archive() })
        }
      }.padding(BuddySpacing.xl).width('100%')
    }.height('100%').width('100%').backgroundColor(BuddyColor.background)
  }
}
