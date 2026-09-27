import SwiftUI

/// 任务 tab. Tasks that need a decision come first, then running tasks as
/// cards, then the rest as rows. All data comes from `HomeDashboardModel`
/// (Rust snapshot projections); actions go through `HomeShellActions`.
struct TasksHomeView: View {
    let model: HomeDashboardModel
    let actions: HomeShellActions
    let openingKey: ThreadKey?
    let onManageHosts: () -> Void
    /// Extra bottom space so the last row clears the floating composer and tab bar.
    var bottomContentInset: CGFloat = 0

    @Environment(AppModel.self) var appModel
    @AppStorage("homeZoomLevel") var zoomLevel = 2

    @State var cancellingKeys: Set<String> = []
    @State var hydratingKeys: Set<String> = []
    @State var replyTarget: HomeDashboardRecentSession?
    @State var deleteTarget: HomeTaskItem?
    @State var isSearching = false
    @State var searchQuery = ""
    @State var searchRuntimeKind: AgentRuntimeKind?
    @State var isLoadingSearch = false

    var showsDetail: Bool { zoomLevel >= 3 }

    var visibleSessions: [HomeDashboardRecentSession] {
        guard let serverId = model.selectedServerId, !serverId.isEmpty else { return model.recentSessions }
        return model.recentSessions.filter { $0.serverId == serverId }
    }

    var items: [HomeTaskItem] {
        HomeTaskPresentation.items(
            sessions: visibleSessions,
            pendingApprovals: appModel.snapshot?.pendingApprovals ?? [],
            pendingInputs: appModel.snapshot?.pendingUserInputs ?? [],
            pinnedKeys: model.pinnedKeys,
            cancellingKeys: cancellingKeys
        )
    }

    var body: some View {
        let items = items
        let attention = items.filter { $0.state.needsAttention }
        let active = items.filter { $0.state == .running || $0.state == .stopping }
        let rest = items.filter { !$0.state.needsAttention && $0.state != .running && $0.state != .stopping }

        List {
            Group {
                TasksHomeHeader(
                    servers: model.connectedServers,
                    selectedServerId: model.selectedServerId,
                    actions: actions,
                    onManageHosts: onManageHosts,
                    zoomLevel: $zoomLevel
                )
                .padding(.top, BuddySpacing.xs)

                if isSearching {
                    searchField
                } else {
                    hero(running: active.count, waiting: attention.count, hasTasks: !items.isEmpty)
                }
            }
            .buddyListRow()

            if isSearching {
                searchResults.buddyListRow()
            } else if items.isEmpty {
                emptyContent.buddyListRow()
            } else {
                attentionSection(attention, showsSearch: true)
                activeSection(active, showsSearch: attention.isEmpty)
                recentSection(rest, showsSearch: attention.isEmpty && active.isEmpty)
            }

            Color.clear
                .frame(height: bottomContentInset)
                .buddyListRow()
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 1)
        .buddyPageBackground()
        .onAppear(perform: hydratePinnedSessions)
        .onChange(of: hydrationSignature) { _, _ in hydratePinnedSessions() }
        .onChange(of: model.pinnedKeys) { _, _ in hydratePinnedSessions() }
        .onChange(of: activitySignature) { _, _ in pruneFinishedStops() }
        .sheet(item: $replyTarget) { session in
            QuickReplySheet(thread: session, onSend: { key, text in
                await actions.sendQuickReply(key, text)
            })
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .buddySheetStyle()
        }
        .alert("Delete Session?", isPresented: Binding(
            get: { deleteTarget != nil },
            set: { if !$0 { deleteTarget = nil } }
        )) {
            Button("Cancel", role: .cancel) { deleteTarget = nil }
            Button("Delete", role: .destructive) {
                if let target = deleteTarget {
                    Task { await actions.deleteThread(target.key) }
                }
                deleteTarget = nil
            }
        } message: {
            Text("This will permanently delete \"\(deleteTarget?.title ?? "this session")\".")
        }
    }

    // MARK: - Actions

    var handlers: TaskActionHandlers {
        TaskActionHandlers(
            reply: { replyTarget = $0.session },
            stop: { item in
                cancellingKeys.insert(HomeTaskPresentation.hydrationId(item.key))
                Task { await actions.cancelThread(item.key) }
            },
            fork: { item in Task { await actions.forkThread(item.session) } },
            togglePin: { item in
                if item.isPinned { actions.unpinThread(item.key) } else { actions.pinThread(item.key) }
            },
            hide: { actions.hideThread($0.key) },
            pictureInPicture: { StreamingPiPController.shared.start(for: $0.key) },
            delete: { deleteTarget = $0 }
        )
    }

    func open(_ item: HomeTaskItem) {
        guard openingKey == nil else { return }
        Task { await actions.openSession(item.session) }
    }
}

extension View {
    /// Plain, full-width list row on the page background with the page gutter.
    func buddyListRow(vertical: CGFloat = BuddySpacing.xs) -> some View {
        listRowInsets(EdgeInsets(top: vertical, leading: BuddySpacing.xl, bottom: vertical, trailing: BuddySpacing.xl))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
    }
}
