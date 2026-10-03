import { BuddyColor, BuddyRadius, BuddySize, BuddyText } from './BuddyTokens';

@Component
export struct BuddyButton {
  @Prop label: string = '';
  @Prop isEnabled: boolean = true;
  @Prop secondary: boolean = false;
  action: () => void = () => {};

  build() {
    Button(this.label, { type: ButtonType.Normal })
      .height(BuddySize.control).borderRadius(BuddyRadius.button)
      .fontSize(BuddyText.body).fontWeight(FontWeight.Medium)
      .backgroundColor(this.secondary ? BuddyColor.surface : BuddyColor.action)
      .fontColor(this.secondary ? BuddyColor.text : BuddyColor.onAction)
      .enabled(this.isEnabled).opacity(this.isEnabled ? 1 : 0.5)
      .onClick(this.action)
  }
}
