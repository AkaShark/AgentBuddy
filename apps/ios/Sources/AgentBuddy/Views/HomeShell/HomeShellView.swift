import SwiftUI

/// Phone home: 任务 / 项目 / 主机 with a persistent composer pill and tab bar.
/// It is the root of the phone `NavigationStack`, so conversations push over
/// it (tab bar hidden, native back gesture intact) and popping restores the
/// tab and scroll position.
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
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom, spacing: 0) { bottomChrome }
            .background {
                if layout == .sidebar && AgentBuddyPlatform.rendersAsMacApp {
                    Color.clear
                } else {
                    AgentBuddyTheme.background.ignoresSafeArea()
                }
            }
            .toolbar(.hidden, for: .navigationBar)
    }

    @ViewBuilder
    private var content: some View {
        switch selectedTab.wrappedValue {
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

    private var bottomChrome: some View {
        VStack(spacing: BuddySpacing.xs) {
            if layout == .phone && selectedTab.wrappedValue.showsComposerPill {
                HomeComposerPill(
                    onCompose: { actions.newTask(nil) },
                    onVoice: actions.startVoice,
                    isStartingVoice: isStartingVoice
                )
                .padding(.horizontal, BuddySpacing.md)
            }
            HomeShellTabBar(selection: selectedTab)
                .padding(.horizontal, BuddySpacing.md)
        }
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
