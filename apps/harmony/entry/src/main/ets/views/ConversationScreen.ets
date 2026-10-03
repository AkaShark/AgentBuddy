import { PendingApproval, PendingUserInputRequest, AppModeKind } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { ScreenState, MessageView, MessageBlock } from '../state/ViewState';
import { BuddyColor, BuddySpacing, BuddyRadius, BuddyText, BuddySize } from '../designsystem/BuddyTokens';
import { BuddyButton } from '../designsystem/BuddyButton';
import { QuestionCard } from './QuestionCard';
import { TaskSettingsSheet } from './TaskSettingsSheet';
import { TaskActionsSheet } from './TaskActionsSheet';
import { MessageActionsSheet } from './MessageActionsSheet';
import { goalStatusLabel } from '../state/TaskPresentation';
import { MarkdownText } from './MarkdownText';
import { ConversationImage, RemoteConversationImage } from './ConversationImage';
import common from '@ohos.app.ability.common';
import { ImageAttachment, pickImages } from '../state/ImageAttachment';

@Component
export struct ConversationScreen {
  @Prop @Watch('screenChanged') screen: ScreenState;
  @State draft: string = '';
  @State settings: boolean = false;
  @State actions: boolean = false;
  @State actionsTab: number = 0;
  @State followLatest: boolean = true;
  @State images: ImageAttachment[] = [];
  @State picking: boolean = false;
  @State attachmentError: string = '';
  @State messageActions: boolean = false;
  @State selectedMessage: MessageView = new MessageView();
  private scroller: Scroller = new Scroller();
  private scrollTimer: number = -1;
  private visibleStart: number = 0;
  private threadId: string = '';

  aboutToAppear(): void { this.screenChanged(); }
  aboutToDisappear(): void { clearTimeout(this.scrollTimer); }

  private screenChanged(): void {
    const key = JSON.stringify(this.screen.thread?.key);
    if (key !== this.threadId) {
      this.threadId = key; this.followLatest = true;
      this.draft = this.screen.thread ? AppModel.shared.composerDraft(this.screen.thread.key) : '';
      this.images = this.screen.thread ? AppModel.shared.composerImages(this.screen.thread.key) : [];
      this.attachmentError = '';
      this.messageActions = false;
    }
    if (this.followLatest) this.scheduleLatest();
  }

  private scheduleLatest(): void {
    clearTimeout(this.scrollTimer);
    this.scrollTimer = setTimeout(() => {
      if (this.followLatest) this.scroller.scrollEdge(Edge.Bottom);
    }, 60);
  }

  private async loadOlder(): Promise<void> {
    this.followLatest = false;
    clearTimeout(this.scrollTimer);
    const anchor = this.screen.messages[Math.max(0, this.visibleStart - 1)]?.id;
    const key = this.threadId;
    await AppModel.shared.loadOlderTurns();
    if (key !== this.threadId) return;
    this.scrollTimer = setTimeout(() => {
      const index = this.screen.messages.findIndex(message => message.id === anchor);
      if (index >= 0) this.scroller.scrollToIndex(index + (this.screen.thread?.olderTurnsCursor ? 1 : 0), false, ScrollAlign.START);
    }, 60);
  }

  async send(): Promise<void> {
    const key = this.screen.thread?.key;
    if (!key) return;
    const sent = this.draft;
    const sentImages = this.images.slice();
    const succeeded = await AppModel.shared.send(sent, sentImages);
    if (succeeded) {
      if (AppModel.shared.composerDraft(key) === sent) AppModel.shared.saveComposerDraft(key, '');
      const remaining = AppModel.shared.composerImages(key).filter(image => !sentImages.some(sent => sent.id === image.id));
      AppModel.shared.saveComposerImages(key, remaining);
      if (JSON.stringify(key) === this.threadId) {
        this.draft = AppModel.shared.composerDraft(key); this.images = remaining;
        this.followLatest = true; this.scheduleLatest();
      }
    }
  }

