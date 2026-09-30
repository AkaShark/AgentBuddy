import Combine
import SwiftUI
import UIKit

struct HomeNavigationView: View {
    @Environment(AppModel.self) var appModel
    @Environment(VoiceRuntimeController.self) var voiceRuntime
    @Environment(AppState.self) var appState
    @Environment(ConversationWarmupCoordinator.self) var conversationWarmup
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @AppStorage("workDir") var workDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? "/"
    @State var experimentalFeatures = ExperimentalFeatures.shared
    @State var homeDashboardModel = HomeDashboardModel()
    @State var savedAppsStore = SavedAppsStore.shared
    @State var navigationPath: [HomeNavigationRoute] = []
    @State var directoryPickerSheet: SessionLaunchSupport.DirectoryPickerSheetModel?
    @State var showProjectPicker = false
    @State var openingRecentSessionKey: ThreadKey?
    @State var isStartingNewSession = false
    @State var isStartingVoice = false
    @State var actionErrorMessage: String?
    @State var hydratingPinnedHomeThreadIds: Set<String> = []
    @State var pinnedThreadListingRepairTasks: [String: Task<Bool, Never>] = [:]
    @State var hasSeededInitialConversationRoute = false
    @State var pendingWallpaperConfig: WallpaperConfig?
    @State var pendingWallpaperImage: UIImage?
    @State var isNewTaskSheetPresented = false
    let topInset: CGFloat
    let bottomInset: CGFloat

    var connectedServerOptions: [DirectoryPickerServerOption] {
        homeDashboardModel.connectedServers.filter(\.canLaunchSessions).map { server in
            DirectoryPickerServerOption(
                id: server.id,
                name: server.displayName,
                sourceLabel: server.sourceLabel
            )
        }
    }

    var isHomeRouteActive: Bool {
        navigationPath.isEmpty
    }

    @ViewBuilder
    private var rootNavigationContent: some View {
        if AgentBuddyPlatform.isRegularSurface(horizontalSizeClass: horizontalSizeClass) {
            splitRoot
        } else {
            primaryNavigationStack
        }
    }

    private var splitRoot: some View {
        NavigationSplitView {
            sidebarDashboard
                // Apply Liquid Glass material explicitly to the sidebar
                // column. Catalyst 26 doesn't automatically paint the
                // sidebar with glass the way iPadOS does, so the column
                // comes through flat unless we install the material
                // ourselves. `.ultraThinMaterial` gives the proper
                // sidebar frosted-glass look with subtle vibrancy.
                .containerBackground(.ultraThinMaterial, for: .navigation)
        } detail: {
            primaryNavigationStack
        }
    }

    /// Whether the primary navigation stack is embedded as the detail pane
    /// of a `NavigationSplitView`. In that case the sidebar already hosts
    /// `HomeDashboardView`, so the detail pane's root should be an empty
    /// welcome surface instead of a second dashboard rendering.
    var isEmbeddedInSplit: Bool {
        AgentBuddyPlatform.isRegularSurface(horizontalSizeClass: horizontalSizeClass)
    }

