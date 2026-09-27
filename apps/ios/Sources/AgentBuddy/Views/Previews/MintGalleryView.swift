import SwiftUI

#if DEBUG
/// DEBUG-only state gallery for verifying the Mint UI on a physical device
/// without a connected host. Launch with `--mint-gallery=<page>` where page is
/// `home`, `projects`, `hosts`, `newtask`, `conversation`, `approvals` or
/// `composer`, `accessories`, `prompts`, `addhost`, `pair`, `tasks`, `info`, `models`, `settings` or `expanded`; add `--mint-dark` for
/// the dark palette. Nothing here writes user preferences.
struct MintGalleryView: View {
    enum Page: String {
        case home, conversation, approvals, composer, accessories, prompts, addhost, pair, tasks, info, models, settings, expanded, newtask, projects, hosts
    }

    static var requestedPage: Page? {
        for argument in ProcessInfo.processInfo.arguments where argument.hasPrefix("--mint-gallery=") {
            return Page(rawValue: String(argument.dropFirst("--mint-gallery=".count)))
        }
        return nil
    }

    static var isEnabled: Bool { requestedPage != nil }

    /// `--mint-tab=tasks|projects|hosts` picks the home shell's starting tab.
    static var requestedTab: HomeShellTab? {
        for argument in ProcessInfo.processInfo.arguments where argument.hasPrefix("--mint-tab=") {
            return HomeShellTab(rawValue: String(argument.dropFirst("--mint-tab=".count)))
        }
        return nil
    }

    static var prefersDark: Bool {
        ProcessInfo.processInfo.arguments.contains("--mint-dark")
    }

    @State private var appModel = MintGalleryFixtures.makeAppModel()
    @State private var homeModel = HomeDashboardModel()

    init() {
        // Session-only palette switch: the stored appearance mode is untouched.
        ThemeStore.shared.colorScheme = Self.prefersDark ? .dark : .light
    }

    var body: some View {
        AgentBuddyPreviewScene(appModel: appModel, includeBackground: false) {
            page
                .buddyPageBackground()
        }
        .preferredColorScheme(Self.prefersDark ? .dark : .light)
        .onAppear {
            (UIApplication.shared.delegate as? AppDelegate)?.signalContentReady()
        }
    }

