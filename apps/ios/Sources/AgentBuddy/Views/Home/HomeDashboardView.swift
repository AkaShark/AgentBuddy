import SwiftUI
import UIKit

/// Which chrome layer the dashboard renders with.
///
///  - `.full`: the app's landing page — animated logo in the principal
///    toolbar item, zoom toggle, and the full `HomeBottomBar` composer
///    docked along the bottom. This is what iPhone compact and Catalyst
///    non-split use today.
///  - `.sidebar`: the trimmed projection used in the iPad / Catalyst
///    `NavigationSplitView` sidebar. Branding, zoom, and the bottom
///    composer are stripped; toolbar trailing gains a "+" that fires
///    `onNewThread` so the detail pane can host the hero composer.
enum HomeDashboardChrome {
    case full
    case sidebar
}

struct HomeDashboardView: View {
    var chrome: HomeDashboardChrome = .full
    let recentSessions: [HomeDashboardRecentSession]
    let allSessions: [HomeDashboardRecentSession]
    let pinnedThreadKeys: [SavedThreadsStore.PinnedKey]
    let connectedServers: [HomeDashboardServer]
    let projects: [AppProject]
    let selectedServerId: String?
    let selectedProject: AppProject?
    let openingRecentSessionKey: ThreadKey?
    let onOpenRecentSession: @MainActor (HomeDashboardRecentSession) async -> Void
    let onSelectServer: (HomeDashboardServer) -> Void
    let onAddServer: () -> Void
    let onOpenProjectPicker: () -> Void
    let onThreadCreated: (ThreadKey) -> Void
    let onShowSettings: () -> Void
    /// Optional: surface an "Apps" button alongside Settings. Wired by the
    /// hosting navigation when a "Saved Apps" launcher should be exposed.
    var onShowApps: (() -> Void)? = nil
    var onShowTerminal: (() -> Void)? = nil
    let onPinThread: (ThreadKey) -> Void
    let onUnpinThread: (ThreadKey) -> Void
    let onHideThread: (ThreadKey) -> Void
    /// Sidebar-only: fired when the user taps the "+" in the toolbar.
    var onNewThread: (() -> Void)? = nil
    /// Resume a single thread so the connection has a live listener. Dashboard
    /// orchestrates the parallel calls and tracks per-row state so the left
    /// indicator can reflect it.
    var onHydrateThread: ((ThreadKey, Bool) async -> Void)? = nil
    var onDeleteThread: ((ThreadKey) async -> Void)? = nil
    var onReconnectServer: ((HomeDashboardServer) -> Void)? = nil
    var onRestartAppServer: ((HomeDashboardServer) -> Void)? = nil
    var onDisconnectServer: ((String) -> Void)? = nil
    var onRenameServer: ((String, String) -> Void)? = nil
    var onOpenRecording: ((URL) -> Void)? = nil
    /// Fires when the user commits a quick reply from the swipe action.
    /// Caller should call `appModel.startTurn` against the thread.
    var onSendReply: (@MainActor (ThreadKey, String) async -> Void)? = nil
    /// Cancels the active turn on the given thread. Caller looks up the
    /// thread's `activeTurnId` and calls `appModel.client.interruptTurn`.
    var onCancelThread: (@MainActor (ThreadKey) async -> Void)? = nil
    /// Long-press → "Fork" on a home session card. Caller forks the
    /// thread server-side (head fork, no rollback) and navigates to the
    /// new thread.
    var onForkThread: (@MainActor (HomeDashboardRecentSession) async -> Void)? = nil
    var onInputModeChange: ((HomeInputMode) -> Void)? = nil

    @State var deleteTargetThread: HomeDashboardRecentSession?
    @State var replyTargetThread: HomeDashboardRecentSession?
    /// Tracks threads the user just cancelled so their status dot can show
    /// red until the snapshot confirms the turn is no longer active.
    @State var cancellingKeys: Set<String> = []
    @AppStorage("homeZoomLevel") var zoomLevel = 2

    /// Bounded ease for zoom level transitions. `.easeInOut` completes
    /// deterministically in `duration` (unlike `.smooth` which is a
    /// spring that can overshoot its nominal time). Short enough to
    /// feel responsive, long enough to see the height change.
    static let zoomAnimation: Animation = .easeInOut(duration: 0.22)

    /// Direction of the toolbar zoom toggle: +1 walks up, -1 walks down.
    /// Flips at the 1/4 boundaries so the button bounces 1→2→3→4→3→2→1.
    @State var zoomDirection: Int = 1
    @State var renameServerTarget: HomeDashboardServer?
    @State var renameServerText = ""
    @State var isShowingMountedFolders = false
    @State var inputMode: HomeInputMode = .collapsed
    @State var searchQuery = ""
    @State var selectedSearchRuntimeKind: AgentRuntimeKind?
    @State var hydratingKeys: Set<String> = []
    @State var isLoadingThreadListing = false
    @State var suppressComposerCollapse = false

    var launchableServers: [HomeDashboardServer] {
        connectedServers.filter(\.canLaunchSessions)
    }

    var selectedMachineServerId: String? {
        let trimmed = selectedServerId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }

    var composerServerId: String? {
        selectedProject?.serverId ?? selectedMachineServerId
    }

    var selectedLaunchableServer: HomeDashboardServer? {
        let serverId = composerServerId
        guard let serverId else { return nil }
        return launchableServers.first { $0.id == serverId }
    }

    var onSearchThreads: (@Sendable (_ query: String, _ runtimeKind: AgentRuntimeKind?, _ serverId: String?, _ forceRepair: Bool) async -> Void)? = nil

    var isSearchExpanded: Bool { inputMode == .search }

