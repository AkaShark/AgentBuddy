import image from '@ohos.multimedia.image';
import window from '@ohos.window';
import { AppModel } from '../state/AppModel';
import { BuddyColor, BuddyRadius, BuddySpacing, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct ConversationImage {
  // PixelMap has native state: @Prop's deep copy loses that state. Each image
  // node is created for one source (message revision / completed remote load).
  source: string | image.PixelMap = '';
  @Prop ratio: number = 1.5;
  @State expanded: boolean = false;
  @State zoom: number = 1;
  @State offsetX: number = 0;
  @State offsetY: number = 0;
  @State topInset: number = 0;
  @State bottomInset: number = 0;
  private startScale: number = 1;
  private startX: number = 0;
  private startY: number = 0;

  private reset(): void { this.zoom = 1; this.offsetX = 0; this.offsetY = 0; }

  private async openPreview(): Promise<void> {
    const context = this.getUIContext().getHostContext();
    if (!context) return;
    try {
      const current = await window.getLastWindow(context);
      const system = current.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM);
      const navigation = current.getWindowAvoidArea(window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR);
      this.topInset = this.getUIContext().px2vp(system.topRect.height);
      this.bottomInset = this.getUIContext().px2vp(Math.max(system.bottomRect.height, navigation.bottomRect.height));
    } catch (_) { /* Keep the image available if the window is transitioning. */ }
    this.reset(); this.expanded = true;
  }

  build() {
    Image(this.source).width('100%').aspectRatio(this.ratio).constraintSize({ maxHeight: 320 })
      .objectFit(ImageFit.Contain).borderRadius(BuddyRadius.control)
      .accessibilityText('查看大图').onClick(() => { void this.openPreview(); })
      .bindContentCover($$this.expanded, this.preview())
  }

  @Builder preview() {
    Column({ space: BuddySpacing.sm }) {
      Row() {
        Text('图片').fontSize(BuddyText.heading).fontColor(BuddyColor.text).layoutWeight(1)
        Button('关闭图片').fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .onClick(() => { this.expanded = false; })
      }.width('100%')
      Stack() {
        Image(this.source).width('100%').height('100%').objectFit(ImageFit.Contain)
          .scale({ x: this.zoom, y: this.zoom }).translate({ x: this.offsetX, y: this.offsetY })
      }.layoutWeight(1).width('100%').clip(true)
        .gesture(GestureGroup(GestureMode.Parallel,
          PinchGesture().onActionStart(() => { this.startScale = this.zoom; })
            .onActionUpdate((event: GestureEvent) => { this.zoom = Math.max(1, Math.min(5, this.startScale * event.scale)); })
            .onActionEnd(() => { if (this.zoom === 1) this.reset(); }),
          PanGesture().onActionStart(() => { this.startX = this.offsetX; this.startY = this.offsetY; })
            .onActionUpdate((event: GestureEvent) => {
              if (this.zoom > 1) { this.offsetX = this.startX + event.offsetX; this.offsetY = this.startY + event.offsetY; }
            }),
          TapGesture({ count: 2 }).onAction(() => { this.reset(); })
        ))
      Row({ space: BuddySpacing.sm }) {
        Button('缩小').enabled(this.zoom > 1).fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .onClick(() => { this.zoom = Math.max(1, this.zoom / 1.5); if (this.zoom === 1) this.reset(); })
        Button('复原').fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface).onClick(() => this.reset())
        Button('放大').enabled(this.zoom < 5).fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
          .onClick(() => { this.zoom = Math.min(5, this.zoom * 1.5); })
      }
      Text('双指缩放，拖动查看，双击复原').fontSize(BuddyText.caption).fontColor(BuddyColor.secondary)
    }.padding({ left: BuddySpacing.md, right: BuddySpacing.md,
      top: BuddySpacing.md + this.topInset, bottom: BuddySpacing.md + this.bottomInset })
      .width('100%').height('100%').backgroundColor(BuddyColor.background)
  }
}

@Component
export struct RemoteConversationImage {
  @Prop serverId: string = '';
  @Prop path: string = '';
  @State pixel: image.PixelMap | undefined = undefined;
  @State ratio: number = 1.5;
  @State error: string = '';
  @State loading: boolean = false;
  layoutReady: () => void = () => {};
  private generation: number = 0;

  aboutToAppear(): void { void this.load(); }
  aboutToDisappear(): void {
    this.generation++;
    const old = this.pixel; this.pixel = undefined;
    void old?.release().catch(() => {});
  }

  private async load(): Promise<void> {
    const generation = ++this.generation;
    this.error = ''; this.loading = true;
    let source: image.ImageSource | undefined;
    let decoded: image.PixelMap | undefined;
    try {
      const bytes = await AppModel.shared.imageBytes(this.serverId, this.path);
      if (generation !== this.generation) return;
      if (bytes.byteLength > 32 * 1024 * 1024) throw new Error('图片过大，暂无法预览。');
      source = image.createImageSource(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
      const info = await source.getImageInfo();
      const ratio = Math.min(1, 2048 / Math.max(info.size.width, info.size.height));
      decoded = await source.createPixelMap({ desiredSize: { width: Math.max(1, Math.round(info.size.width * ratio)),
        height: Math.max(1, Math.round(info.size.height * ratio)) } });
      if (generation === this.generation) {
        this.ratio = info.size.width / info.size.height;
        this.pixel = decoded; decoded = undefined;
      }
    } catch (error) {
      if (generation === this.generation) this.error = error instanceof Error ? error.message : '图片加载失败';
    } finally {
      await decoded?.release().catch(() => {});
      await source?.release().catch(() => {});
      if (generation === this.generation) { this.loading = false; this.layoutReady(); }
    }
  }

  build() {
    Column({ space: BuddySpacing.sm }) {
      if (this.pixel) {
        ConversationImage({ source: this.pixel, ratio: this.ratio })
      } else if (this.loading) {
        LoadingProgress().width(32).height(32)
        Text('正在加载图片…').fontColor(BuddyColor.secondary)
      } else {
        Text(this.error || '图片不可用').fontColor(BuddyColor.error).fontSize(BuddyText.caption)
        Button('重试加载图片').onClick(() => { void this.load(); })
          .fontColor(BuddyColor.text).backgroundColor(BuddyColor.surface)
      }
      Text(this.path).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).copyOption(CopyOptions.LocalDevice)
    }.width('100%')
  }
}
