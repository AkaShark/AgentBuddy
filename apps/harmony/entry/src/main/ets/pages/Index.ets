import { AppModel } from '../state/AppModel';
import { ScreenState } from '../state/ViewState';
import { BuddyColor, BuddySpacing, BuddyText, BuddySize } from '../designsystem/BuddyTokens';
import { HomeScreen } from '../views/HomeScreen';
import { ConversationScreen } from '../views/ConversationScreen';

@Entry
@Component
struct Index {
  @State screen: ScreenState = AppModel.shared.view();

  aboutToAppear(): void {
    AppModel.shared.observe((state: ScreenState) => { this.screen = state; });
  }

  aboutToDisappear(): void { AppModel.shared.unobserve(); }

  onBackPress(): boolean {
    if (this.screen.thread) { AppModel.shared.closeThread(); return true; }
    return false;
  }

  build() {
    Column({ space: BuddySpacing.sm }) {
      if (this.screen.error) {
        Row({ space: BuddySpacing.sm }) {
          Text(this.screen.error).fontSize(BuddyText.caption).fontColor(BuddyColor.error).layoutWeight(1)
            .maxLines(3).textOverflow({ overflow: TextOverflow.Ellipsis }).copyOption(CopyOptions.LocalDevice)
          Button('关闭').onClick(() => AppModel.shared.clearError())
        }.padding(BuddySpacing.sm).backgroundColor(BuddyColor.errorSurface).width('100%')
      }
      if (!this.screen.ready) {
        Column({ space: BuddySpacing.xl }) {
          Image($r('app.media.brand_logo')).width(BuddySize.control).height(BuddySize.control)
          Text('搭子').fontSize(BuddyText.title).fontColor(BuddyColor.text)
          LoadingProgress().width(BuddySize.brand).height(BuddySize.brand)
          Text('正在准备…').fontColor(BuddyColor.secondary)
        }.layoutWeight(1).width('100%').justifyContent(FlexAlign.Center)
      } else if (this.screen.thread) {
        ConversationScreen({ screen: this.screen }).layoutWeight(1)
      } else {
        HomeScreen({ screen: this.screen }).layoutWeight(1)
      }
    }.width('100%').height('100%').backgroundColor(BuddyColor.background)
  }
}