    @ViewBuilder
    private var page: some View {
        switch Self.requestedPage ?? .home {
        case .home:
            NavigationStack {
                HomeShellView(
                    model: homeModel,
                    actions: MintGalleryFixtures.noopActions,
                    openingKey: nil,
                    onStartTaskOnServer: { _ in }
                )
            }
            .task {
                homeModel.bind(appModel: appModel)
                homeModel.activate()
            }
        case .conversation:
            NavigationStack {
                ConversationDestinationScreen(
                    threadKey: MintGalleryFixtures.mainThreadKey,
                    bottomInset: 0,
                    onResumeSessions: { _ in },
                    onOpenConversation: { _ in },
                    onInfo: {}
                )
            }
        case .newtask:
            NavigationStack {
                NewThreadHeroView(
                    project: homeModel.projects.first,
                    connectedServers: homeModel.connectedServers,
                    selectedServerId: homeModel.connectedServers.first?.id,
                    onSelectServer: { _ in },
                    onOpenProjectPicker: {},
                    onThreadCreated: { _ in },
                    onCancel: {},
                    autoFocus: false
                )
            }
            .task {
                homeModel.bind(appModel: appModel)
                homeModel.activate()
            }
        case .projects, .hosts:
            NavigationStack {
                if Self.requestedPage == .projects {
                    ProjectsHomeView(model: homeModel, actions: MintGalleryFixtures.noopActions, onManageHosts: {})
                } else {
                    HostsHomeView(model: homeModel, actions: MintGalleryFixtures.noopActions, onStartTask: { _ in })
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .task {
                homeModel.bind(appModel: appModel)
                homeModel.activate()
            }
        case .approvals:
            MintGalleryApprovalsPage()
        case .composer:
            MintGalleryComposerPage()
        case .accessories:
            MintGalleryAccessoriesPage()
        case .prompts:
            MintGalleryAccessoriesPage(showsPrompts: true)
        case .addhost:
            NavigationStack {
                DiscoveryView(entryPoint: .chooser, autoStartDiscovery: false)
            }
        case .pair:
            AlleycatAddServerSheet(appModel: appModel, startScanningOnAppear: false, onConnected: { _ in })
        case .tasks:
            NavigationStack {
                SessionsScreen(autoLoadSessions: false, onOpenConversation: { _ in })
                    .navigationTitle(Text("All tasks"))
                    .navigationBarTitleDisplayMode(.inline)
            }
        case .info:
            NavigationStack {
                ConversationInfoView(
                    threadKey: MintGalleryFixtures.mainThreadKey,
                    serverId: nil,
                    onOpenWallpaper: {},
                    onOpenConversation: { _ in }
                )
            }
        case .settings:
            SettingsView()
        case .expanded:
            ConversationComposerExpandedView(
                inputText: .constant("Also check the logout path on iPad before shipping."),
                isPresented: .constant(true),
                onPasteImage: { _ in },
                onSend: {},
                hasAttachment: false,
                isSendEnabled: false,
                isConnected: false
            )
        case .models:
            if let thread = appModel.snapshot?.threads.first(where: { $0.key == MintGalleryFixtures.mainThreadKey }) {
                ScrollView {
                    ConversationModelPickerPanel(thread: thread)
                        .padding(.top, 56)
                }
            }
        }
    }
}

// MARK: - Fixtures

@MainActor
enum MintGalleryFixtures {
    static let mainThreadKey = ThreadKey(serverId: AgentBuddyPreviewData.sampleServer.id, threadId: "thread-preview-main")

    static func makeAppModel() -> AppModel {
        let appModel = AgentBuddyPreviewData.makeSidebarAppModel()
        guard var snapshot = appModel.snapshot else { return appModel }
        // One running task, one waiting on an approval, one finished.
        if let index = snapshot.sessionSummaries.firstIndex(where: { $0.key.threadId == "thread-preview-fork" }) {
            snapshot.sessionSummaries[index].hasActiveTurn = true
            snapshot.sessionSummaries[index].lastToolLabel = "Checking the login state and page transitions"
        }
        if let index = snapshot.sessionSummaries.firstIndex(where: { $0.key.threadId == "thread-preview-older" }) {
            snapshot.sessionSummaries[index].lastTurnEndMs = Int64(Date().addingTimeInterval(-1080).timeIntervalSince1970 * 1000)
        }
        snapshot.pendingApprovals = [approval(id: "gallery-approval-1", kind: .command, thread: mainThreadKey.threadId)]
        appModel.applySnapshot(snapshot)
        return appModel
    }

    static func approval(id: String, kind: ApprovalKind, thread: String) -> PendingApproval {
        PendingApproval(
            id: id,
            serverId: AgentBuddyPreviewData.sampleServer.id,
            kind: kind,
            threadId: thread,
            turnId: nil,
            itemId: nil,
            command: kind == .command ? "make test && ./tools/scripts/check-login-flow.sh --device \"iPhone 16\" --verbose" : nil,
            path: kind == .fileChange ? "apps/ios/Sources/AgentBuddy/Views/Login/LoginFlowView.swift" : nil,
            grantRoot: kind == .permissions ? "~/Projects/AgentBuddy" : nil,
            cwd: "/Users/sharker/Projects/AgentBuddy",
            reason: kind == .permissions ? "Needs write access outside the workspace to update the shared cache." : nil
        )
    }

    static var noopActions: HomeShellActions {
        HomeShellActions(
            openSession: { _ in },
            showAllTasks: {},
            pinThread: { _ in },
            unpinThread: { _ in },
            hideThread: { _ in },
            deleteThread: { _ in },
            sendQuickReply: { _, _ in },
            cancelThread: { _ in },
            forkThread: { _ in },
            hydrateThread: { _, _ in },
            searchThreads: { _, _, _, _ in },
            newTask: { _ in },
            startVoice: {},
            selectProject: { _ in },
            createProject: {},
            selectServer: { _ in },
            clearServerScope: {},
            addServer: {},
            pairWithQRCode: {},
            reconnectServer: { _ in },
            restartAppServer: { _ in },
            renameServer: { _, _ in },
            removeServer: { _ in },
            showSettings: {},
            showApps: nil,
            showTerminal: nil
        )
    }
}
#endif
