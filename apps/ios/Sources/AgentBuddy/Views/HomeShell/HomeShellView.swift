import SwiftUI

/// Home shell: 任务 / 项目 / 主机. On the phone it uses the system tab bar
/// (`HomeShellSystemTabs`, Liquid Glass on iOS 26) and is the root of the phone
/// `NavigationStack`, so conversations push over it (tab bar hidden, native
/// back gesture intact) and popping restores the tab and scroll position. In
/// the iPad / Mac sidebar it keeps a compact custom tab row.
struct HomeShellView: View {
    var layout: HomeShellLayout = .phone
    let model: HomeDashboardModel
    let actions: HomeShellActions
    let openingKey: ThreadKey?
    var isStartingVoice = false
    let onStartTaskOnServer: (HomeDashboardServer) -> Void

    @SceneStorage("homeShellTab") private var selectedTabRaw = HomeShellTab.tasks.rawValue

    private var selectedTab: Binding<HomeShellTab> {
        Binding(
            get: { HomeShellTab(rawValue: selectedTabRaw) ?? .tasks },
            set: { selectedTabRaw = $0.rawValue }
        )
    }

    var body: some View {
        Group {
            if layout == .phone {
                phoneTabs
            } else {
                sidebarShell
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        #if DEBUG
        .onAppear {
            if MintGalleryView.isEnabled, let tab = MintGalleryView.requestedTab {
                selectedTabRaw = tab.rawValue
            }
        }
        #endif
    }

    /// Phone: the system tab bar (Liquid Glass on iOS 26).
    private var phoneTabs: some View {
        HomeShellSystemTabs(
            selection: selectedTab,
            composer: HomeComposerPillActions(
                onCompose: { actions.newTask(nil) },
                onVoice: actions.startVoice,
                isStartingVoice: isStartingVoice
            )
        ) { tab in
            page(for: tab)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(AgentBuddyTheme.background.ignoresSafeArea())
        }
    }

    /// iPad / Mac sidebar column: keeps the compact custom tab row; the detail
    /// pane owns the composer.
    private var sidebarShell: some View {
        page(for: selectedTab.wrappedValue)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom, spacing: 0) { sidebarTabBar }
            .background {
                if AgentBuddyPlatform.rendersAsMacApp {
                    Color.clear
                } else {
                    AgentBuddyTheme.background.ignoresSafeArea()
                }
            }
    }

    @ViewBuilder
    private func page(for tab: HomeShellTab) -> some View {
        switch tab {
        case .tasks:
            TasksHomeView(
                model: model,
                actions: actions,
                openingKey: openingKey,
                onManageHosts: { selectedTab.wrappedValue = .hosts },
                showsComposeButton: layout == .sidebar
            )
        case .projects:
            ProjectsHomeView(
                model: model,
                actions: actions,
                onManageHosts: { selectedTab.wrappedValue = .hosts }
            )
        case .hosts:
            HostsHomeView(
                model: model,
                actions: actions,
                onStartTask: onStartTaskOnServer
            )
        }
    }

    private var sidebarTabBar: some View {
        HomeShellTabBar(selection: selectedTab)
            .padding(.horizontal, BuddySpacing.md)
            .padding(.top, BuddySpacing.xs)
            .background {
                LinearGradient(
                    stops: [
                        .init(color: AgentBuddyTheme.background.opacity(0), location: 0),
                        .init(color: AgentBuddyTheme.background, location: 0.28),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea(edges: .bottom)
                .allowsHitTesting(false)
            }
    }
}
