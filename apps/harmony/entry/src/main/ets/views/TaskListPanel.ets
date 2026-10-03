import { AppModel } from '../state/AppModel';
import { ScreenState, ProjectView } from '../state/ViewState';
import { HomeTaskItem, homeTaskItems, taskRenderKey } from '../state/HomeTaskList';
import { TaskListDataSource } from './TaskListDataSource';
import { HomeTaskMenu } from './HomeTaskMenu';
import { BuddyColor, BuddySpacing, BuddyRadius, BuddyText, BuddySize } from '../designsystem/BuddyTokens';

@Component
export struct TaskListPanel {
  @Prop @Watch('refreshRows') screen: ScreenState;
  @State count: number = 0;
  @State menuOpen: boolean = false;
  @State selected: HomeTaskItem = new HomeTaskItem();
  private source = new TaskListDataSource();
  private scroller = new Scroller();
  private filterKey: string = '';

  aboutToAppear(): void { this.refreshRows(); }
  private project(): ProjectView | undefined {
    return this.screen.projects.find(project => project.id === this.screen.homePreferences.homeSelection.selectedProjectId);
  }
  private projects(): ProjectView[] {
    const server = this.screen.homePreferences.homeSelection.selectedServerId;
    return this.screen.projects.filter(project => !server || project.serverId === server);
  }
  private refreshRows(): void {
    const selection = this.screen.homePreferences.homeSelection;
    const filter = JSON.stringify([selection, this.screen.homeScope, this.screen.homeQuery]);
    const items = homeTaskItems(this.screen.tasks, this.screen.servers, this.screen.homePreferences,
      this.screen.homeScope, this.screen.homeQuery, this.project()?.path ?? '');
    this.source.replace(items); this.count = items.length;
    if (this.filterKey && this.filterKey !== filter) this.scroller.scrollEdge(Edge.Top);
    this.filterKey = filter;
  }
  private hostLabel(): string {
    const id = this.screen.homePreferences.homeSelection.selectedServerId;
    return this.screen.servers.find(server => server.serverId === id)?.displayName || '所有主机';
  }
  private hostOptions(): SelectOption[] {
    const options: SelectOption[] = [{ value: '所有主机' }];
    this.screen.servers.forEach(server => options.push({ value: server.displayName }));
    return options;
  }
  private projectOptions(): SelectOption[] {
    const options: SelectOption[] = [{ value: '所有项目' }];
    this.projects().forEach(project => options.push({ value: project.path }));
    return options;
  }
  private selectHost(index: number): void {
    void AppModel.shared.homeActions()?.select({ selectedServerId: index > 0 ? this.screen.servers[index - 1].serverId : undefined });
  }
  private selectProject(index: number): void {
    const project = index > 0 ? this.projects()[index - 1] : undefined;
    void AppModel.shared.homeActions()?.select({ selectedServerId: project?.serverId ?? this.screen.homePreferences.homeSelection.selectedServerId,
      selectedProjectId: project?.id });
  }

  @Builder row(item: HomeTaskItem) {
    Row({ space: BuddySpacing.sm }) {
      Column({ space: BuddySpacing.xs }) {
        Row({ space: BuddySpacing.xs }) {
          Text((item.pinned ? '★ ' : '') + item.title).fontSize(BuddyText.body).fontWeight(FontWeight.Medium)
            .fontColor(BuddyColor.text).maxLines(2).layoutWeight(1)
          if (item.active) { LoadingProgress().width(BuddySize.icon).height(BuddySize.icon) }
        }.width('100%')
        if (item.preview) {
          Text(item.preview).maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis })
            .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
        }
        Text(item.host + (item.runtime ? ' · ' + item.runtime : ''))
          .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      }.layoutWeight(1).onClick(() => { void AppModel.shared.openThread(item.key); })
      Button('更多').fontSize(BuddyText.caption).fontColor(BuddyColor.text).backgroundColor(BuddyColor.background)
        .enabled(!this.screen.busy).onClick(() => { this.selected = item; this.menuOpen = true; })
    }.padding(BuddySpacing.lg).width('100%').backgroundColor(BuddyColor.surface).borderRadius(BuddyRadius.card)
  }

  build() {
    Column({ space: BuddySpacing.sm }) {
      TextInput({ placeholder: '搜索任务', text: this.screen.homeQuery }).height(BuddySize.control)
        .onChange(value => AppModel.shared.setHomeView(this.screen.homeScope, value))
      Row({ space: BuddySpacing.xs }) {
        ForEach(['常用', '全部', '已隐藏'], (label: string, index: number) => {
          Button(label).layoutWeight(1).fontSize(BuddyText.caption)
            .fontColor(this.screen.homeScope === index ? BuddyColor.onBrand : BuddyColor.text)
            .backgroundColor(this.screen.homeScope === index ? BuddyColor.brand : BuddyColor.surface)
            .onClick(() => AppModel.shared.setHomeView(index, this.screen.homeQuery))
        })
      }.width('100%')
      Row({ space: BuddySpacing.xs }) {
        Select(this.hostOptions())
          .selected(this.screen.servers.findIndex(server => server.serverId === this.screen.homePreferences.homeSelection.selectedServerId) + 1)
          .value(this.hostLabel()).layoutWeight(1).font({ size: BuddyText.caption }).enabled(!this.screen.busy)
          .onSelect((index: number) => this.selectHost(index))
        Select(this.projectOptions())
          .selected(this.projects().findIndex(project => project.id === this.screen.homePreferences.homeSelection.selectedProjectId) + 1)
          .value(this.project()?.path || '所有项目').layoutWeight(1).font({ size: BuddyText.caption }).enabled(!this.screen.busy)
          .onSelect((index: number) => this.selectProject(index))
      }.width('100%')
      Text((this.screen.busy ? '正在更新 · ' : '') + this.count + ' 个任务' +
        (this.screen.homeScope === 0 && !this.screen.homeQuery ? (this.screen.homePreferences.pinnedThreads.length ? ' · 固定列表' : ' · 最近 10 个') : ''))
        .fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
      if (this.count === 0) {
        Text(this.screen.homeQuery ? '没有匹配的任务' : this.screen.homeScope === 2 ? '没有已隐藏的任务' :
          '暂无任务，可切换到“全部”或调整主机和项目筛选。')
          .fontSize(BuddyText.body).fontColor(BuddyColor.secondary).padding(BuddySpacing.xl).width('100%')
      }
      List({ space: BuddySpacing.sm, scroller: this.scroller }) {
        LazyForEach(this.source, (item: HomeTaskItem) => { ListItem() { this.row(item) } },
          (item: HomeTaskItem) => taskRenderKey(item))
      }.layoutWeight(1).width('100%').cachedCount(3)
    }.width('100%').height('100%')
      .bindSheet($$this.menuOpen, this.menu(), { height: SheetSize.LARGE, showClose: true })
  }
  @Builder menu() { HomeTaskMenu({ item: this.selected, screen: this.screen, close: () => { this.menuOpen = false; } }) }
}
