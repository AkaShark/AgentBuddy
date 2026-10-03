import common from '@ohos.app.ability.common';
import pasteboard from '@ohos.pasteboard';
import resourceManager from '@ohos.resourceManager';
import { Markdown, MarkdownController } from '@luvi/lv-markdown-in';
import { BuddyColor, BuddyText, BuddyRadius, BuddySpacing } from '../designsystem/BuddyTokens';

@Component
export struct MarkdownText {
  @Prop text: string = '';
  @State failed: boolean = false;
  layoutReady: () => void = () => {};
  private controller: MarkdownController = new MarkdownController();

  aboutToAppear(): void {
    this.controller.setTextColor(BuddyColor.text).setTextSize(BuddyText.body)
      .setTextFontFamily('HarmonyOS Sans').setTextLineHeight(BuddyText.body * 1.5)
      .setTitleColor(BuddyColor.text)
      .setTitleSize([BuddyText.title, BuddyText.heading, BuddyText.heading, BuddyText.body, BuddyText.body, BuddyText.body])
      .setTitleFontWeight(FontWeight.Bold)
      .setInlineCodeColor(BuddyColor.text).setInlineCodeBackgroundColor(BuddyColor.background)
      .setInlineCodeBackgroundRadius(BuddyRadius.control)
      .setQuoteTextColor(BuddyColor.secondary).setQuoteBackgroundColor(BuddyColor.background)
      .setQuoteBorderColor(BuddyColor.brand).setQuoteBorderRadius(BuddyRadius.control)
      .setQuotePadding(BuddySpacing.sm).setHyperlinkTextColor(BuddyColor.link)
      .setTableBackgroundColor(BuddyColor.surface).setTableTitleTextColor(BuddyColor.text);
    this.controller.setTableNormalTextColor(BuddyColor.text);
    this.controller.setTableTitleBackgroundColor(BuddyColor.background)
      .setTableInterleaveBackgroundColor(BuddyColor.surface).setTableActionBackgroundColor(BuddyColor.background)
      .setTableOuterBorderColor(BuddyColor.border).setTableInnerBorderColor(BuddyColor.border)
      .setTableBorderRadius(BuddyRadius.control).setTodoSelectedColor(BuddyColor.action)
      .setUlPointColor(BuddyColor.text).setLineColor(BuddyColor.border)
      .setLatexMathTextSize(BuddyText.body).setHtmlPreviewEnable(false).setHtmlPreviewJavaScriptEnable(false)
      .setMermaidEnable(false).setThreadRenderEnable(false).setSetextHeadingEnable(true)
      .setTextSelectionEnable(true).setCodeBlockBorderRadius(BuddyRadius.control)
      .setCodeCopyListener((value: string) => { this.copy(value); })
      .setTextSelectionCopyListener((value: string) => { this.copy(value); })
      .setTableCopyClickListener((value: string) => { this.copy(value); })
      .setHyperlinkClickListener((_title: string, src: string): boolean => {
        if (/^https?:\/\//i.test(src)) {
          const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
          void context.openLink(src).catch(() => this.toast('无法打开链接'));
        } else { this.toast('此链接暂不支持打开'); }
        return true;
      })
      .setImageLoadProxy((src: string): string =>
        /^(https?:\/\/|data:image\/(png|jpeg|gif|webp);base64,)/i.test(src) ? src : '');
    this.controller.setBlockSpacing(BuddySpacing.sm);
    const manager = this.getUIContext().getHostContext()?.resourceManager;
    if (manager) {
      this.controller.setLatexMathTextColor(manager.getColorSync(BuddyColor.text.id));
      this.controller.setCodeBlockTheme(manager.getConfigurationSync().colorMode === resourceManager.ColorMode.DARK ? 'dark' : 'light');
    }
  }

  private copy(value: string): void {
    void pasteboard.getSystemPasteboard().setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, value))
      .then(() => this.toast('已复制')).catch(() => this.toast('复制失败，请重试'));
  }

  private toast(message: string): void { this.getUIContext().getPromptAction().showToast({ message }); }

  build() {
    if (this.failed) {
      Text(this.text).fontSize(BuddyText.body).fontColor(BuddyColor.text)
        .copyOption(CopyOptions.LocalDevice).width('100%')
    } else {
      Markdown({ text: this.text, controller: this.controller,
        callback: { complete: () => this.layoutReady(), fail: () => { this.failed = true; } } })
        .width('100%').onAreaChange(() => this.layoutReady())
    }
  }
}