    var searchSessions: [HomeDashboardRecentSession] {
        let serverId = selectedMachineServerId
        guard let serverId, !serverId.isEmpty else { return allSessions }
        return allSessions.filter { $0.serverId == serverId }
    }

    private var searchServers: [HomeDashboardServer] {
        let serverId = selectedMachineServerId
        guard let serverId, !serverId.isEmpty else { return connectedServers }
        return connectedServers.filter { $0.id == serverId }
    }

    var availableSearchRuntimeKinds: [AgentRuntimeKind] {
        let kinds = Set(
            searchServers.flatMap { server in
                server.agentRuntimes
                    .filter(\.available)
                    .map(\.kind)
            }
        )
        return AgentRuntimeKind.presentationOrder.filter { kinds.contains($0) }
    }

    private var searchLoadID: String {
        [
            isSearchExpanded ? "open" : "closed",
            selectedMachineServerId ?? "all",
            searchQuery,
            selectedSearchRuntimeKind?.displayLabel ?? "all"
        ].joined(separator: "|")
    }

    func hydrationId(_ key: ThreadKey) -> String {
        "\(key.serverId)/\(key.threadId)"
    }

    private func autoHydrateIfNeeded() {
        guard let onHydrateThread else { return }
        // Gate on the explicit resumed bit rather than hydrated stats. With
        // paginated threads, loaded items and attached live listeners are now
        // separate states.
        let visible = visibleSessions
        let byPinnedKey = Dictionary(uniqueKeysWithValues: visible.map {
            (SavedThreadsStore.PinnedKey(threadKey: $0.key), $0)
        })
        let pinnedFirst = pinnedThreadKeys.compactMap { byPinnedKey[$0] }
        for session in pinnedFirst where !session.isResumed {
            let id = hydrationId(session.key)
            guard !hydratingKeys.contains(id) else { continue }
            hydratingKeys.insert(id)
            Task {
                await onHydrateThread(session.key, true)
                await MainActor.run {
                    _ = hydratingKeys.remove(id)
                }
            }
        }
    }

    var visibleSessions: [HomeDashboardRecentSession] {
        let serverId = selectedMachineServerId
        guard let serverId, !serverId.isEmpty else { return recentSessions }
        return recentSessions.filter { $0.serverId == serverId }
    }

    var zoomIcon: String {
        switch zoomLevel {
        case 1: return "list.bullet"
        case 2: return "list.dash"
        case 3: return "list.bullet.rectangle"
        default: return "list.bullet.rectangle.fill"
        }
    }

    var body: some View {
        canvas
            .onAppear { onInputModeChange?(inputMode) }
            .onChange(of: inputMode) { _, nextMode in
                onInputModeChange?(nextMode)
                if nextMode != .search {
                    selectedSearchRuntimeKind = nil
                }
            }
            .onAppear { autoHydrateIfNeeded() }
            .onChange(of: visibleSessions.map { hydrationId($0.key) }) { _, _ in
                autoHydrateIfNeeded()
            }
            .onChange(of: pinnedThreadKeys) { _, _ in
                autoHydrateIfNeeded()
            }
            // Clear a cancelled key once the snapshot says the turn is
            // actually gone. Gives the dot a brief red period while the
            // cancel is in flight, then reverts to normal indicator logic.
            .onChange(of: visibleSessions.map { "\(hydrationId($0.key)):\($0.hasTurnActive)" }) { _, _ in
                let stillActive = Set(
                    visibleSessions
                        .filter { $0.hasTurnActive }
                        .map { hydrationId($0.key) }
                )
                cancellingKeys.formIntersection(stillActive)
            }
            .task(id: searchLoadID) {
                guard isSearchExpanded, let onSearchThreads else { return }
                let query = searchQuery
                let runtimeKind = selectedSearchRuntimeKind
                let serverId = selectedMachineServerId
                if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    try? await Task.sleep(nanoseconds: 250_000_000)
                    guard !Task.isCancelled else { return }
                }
                await MainActor.run { isLoadingThreadListing = true }
                await onSearchThreads(query, runtimeKind, serverId, false)
                guard !Task.isCancelled else { return }
                await MainActor.run { isLoadingThreadListing = false }
            }
            .onChange(of: availableSearchRuntimeKinds) { _, kinds in
                if let selectedSearchRuntimeKind, !kinds.contains(selectedSearchRuntimeKind) {
                    self.selectedSearchRuntimeKind = nil
                }
            }
            .background(dashboardBackground)
            .alert("Delete Session?", isPresented: Binding(
                get: { deleteTargetThread != nil },
                set: { if !$0 { deleteTargetThread = nil } }
            )) {
                Button("Cancel", role: .cancel) { deleteTargetThread = nil }
                Button("Delete", role: .destructive) {
                    if let thread = deleteTargetThread {
                        Task { await onDeleteThread?(thread.key) }
                    }
                    deleteTargetThread = nil
                }
            } message: {
                Text("This will permanently delete \"\(deleteTargetThread?.sessionTitle ?? "this session")\".")
            }
            .alert("Rename server", isPresented: Binding(
                get: { renameServerTarget != nil },
                set: { if !$0 { renameServerTarget = nil } }
            )) {
                TextField("Server name", text: $renameServerText)
                Button("Cancel", role: .cancel) { renameServerTarget = nil }
                Button("Save") {
                    if let server = renameServerTarget {
                        let trimmed = renameServerText.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            onRenameServer?(server.id, trimmed)
                        }
                    }
                    renameServerTarget = nil
                }
            }
            .sheet(item: $replyTargetThread) { thread in
                QuickReplySheet(
                    thread: thread,
                    onSend: { key, text in
                        await onSendReply?(key, text)
                    }
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $isShowingMountedFolders) {
                MountedFoldersView()
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(sidebarNavBarVisibility, for: .navigationBar)
            .toolbar { toolbarContent }
    }
}
