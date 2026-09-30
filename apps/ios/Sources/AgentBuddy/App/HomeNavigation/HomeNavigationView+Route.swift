import Foundation

extension HomeNavigationView {
    enum HomeNavigationRoute: Hashable {
        /// All tasks screen. A non-nil `project` limits it to that project's
        /// tasks (the Projects tab's "See all tasks").
        case sessions(serverId: String, title: String, project: AppProject? = nil)
        case conversation(ThreadKey)
        case realtimeVoice(ThreadKey)
        case conversationInfo(ThreadKey)
        case wallpaperSelection(ThreadKey)
        case wallpaperAdjust(ThreadKey)
        case serverInfo(serverId: String)
        case serverWallpaperSelection(serverId: String)
        case serverWallpaperAdjust(serverId: String)
        case replayRecording(URL)
        /// Hero composer landing in the detail pane. Pushed by the sidebar
        /// "+" button on regular-width surfaces. On send, replaces itself
        /// with `.conversation(key)` so the bottom composer visually
        /// inherits the hero composer's position.
        case newThread
        /// Saved apps list — always-visible.
        case appsList
        /// Saved-app detail, pushed when the user taps a home-screen thread
        /// that has saved apps (or when routed from the AppsList).
        case savedApp(appId: String)
        /// Local on-device terminal backed by the shared Rust terminal session.
        case terminal(preferredAlleycatNodeId: String?)
    }
}
