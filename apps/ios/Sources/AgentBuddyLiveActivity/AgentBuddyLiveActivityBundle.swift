import SwiftUI
import WidgetKit

@main
struct AgentBuddyLiveActivityBundle: WidgetBundle {
    var body: some Widget {
        CodexTurnLiveActivity()
        CodexVoiceCallLiveActivity()
    }
}