  private conversationTitle(): string {
    const key = this.screen.thread?.key;
    return this.screen.thread?.info.title || this.screen.tasks.find(task =>
      task.key.serverId === key?.serverId && task.key.threadId === key?.threadId)?.title || '任务';
  }

  private async attach(fromFiles: boolean): Promise<void> {
    const key = this.screen.thread?.key;
    if (!key || this.picking || this.images.length >= 4) return;
    this.picking = true; this.attachmentError = '';
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const selected = await pickImages(context, fromFiles, 4 - this.images.length);
      const next = AppModel.shared.composerImages(key).concat(selected).slice(0, 4);
      AppModel.shared.saveComposerImages(key, next);
      if (JSON.stringify(key) === this.threadId) this.images = next;
    } catch (error) { this.attachmentError = error instanceof Error ? error.message : '无法读取图片，请重试。'; }
    finally { this.picking = false; }
  }

  private removeImage(id: string): void {
    this.images = this.images.filter(image => image.id !== id);
    if (this.screen.thread) AppModel.shared.saveComposerImages(this.screen.thread.key, this.images);
  }

  @Builder
  message(item: MessageView) {
    Column({ space: BuddySpacing.xs }) {
      if (item.title) {
        Text(item.title).fontSize(BuddyText.caption).fontWeight(FontWeight.Medium)
          .fontColor(BuddyColor.secondary).width('100%')
      }
      if (item.remoteImagePath) {
        RemoteConversationImage({ serverId: this.screen.thread?.key.serverId ?? '', path: item.remoteImagePath,
          layoutReady: () => { if (this.followLatest) this.scheduleLatest(); } })
      } else if (!item.code && item.role !== 'user' && item.text) {
        MarkdownText({ text: item.text, layoutReady: () => { if (this.followLatest) this.scheduleLatest(); } })
      } else {
        ForEach(item.blocks, (block: MessageBlock) => {
        Column({ space: BuddySpacing.xs }) {
          if (block.language) { Text(block.language).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%') }
          if (block.text) {
            Text(block.text).fontSize(block.code ? BuddyText.caption : BuddyText.body)
              .fontFamily(block.code ? 'monospace' : 'HarmonyOS Sans').copyOption(CopyOptions.LocalDevice)
              .fontColor(item.role === 'user' ? BuddyColor.onBrand : BuddyColor.text).width('100%')
          }
          if (block.image) { Image(block.image).width('100%').constraintSize({ maxHeight: 320 }).objectFit(ImageFit.Contain) }
        }.width('100%').padding(block.code ? BuddySpacing.sm : 0)
          .backgroundColor(block.code ? BuddyColor.background : Color.Transparent).borderRadius(BuddyRadius.control)
        })
      }
      ForEach(item.images, (uri: string) => { ConversationImage({ source: uri }) })
      if (item.role === 'user') {
        Button('消息操作').fontSize(BuddyText.caption).fontColor(BuddyColor.onBrand)
          .backgroundColor(Color.Transparent).enabled(!this.screen.busy && !this.screen.thread?.activeTurnId)
          .onClick(() => { this.selectedMessage = item; AppModel.shared.clearError(); this.messageActions = true; })
      }
    }.width('100%').padding(BuddySpacing.md).borderRadius(BuddyRadius.card)
      .backgroundColor(item.role === 'user' ? BuddyColor.brand : BuddyColor.surface)
  }

  @Builder
  approval(approval: PendingApproval) {
    Column({ space: BuddySpacing.sm }) {
      Text('需要你的批准').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      Text(approval.reason || approval.command || approval.path || '请确认是否允许继续执行。')
        .fontColor(BuddyColor.text).copyOption(CopyOptions.LocalDevice).width('100%')
      if (approval.command && approval.reason) {
        Text(approval.command).fontFamily('monospace').fontColor(BuddyColor.secondary).width('100%')
      }
      if (approval.cwd) { Text('目录：' + approval.cwd).fontColor(BuddyColor.text).width('100%') }
      if (approval.path) { Text('文件：' + approval.path).fontColor(BuddyColor.text).width('100%') }
      if (approval.grantRoot) { Text('授权范围：' + approval.grantRoot).fontColor(BuddyColor.text).width('100%') }
      Row({ space: BuddySpacing.sm }) {
        BuddyButton({ label: '允许一次', isEnabled: !this.screen.busy, action: () => AppModel.shared.approve(approval.id, true) })
        BuddyButton({ label: '拒绝', secondary: true, isEnabled: !this.screen.busy,
          action: () => AppModel.shared.approve(approval.id, false) })
      }
    }.padding(BuddySpacing.md).width('100%').backgroundColor(BuddyColor.brand).borderRadius(BuddyRadius.card)
  }

  build() {
    Column({ space: BuddySpacing.sm }) {
      Row({ space: BuddySpacing.sm }) {
        Button('返回').onClick(() => AppModel.shared.closeThread())
          .backgroundColor(BuddyColor.surface).fontColor(BuddyColor.text)
        Text(this.conversationTitle()).fontSize(BuddyText.heading)
          .fontColor(BuddyColor.text).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).layoutWeight(1)
        Button('操作').onClick(() => { this.actionsTab = 0; this.actions = true; })
          .fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .bindSheet($$this.actions, this.actionsSheet(), { height: SheetSize.LARGE, showClose: true })
        Button('设置').onClick(() => { this.settings = true; })
          .fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .bindSheet($$this.settings, this.settingsSheet(), { height: SheetSize.LARGE, showClose: true })
        if (this.screen.thread?.activeTurnId) {
          Button('停止').onClick(() => AppModel.shared.interrupt()).fontColor(BuddyColor.error).backgroundColor(BuddyColor.surface)
        }
      }.width('100%')
      if (this.screen.thread?.goal) {
        Button('目标 · ' + goalStatusLabel(this.screen.thread?.goal?.status) + ' · ' + this.screen.thread?.goal?.objective)
          .fontSize(BuddyText.caption).fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .width('100%').labelStyle({ maxLines: 2 })
          .onClick(() => { this.actionsTab = 1; this.actions = true; })
      }
      List({ space: BuddySpacing.sm, scroller: this.scroller }) {
        if (this.screen.thread?.olderTurnsCursor) {
          ListItem() { BuddyButton({ label: '加载更早消息', secondary: true, isEnabled: !this.screen.busy,
            action: () => { this.loadOlder(); } }) }
        }
        ForEach(this.screen.messages, (item: MessageView) => {
          ListItem() { this.message(item) }
        }, (item: MessageView) => item.id + ':' + item.renderRevision)
        ForEach(this.screen.approvals.filter((value: PendingApproval) =>
          value.serverId === this.screen.thread?.key.serverId && value.threadId === this.screen.thread?.key.threadId),
          (value: PendingApproval) => { ListItem() { this.approval(value) } }, (value: PendingApproval) => value.id)
        ForEach(this.screen.questions.filter((value: PendingUserInputRequest) =>
          value.serverId === this.screen.thread?.key.serverId && value.threadId === this.screen.thread?.key.threadId),
          (value: PendingUserInputRequest) => {
            ListItem() { QuestionCard({ request: value, busy: this.screen.busy }) }
          }, (value: PendingUserInputRequest) => value.id)
        if (this.screen.thread?.activeTurnId) {
          ListItem() {
            Row({ space: BuddySpacing.sm }) {
              LoadingProgress().width(BuddySize.icon).height(BuddySize.icon)
              Text('搭子正在处理…').fontColor(BuddyColor.secondary)
            }.padding(BuddySpacing.md)
          }
        }
      }.layoutWeight(1).width('100%')
        .onScrollIndex((start: number) => { this.visibleStart = start; })
        .onScrollFrameBegin((offset: number, state: ScrollState) => {
          if (state === ScrollState.Scroll && offset < 0) this.followLatest = false;
          return { offsetRemain: offset };
        })
        .onScrollStop(() => { if (this.scroller.isAtEnd()) this.followLatest = true; })
        .onAreaChange(() => { if (this.followLatest) this.scheduleLatest(); })
      if (!this.followLatest) {
        Button('回到最新消息').fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .onClick(() => { this.followLatest = true; this.scheduleLatest(); })
      }
      if (this.attachmentError) { Text(this.attachmentError).fontSize(BuddyText.caption).fontColor(BuddyColor.error) }
      if (this.screen.thread?.pendingPlanImplementationPrompt) {
        Button('计划已就绪 · 查看并执行').fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .onClick(() => { this.actionsTab = 0; this.actions = true; })
      } else if (this.screen.thread?.collaborationMode === AppModeKind.Plan) {
        Text('计划模式').fontSize(BuddyText.caption).fontColor(BuddyColor.secondary)
      }
      if (this.screen.thread?.queuedFollowUps.length) {
        Button('待发消息 · ' + this.screen.thread!.queuedFollowUps.length).fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .onClick(() => { this.actionsTab = 3; this.actions = true; })
      }
      if (this.images.length > 0) {
        Scroll() {
          Row({ space: BuddySpacing.sm }) {
            ForEach(this.images, (attachment: ImageAttachment, index: number) => {
              Column({ space: BuddySpacing.xs }) {
                Image(attachment.dataUri).width(72).height(72).objectFit(ImageFit.Cover).borderRadius(BuddyRadius.control)
                Button('移除图片' + (index + 1)).fontSize(BuddyText.caption).fontColor(BuddyColor.text)
                  .backgroundColor(BuddyColor.surface).enabled(!this.screen.busy)
                  .onClick(() => this.removeImage(attachment.id))
              }
            }, (attachment: ImageAttachment) => attachment.id)
          }
        }.scrollable(ScrollDirection.Horizontal).width('100%')
      }
      Row({ space: BuddySpacing.xs }) {
        Button(this.picking ? '…' : '＋').width(BuddySize.control).height(BuddySize.control)
          .fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .accessibilityText('添加图片').enabled(!this.picking && !this.screen.busy && this.images.length < 4)
          .bindMenu([{ value: '相册图片', action: () => { this.attach(false); } },
            { value: '文件中的图片', action: () => { this.attach(true); } }])
        TextArea({ placeholder: '给搭子发消息…', text: this.draft }).layoutWeight(1)
          .constraintSize({ minHeight: BuddySize.control, maxHeight: 144 })
          .onChange((value: string) => {
            this.draft = value;
            if (this.screen.thread) AppModel.shared.saveComposerDraft(this.screen.thread.key, value);
          })
        BuddyButton({ label: '发送', isEnabled: (!!this.draft.trim() || this.images.length > 0) && !this.screen.busy && !this.picking,
          action: () => { this.send(); } })
      }.width('100%')
    }.padding(BuddySpacing.md).width('100%').height('100%')
      .bindSheet($$this.messageActions, this.messageSheet(), { height: SheetSize.LARGE, showClose: true })
  }

  @Builder settingsSheet() { TaskSettingsSheet({ screen: this.screen, close: () => { this.settings = false; } }) }
  @Builder actionsSheet() { TaskActionsSheet({ screen: this.screen, initialTab: this.actionsTab, close: () => { this.actions = false; } }) }
  @Builder messageSheet() {
    if (this.screen.thread) {
      MessageActionsSheet({ screen: this.screen, message: this.selectedMessage, threadKey: this.screen.thread.key,
        close: () => { this.messageActions = false; }, edited: () => {
          if (!this.screen.thread) return;
          this.draft = AppModel.shared.composerDraft(this.screen.thread.key);
          this.images = AppModel.shared.composerImages(this.screen.thread.key);
          this.followLatest = true; this.scheduleLatest();
        } })
    }
  }
}
