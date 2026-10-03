import { ThreadKey } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { MessageView, ScreenState } from '../state/ViewState';
import { BuddyButton } from '../designsystem/BuddyButton';
import { BuddyColor, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct MessageActionsSheet {
  @Prop screen: ScreenState;
  message: MessageView = new MessageView();
  threadKey: ThreadKey = { serverId: '', threadId: '' };
  close: () => void = () => {};
  edited: () => void = () => {};
  @State confirmingEdit: boolean = false;

  private async run(edit: boolean): Promise<void> {
    if (await AppModel.shared.actOnMessage(this.threadKey, this.message.id, edit)) {
      if (edit) this.edited();
      this.close();
    }
  }

  build() {
    Column({ space: BuddySpacing.md }) {
      Text('消息操作').fontSize(BuddyText.title).fontColor(BuddyColor.text).width('100%')
      Text(this.message.text || '图片消息').fontSize(BuddyText.body).fontColor(BuddyColor.text)
        .maxLines(5).textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
      if (this.message.images.length) {
        Text('附带 ' + this.message.images.length + ' 张图片').fontColor(BuddyColor.secondary).width('100%')
      }
      if (this.screen.error) {
        Scroll() { Text(this.screen.error).fontColor(BuddyColor.error).width('100%').copyOption(CopyOptions.LocalDevice) }
          .constraintSize({ maxHeight: 160 }).width('100%')
      }
      if (this.confirmingEdit) {
        Text('将移除这条消息和后续对话，把原消息及图片放入输入框。已修改的文件不会还原。')
          .fontColor(BuddyColor.text).width('100%')
        if (AppModel.shared.composerDraft(this.threadKey) || AppModel.shared.composerImages(this.threadKey).length) {
          Text('当前未发送的草稿将被原消息替换。').fontColor(BuddyColor.error).width('100%')
        }
        BuddyButton({ label: '确认回退并编辑', isEnabled: !this.screen.busy && !this.screen.thread?.activeTurnId,
          action: () => { void this.run(true); } })
        BuddyButton({ label: '取消编辑', secondary: true, isEnabled: !this.screen.busy,
          action: () => { this.confirmingEdit = false; } })
      } else {
        BuddyButton({ label: '编辑这条消息', isEnabled: !this.screen.busy && !this.screen.thread?.activeTurnId,
          action: () => { this.confirmingEdit = true; } })
        Text('分叉会保留这条消息及其回复，在新任务中继续。原任务保持不变。')
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
        BuddyButton({ label: '从这里分叉', secondary: true, isEnabled: !this.screen.busy && !this.screen.thread?.activeTurnId,
          action: () => { void this.run(false); } })
      }
    }.padding(BuddySpacing.xl).width('100%').height('100%').backgroundColor(BuddyColor.background)
  }
}
