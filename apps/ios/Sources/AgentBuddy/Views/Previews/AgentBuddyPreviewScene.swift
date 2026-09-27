import Foundation

#if DEBUG
import SwiftUI

@MainActor
struct AgentBuddyPreviewScene<Content: View>: View {
    @State private var appModel: AppModel
    @State private var appState: AppState
    // `.shared` is defined identically on both the iOS and Catalyst
    // versions of `VoiceRuntimeController`. Calling the synthesized
    // init directly (`VoiceRuntimeController()`) trips a scope-
    // resolution hiccup on Catalyst 26 + Swift 6 around the
    // `@MainActor @Observable` class — the singleton is the safe path
    // and gives us the same environment object.
    @State private var voiceRuntime = VoiceRuntimeController.shared

    private let includeBackground: Bool
    private let content: Content

    init(
        appModel: AppModel? = nil,
        appState: AppState? = nil,
        includeBackground: Bool = true,
        @ViewBuilder content: () -> Content
    ) {
        _appModel = State(initialValue: appModel ?? AgentBuddyPreviewData.makeConversationAppModel())
        _appState = State(initialValue: appState ?? AgentBuddyPreviewData.makeAppState())
        self.includeBackground = includeBackground
        self.content = content()
    }

    var body: some View {
        ZStack {
            if includeBackground {
                AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
            }
            content
        }
        .environment(appModel)
        .environment(voiceRuntime)
        .environment(appState)
        .environment(ThemeManager.shared)
        .environment(WallpaperManager.shared)
        .environment(ApprovalCoordinator())
    }
}
#endif
