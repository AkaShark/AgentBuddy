import { UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { AppModel } from '../state/AppModel';
import { PushController } from '../state/PushController';

export default class EntryAbility extends UIAbility {
  private notifications = new PushController(this, text => AppModel.shared.notificationStatus(text),
    key => AppModel.shared.openNotification(key));

  onCreate(want: Want): void {
    AppModel.shared.attachNotifications(this.notifications);
    this.notifications.receive(want);
  }

  onNewWant(want: Want): void { this.notifications.receive(want); }

  onWindowStageCreate(stage: window.WindowStage): void {
    AppModel.shared.initialize(this.context);
    stage.loadContent('pages/Index');
  }

  onForeground(): void {
    AppModel.shared.resume();
  }

  onBackground(): void {
    AppModel.shared.background();
  }

  onDestroy(): void {
    this.notifications.detach();
    AppModel.shared.detach();
  }
}
