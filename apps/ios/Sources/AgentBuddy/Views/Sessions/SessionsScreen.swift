import SwiftUI

struct SessionsScreen: View {
    static let sessionListPageLimit: UInt32 = 80

    @Environment(AppModel.self) var appModel
    @Environment(AppState.self) var appState
    @Environment(ConversationWarmupCoordinator.self) var conversationWarmup
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State var sessionsModel: SessionsModel
    @State var isLoading: Bool
    @State var resumingKey: ThreadKey?
    @State var isStartingNewSession = false
    @State var directoryPickerSheet: SessionLaunchSupport.DirectoryPickerSheetModel?
    @State var sessionSearchQuery = ""
    @State private var debouncedSessionSearchQuery = ""
    @State var selectedRuntimeKindFilter: AgentRuntimeKind?
    @State var isForkingActiveThread = false
    @State var sessionActionErrorMessage: String?
    @State var renamingThreadKey: ThreadKey?
    @State var renameCurrentTitle = ""
    @State var renameDraft = ""
    @State var archiveTargetKey: ThreadKey?
    @State var collapsedWorkspaceGroupIDs: Set<String> = []
    @State var collapsedSessionNodeKeys: Set<ThreadKey> = []
    @State var pendingActiveSessionScroll = false
    @State private var sessionSearchDebounceTask: Task<Void, Never>?
    @State var hasLoadedInitialSessions = false
    @State var isSessionLoadInFlight = false
    let autoLoadSessions: Bool
    /// Set when this screen lists one project's tasks (Projects tab).
    let projectScope: SessionsProjectScope?
    let onOpenConversation: (ThreadKey) -> Void
    private let onInfo: (() -> Void)?
    static let relativeFormatter: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter
    }()

    init(
        autoLoadSessions: Bool = true,
        projectScope: SessionsProjectScope? = nil,
        onOpenConversation: @escaping (ThreadKey) -> Void,
        onInfo: (() -> Void)? = nil
    ) {
        self.autoLoadSessions = autoLoadSessions
        self.projectScope = projectScope
        self.onOpenConversation = onOpenConversation
        self.onInfo = onInfo
        _isLoading = State(initialValue: autoLoadSessions)
        _sessionsModel = State(initialValue: SessionsModel(projectScope: projectScope))
    }

    var body: some View {
        screenContent(derived: sessionsModel.derivedData)
    }


    private func screenContent(derived: SessionsDerivedData) -> some View {
        let base = screenLayout(derived: derived)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if let onInfo {
                        Button(action: onInfo) {
                            Image(systemName: "info.circle")
                                .font(.system(size: 17, weight: .medium))
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                        }
                        .accessibilityLabel(Text("Host details"))
                    }
                    refreshToolbarButton
                }
            }

        let lifecycle = attachLifecycleHandlers(to: base, derived: derived)
        let alerts = attachSheetAndAlerts(to: lifecycle)

        return alerts.sheet(item: $directoryPickerSheet) { _ in
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
                        Task { await startNewSession(serverId: serverId, cwd: cwd) }
                    },
                    onDismissRequested: {
                        directoryPickerSheet = nil
                    }
                )
            }
            .environment(appModel)
        }
    }

    private func attachLifecycleHandlers<Content: View>(
        to content: Content,
        derived: SessionsDerivedData
    ) -> some View {
        content
            .task {
                sessionsModel.bind(appModel: appModel, appState: appState)
                sessionsModel.updateSearchQuery(debouncedSessionSearchQuery)
                await loadSessionsIfNeeded()
            }
            .onAppear {
                scheduleActiveSessionScrollIfNeeded()
            }
            .onChange(of: connectedServerIds) { _, ids in
                guard autoLoadSessions, !ids.isEmpty else { return }
                Task { await loadSessionsIfNeeded(force: true) }
                scheduleActiveSessionScrollIfNeeded()
                guard let pickerSheet = directoryPickerSheet else {
                    if let filterId = selectedServerFilterId, !ids.contains(filterId) {
                        selectedServerFilterId = nil
                    }
                    return
                }
                guard let fallbackServerId = defaultNewSessionServerId(preferredServerId: pickerSheet.selectedServerId) else {
                    directoryPickerSheet = nil
                    appState.showServerPicker = true
                    return
                }
                if pickerSheet.selectedServerId != fallbackServerId {
                    var nextSheet = pickerSheet
                    nextSheet.selectedServerId = fallbackServerId
                    directoryPickerSheet = nextSheet
                }
                if let filterId = selectedServerFilterId, !ids.contains(filterId) {
                    selectedServerFilterId = nil
                }
            }
            .onChange(of: activeThreadKey) { _, _ in
                scheduleActiveSessionScrollIfNeeded()
            }
            .onChange(of: sessionSearchQuery) { _, next in
                scheduleSessionSearchDebounce(for: next)
            }
            .onChange(of: debouncedSessionSearchQuery) { _, next in
                sessionsModel.updateSearchQuery(next)
            }
            .onChange(of: selectedRuntimeKindFilter) { _, next in
                sessionsModel.updateRuntimeKindFilter(next)
            }
            .onChange(of: derived.workspaceGroupIDs) { _, ids in
                let idSet: Set<String> = Set(ids)
                collapsedWorkspaceGroupIDs = collapsedWorkspaceGroupIDs.intersection(idSet)
            }
            .onChange(of: derived.allThreadKeys) { _, keys in
                let keySet: Set<ThreadKey> = Set(keys)
                collapsedSessionNodeKeys = collapsedSessionNodeKeys.intersection(keySet)
            }
            .onDisappear {
                sessionSearchDebounceTask?.cancel()
                sessionSearchDebounceTask = nil
            }
    }

    private func attachSheetAndAlerts<Content: View>(to content: Content) -> some View {
        content
            .alert("Session Action Failed", isPresented: Binding(
                get: { sessionActionErrorMessage != nil },
                set: { if !$0 { sessionActionErrorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { sessionActionErrorMessage = nil }
            } message: {
                Text(sessionActionErrorMessage ?? "Unknown error")
            }
            .alert("Rename Session", isPresented: Binding(
                get: { renamingThreadKey != nil },
                set: {
                    if !$0 {
                        renamingThreadKey = nil
                        renameCurrentTitle = ""
                        renameDraft = ""
                    }
                }
            )) {
                TextField("New session title", text: $renameDraft)
                Button("Save") { Task { await submitRename() } }
                Button("Cancel", role: .cancel) {
                    renamingThreadKey = nil
                    renameCurrentTitle = ""
                    renameDraft = ""
                }
            } message: {
                Text("Current session title:\n\(renameCurrentTitle)")
            }
            .confirmationDialog(
                "Delete session?",
                isPresented: Binding(
                    get: { archiveTargetKey != nil },
                    set: { if !$0 { archiveTargetKey = nil } }
                ),
                titleVisibility: Visibility.visible,
                presenting: archiveTargetThread
            ) { thread in
                Button("Delete \"\(thread.sessionTitle)\"", role: .destructive) {
                    Task { await confirmArchiveSession() }
                }
                Button("Cancel", role: .cancel) { archiveTargetKey = nil }
            } message: { _ in
                Text("This removes the session from the list.")
            }
    }

    /// One Mint list: host summary and "New task" first, then search and
    /// filter chips, then tasks grouped by workspace.
    private func screenLayout(derived: SessionsDerivedData) -> some View {
        sessionList(derived: derived) {
            Group {
                serversRow
                newSessionButton
                    .padding(.bottom, BuddySpacing.xs)
            }
            .sessionsListRow(vertical: BuddySpacing.xxs)

            if derived.allThreads.isEmpty {
                emptySessionsState
                    .sessionsListRow(vertical: BuddySpacing.xs)
            } else {
                if isLoading {
                    loadingMoreSessionsRow
                        .sessionsListRow()
                }
                sessionSearchBar
                    .sessionsListRow(vertical: BuddySpacing.xs)
                runtimeKindPillRow
                sessionFilterRow
                if derived.filteredThreads.isEmpty {
                    noMatchingSessionsState(query: trimmedSessionSearchQuery)
                        .sessionsListRow(vertical: BuddySpacing.xs)
                }
            }
        }
        .buddyPageBackground()
        .accessibilityIdentifier("sessions.container")
    }

    var selectedServerFilterId: String? {
        get { appState.sessionsSelectedServerFilterId }
        nonmutating set { appState.sessionsSelectedServerFilterId = newValue }
    }

    var showOnlyForks: Bool {
        get { appState.sessionsShowOnlyForks }
        nonmutating set { appState.sessionsShowOnlyForks = newValue }
    }

    var workspaceSortMode: WorkspaceSortMode {
        get { WorkspaceSortMode(rawValue: appState.sessionsWorkspaceSortModeRaw) ?? .mostRecent }
        nonmutating set { appState.sessionsWorkspaceSortModeRaw = newValue.rawValue }
    }

    var connectedServerIds: [String] {
        connectedServerOptions.map(\.id).sorted()
    }

    private var trimmedSessionSearchQuery: String {
        sessionSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var archiveTargetThread: AppSessionSummary? {
        guard let archiveTargetKey else { return nil }
        return sessionsModel.derivedData.allThreads.first(where: { $0.key == archiveTargetKey })
    }

    var connectedServerOptions: [DirectoryPickerServerOption] {
        sessionsModel.connectedServerOptions
    }

    var connectedServers: [HomeDashboardServer] {
        sessionsModel.connectedServers
    }

    var ephemeralStateByThreadKey: [ThreadKey: SessionsModel.ThreadEphemeralState] {
        sessionsModel.ephemeralStateByThreadKey
    }

    var activeThreadKey: ThreadKey? {
        sessionsModel.activeThreadKey
    }

    private func scheduleSessionSearchDebounce(for nextQuery: String) {
        sessionSearchDebounceTask?.cancel()
        sessionSearchDebounceTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 120_000_000)
            guard !Task.isCancelled else { return }
            let normalized = nextQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            if debouncedSessionSearchQuery != normalized {
                debouncedSessionSearchQuery = normalized
            }
        }
    }

    func defaultNewSessionServerId(preferredServerId: String? = nil) -> String? {
        SessionLaunchSupport.defaultConnectedServerId(
            connectedServerIds: connectedServerIds,
            activeThreadKey: activeThreadKey,
            preferredServerId: preferredServerId
        )
    }

    @AppStorage("workDir") var workDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? "/"
}

#if DEBUG
#Preview("Sessions Screen") {
    AgentBuddyPreviewScene(
        appModel: AgentBuddyPreviewData.makeSidebarAppModel(),
        appState: AgentBuddyPreviewData.makeAppState()
    ) {
        NavigationStack {
            SessionsScreen(autoLoadSessions: false, onOpenConversation: { _ in })
        }
    }
}
#endif