    var body: some View {
        rootNavigationContent
        .task {
            homeDashboardModel.bind(appModel: appModel)
            updateHomeDashboardActivity()
            hydratePinnedThreadsIfNeeded()
            seedInitialConversationIfNeeded(activeKey: appModel.snapshot?.activeThread)
            // A recreated stack (e.g. theme change) starts at home again.
            AppRuntimeController.shared.visibleConversationKey = Self.visibleConversationKey(in: navigationPath)
            appState.visibleConversationKey = Self.visibleConversationKey(in: navigationPath)
            openNotificationThreadIfRequested()
        }
        .onChange(of: appModel.snapshot?.activeThread) { _, newKey in
            seedInitialConversationIfNeeded(activeKey: newKey)
        }
        .onChange(of: navigationPath) { _, newPath in
            AppRuntimeController.shared.visibleConversationKey = Self.visibleConversationKey(in: newPath)
            appState.visibleConversationKey = Self.visibleConversationKey(in: newPath)
        }
        .onChange(of: AppRuntimeController.shared.notificationNavigationRequest) { _, _ in
            openNotificationThreadIfRequested()
        }
        .onChange(of: navigationPath.count) { _, newCount in
            updateHomeDashboardActivity()
        }
        .onChange(of: pinnedThreadHydrationSignature) { _, _ in
            hydratePinnedThreadsIfNeeded()
        }
        .onChange(of: appState.pendingThreadNavigation) { _, newKey in
            if let newKey {
                appState.pendingThreadNavigation = nil
                replaceTopConversation(with: newKey)
            }
        }
        .onChange(of: SavedAppsNavigation.shared.pendingConversationThreadId) { _, newThreadId in
            guard let newThreadId else { return }
            _ = SavedAppsNavigation.shared.consumeConversationRequest()
            guard let key = appModel.snapshot?.threads.first(where: { $0.key.threadId == newThreadId })?.key else {
                return
            }
            // Pop the saved-app detail off the stack, then push the conversation.
            if case .savedApp = navigationPath.last {
                navigationPath.removeLast()
            }
            openConversation(key)
        }
        #if targetEnvironment(macCatalyst)
        .onReceive(NotificationCenter.default.publisher(for: .agentBuddyCommandNewSession)) { _ in
            handleNewSessionTap()
        }
        .onReceive(NotificationCenter.default.publisher(for: .agentBuddyCommandNavigateBack)) { _ in
            if !navigationPath.isEmpty { navigationPath.removeLast() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .agentBuddyCommandNavigateForward)) { _ in
            if let activeKey = appModel.snapshot?.activeThread,
               navigationPath.last != .conversation(activeKey) {
                navigationPath.append(.conversation(activeKey))
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .agentBuddyCommandSelectSession)) { notification in
            guard let index = notification.userInfo?["index"] as? Int,
                  let summaries = appModel.snapshot?.sessionSummaries,
                  summaries.indices.contains(index) else { return }
            Task { @MainActor in
                await openSessionAtIndex(summaries[index])
            }
        }
        #endif
        .sheet(item: $directoryPickerSheet) { _ in
            NavigationStack {
                DirectoryPickerView(
                    servers: connectedServerOptions,
                    selectedServerId: Binding(
                        get: { directoryPickerSheet?.selectedServerId ?? defaultNewSessionServerId() ?? "" },
                        set: { nextServerId in
                            guard var sheet = directoryPickerSheet else { return }
                            sheet.selectedServerId = nextServerId
                            directoryPickerSheet = sheet
                        }
                    ),
                    onServerChanged: { nextServerId in
                        guard var sheet = directoryPickerSheet else { return }
                        sheet.selectedServerId = nextServerId
                        directoryPickerSheet = sheet
                    },
                    onDirectorySelected: { serverId, cwd in
                        directoryPickerSheet = nil
                        createAndSelectProject(serverId: serverId, cwd: cwd)
                    },
                    onDismissRequested: {
                        directoryPickerSheet = nil
                    }
                )
            }
            .environment(appModel)
        }
        .sheet(isPresented: $isNewTaskSheetPresented) {
            newTaskSheet
        }
        .sheet(isPresented: $showProjectPicker) {
            ProjectPickerSheet(
                projects: homeDashboardModel.projects,
                serverNamesById: Dictionary(uniqueKeysWithValues: homeDashboardModel.connectedServers.map { ($0.id, $0.displayName) }),
                onSelect: { project in
                    homeDashboardModel.selectProject(project)
                },
                onCreateNew: {
                    showProjectPicker = false
                    let defaultServerId = homeDashboardModel.selectedServerId ?? defaultNewSessionServerId()
                    if let defaultServerId {
                        directoryPickerSheet = SessionLaunchSupport.DirectoryPickerSheetModel(selectedServerId: defaultServerId)
                    } else {
                        appState.showServerPicker = true
                    }
                }
            )
            .environment(appModel)
        }
        .alert("Home Action Failed", isPresented: Binding(
            get: { actionErrorMessage != nil },
            set: { if !$0 { actionErrorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { actionErrorMessage = nil }
        } message: {
            Text(actionErrorMessage ?? "Unknown error")
        }
    }
}
