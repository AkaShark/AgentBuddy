import { SavedServerRecord } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { ScreenState, ProjectView } from '../state/ViewState';
import { BuddyColor, BuddySpacing, BuddyRadius, BuddyText, BuddySize } from '../designsystem/BuddyTokens';
import { BuddyButton } from '../designsystem/BuddyButton';
import { AddHostSheet } from './HostSheet';
import { NewTaskSheet } from './NewTaskSheet';
import { hostHealthLabel } from '../state/HostPresentation';
import { AccountSheet } from './AccountSheet';
import { TaskListPanel } from './TaskListPanel';

@Component
export struct HomeScreen {
  @Prop screen: ScreenState;
  @State tab: number = 0;
  @State addHost: boolean = false;
  @State newTask: boolean = false;
  @State projectPath: string = '';
  @State projectServer: string = '';
  @State accountOpen: boolean = false;

  @Builder
  empty(title: string, detail: string) {
    Column({ space: BuddySpacing.md }) {
      Image($r('app.media.brand_logo')).width(BuddySize.control).height(BuddySize.control)
      Text(title).fontSize(BuddyText.heading).fontColor(BuddyColor.text)
      Text(detail).fontSize(BuddyText.body).fontColor(BuddyColor.secondary).textAlign(TextAlign.Center)
    }.width('100%').padding(BuddySpacing.xxl)
  }

  @Builder
  projectRow(project: ProjectView) {
    Column({ space: BuddySpacing.xs }) {
      Text(project.path).fontSize(BuddyText.body).fontColor(BuddyColor.text).width('100%')
      Text(`${project.count} 个任务`).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      BuddyButton({ label: '查看项目任务', secondary: true, isEnabled: !this.screen.busy, action: () => {
        void AppModel.shared.homeActions()?.select({ selectedServerId: project.serverId, selectedProjectId: project.id })
          .then(ok => { if (ok) { AppModel.shared.setHomeView(1, ''); this.tab = 0; } });
      } })
      BuddyButton({ label: '在此创建任务', secondary: true, action: () => {
        this.projectPath = project.path; this.projectServer = project.serverId; this.newTask = true;
      } })
    }.width('100%').padding(BuddySpacing.lg).backgroundColor(BuddyColor.surface).borderRadius(BuddyRadius.card)
  }

  @Builder
  hostRow(host: SavedServerRecord) {
    Column({ space: BuddySpacing.sm }) {
      Text(host.name).fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      Text(host.hostname).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      Text(hostHealthLabel(this.screen.servers.find(server => server.serverId === host.id)))
        .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      Row({ space: BuddySpacing.sm }) {
        BuddyButton({ label: '连接', isEnabled: !this.screen.busy,
          action: () => AppModel.shared.reconnectHost(host.id) })
        BuddyButton({ label: '移除', secondary: true, action: () => {
          this.getUIContext().showAlertDialog({ title: '移除主机？', message: '电脑上的任务会保留。',
            primaryButton: { value: '取消', action: () => {} },
            secondaryButton: { value: '移除', action: () => AppModel.shared.forgetHost(host.id) } });
        } })
      }
    }.width('100%').padding(BuddySpacing.lg).backgroundColor(BuddyColor.surface).borderRadius(BuddyRadius.card)
  }

  build() {
    Column({ space: BuddySpacing.md }) {
      Row({ space: BuddySpacing.sm }) {
        Image($r('app.media.brand_logo')).width(BuddySize.brand).height(BuddySize.brand)
        Text('搭子').fontSize(BuddyText.title).fontWeight(FontWeight.Bold).fontColor(BuddyColor.text).layoutWeight(1)
        Button('刷新').fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .enabled(!this.screen.busy).onClick(() => AppModel.shared.refreshSessions())
        Button('添加主机').fontColor(BuddyColor.onBrand).backgroundColor(BuddyColor.brand)
          .onClick(() => { this.addHost = true; })
          .bindSheet($$this.addHost, this.hostSheet(), { height: SheetSize.LARGE, showClose: true })
      }.width('100%')
      Row({ space: BuddySpacing.xs }) {
        ForEach(['任务', '项目', '主机'], (label: string, index: number) => {
          Button(label).layoutWeight(1).fontSize(BuddyText.body)
            .backgroundColor(this.tab === index ? BuddyColor.brand : BuddyColor.surface)
            .fontColor(this.tab === index ? BuddyColor.onBrand : BuddyColor.secondary)
            .onClick(() => { this.tab = index; })
        })
      }.width('100%')
      if (this.tab === 2 && this.screen.servers.length > 0) {
        Button('账户与用量').fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .onClick(() => { this.accountOpen = true; })
          .bindSheet($$this.accountOpen, this.accountSheet(), { height: SheetSize.LARGE, showClose: true })
      }
      if (this.tab === 2) {
        Column({ space: BuddySpacing.xs }) {
          Text(this.screen.notificationStatus).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary)
          BuddyButton({ label: '开启 / 检查任务通知', secondary: true,
            action: () => AppModel.shared.enableNotifications() })
        }.width('100%')
      }
      if (this.tab === 0) { TaskListPanel({ screen: this.screen }).layoutWeight(1) }
      else { Scroll() {
        Column({ space: BuddySpacing.sm }) {
          if (this.tab === 1) {
            if (this.screen.projects.length === 0) { this.empty('项目', '连接主机后，在这里查看任务所属的项目目录。') }
            ForEach(this.screen.projects, (project: ProjectView) => { this.projectRow(project) }, (project: ProjectView) => project.id + ':' + project.count)
          } else {
            if (this.screen.hosts.length === 0) { this.empty('把电脑带在身边', '添加运行搭子主机端的电脑，继续你的工作。') }
            ForEach(this.screen.hosts, (host: SavedServerRecord) => { this.hostRow(host) }, (host: SavedServerRecord) =>
              host.id + ':' + host.name + ':' + host.hostname + ':' + hostHealthLabel(this.screen.servers.find(server => server.serverId === host.id)))
          }
        }.width('100%')
      }.layoutWeight(1).width('100%').align(Alignment.Top) }
      BuddyButton({ label: '＋ 新任务', isEnabled: !this.screen.busy,
        action: () => { this.projectPath = ''; this.projectServer = ''; this.newTask = true; } })
    }.padding(BuddySpacing.xl).width('100%').height('100%')
      .bindSheet($$this.newTask, this.taskSheet(), { height: SheetSize.LARGE, showClose: true })
  }

  @Builder hostSheet() { AddHostSheet({ screen: this.screen, close: () => { this.addHost = false; } }) }
  @Builder accountSheet() { AccountSheet({ screen: this.screen }) }
  @Builder taskSheet() { NewTaskSheet({ screen: this.screen, initialPath: this.projectPath,
    initialServer: this.projectServer, close: () => { this.newTask = false; } }) }
}
