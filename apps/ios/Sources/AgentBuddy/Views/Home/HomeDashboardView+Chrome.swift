import SwiftUI
import UIKit

extension HomeDashboardView {
    var sidebarNavBarVisibility: Visibility { .visible }

    @ToolbarContentBuilder
    var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            HStack(spacing: 12) {
                Button(action: onShowSettings) {
                    Image(systemName: "gearshape")
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                }
                if let onShowApps {
                    Button(action: onShowApps) {
                        Image(systemName: "square.grid.2x2")
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                    .accessibilityLabel("Apps")
                }
                if let onShowTerminal {
                    Button(action: onShowTerminal) {
                        Image(systemName: "terminal")
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                    .accessibilityLabel("Terminal")
                }
            }
        }
        ToolbarItem(placement: .principal) {
            if chrome == .sidebar {
                AnimatedLogo(size: 44)
            } else {
                AnimatedLogo(size: 64)
            }
        }
        if chrome == .full {
            ToolbarItem(placement: .topBarTrailing) {
                zoomButton
            }
        } else {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    onNewThread?()
                } label: {
                    Image(systemName: "square.and.pencil")
                        .foregroundColor(AgentBuddyTheme.accent)
                }
                .accessibilityLabel("New thread")
            }
        }
    }

    private var zoomButton: some View {
        Button {
            // Four levels: 1 SCAN → 2 GLANCE → 3 READ → 4 DEEP.
            // Bounce through them: 1→2→3→4→3→2→1.
            let ladder = [1, 2, 3, 4]
            let currentIdx = ladder.firstIndex(of: zoomLevel) ?? 0
            var nextIdx = currentIdx + zoomDirection
            if nextIdx >= ladder.count {
                zoomDirection = -1
                nextIdx = currentIdx + zoomDirection
            } else if nextIdx < 0 {
                zoomDirection = 1
                nextIdx = currentIdx + zoomDirection
            }
            withAnimation(Self.zoomAnimation) {
                zoomLevel = ladder[max(0, min(ladder.count - 1, nextIdx))]
            }
        } label: {
            Image(systemName: zoomIcon)
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
        .accessibilityLabel("Zoom")
    }

    /// The sidebar chrome on a Mac (Catalyst or iOS-on-Mac) sits inside
    /// SwiftUI's `NavigationSplitView` sidebar column, which renders
    /// Liquid Glass automatically. Painting the gradient on top would
    /// clobber that material, so we punch to `.clear` for that case
    /// only. Everywhere else the dashboard owns its own gradient backdrop.
    @ViewBuilder
    var dashboardBackground: some View {
        if AgentBuddyPlatform.rendersAsMacApp && chrome == .sidebar {
            Color.clear
        } else {
            AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
        }
    }

    var topChrome: some View {
        ServerPillRow(
            servers: connectedServers,
            selectedServerId: selectedMachineServerId,
            onTap: onSelectServer,
            onReconnect: { server in onReconnectServer?(server) },
            onRestartAppServer: { server in onRestartAppServer?(server) },
            onRename: { server in
                renameServerText = server.displayName
                renameServerTarget = server
            },
            onRemove: { server in onDisconnectServer?(server.id) },
            onShowMountedFolders: { _ in isShowingMountedFolders = true },
            onAdd: onAddServer
        )
        .frame(maxWidth: .infinity)
    }

    /// Sidebar chrome gets a compact search-only bar at the bottom —
    /// tapping the magnifying glass morphs it into a search field, which
    /// swaps the sessions list for `ThreadSearchResultsView` (the canvas
    /// already keys on `isSearchExpanded` regardless of chrome). The
    /// close button on the search field restores the sessions list.
    var sidebarBottomChrome: some View {
        HomeBottomBar(
            mode: $inputMode,
            searchQuery: $searchQuery,
            project: nil,
            transcriptionServerId: nil,
            onThreadCreated: { _ in },
            compact: true
        )
        .padding(.bottom, 4)
        .background(
            LinearGradient(
                colors: Array(AgentBuddyTheme.headerScrim.reversed()),
                startPoint: .top,
                endPoint: .bottom
            )
            .padding(.top, -30)
            .ignoresSafeArea(.container, edges: .bottom)
            .allowsHitTesting(false)
        )
    }

    var bottomChrome: some View {
        VStack(alignment: .trailing, spacing: 6) {
            DebugBuildLabel()
                .padding(.trailing, 14)
            if inputMode == .composer {
                HStack(spacing: 8) {
                    Spacer()
                    HomeModelChip(
                        serverId: composerServerId,
                        disabled: selectedLaunchableServer == nil,
                        onSheetStateChange: { isPresented in
                            suppressComposerCollapse = isPresented
                        }
                    )
                    ProjectChip(
                        project: selectedProject,
                        disabled: launchableServers.isEmpty,
                        onTap: onOpenProjectPicker
                    )
                }
                .padding(.horizontal, 14)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }

            HomeBottomBar(
                mode: $inputMode,
                searchQuery: $searchQuery,
                collapseSuppressed: suppressComposerCollapse,
                project: selectedProject,
                transcriptionServerId: composerServerId,
                onThreadCreated: onThreadCreated
            )
        }
        .padding(.bottom, 4)
        .background(
            LinearGradient(
                colors: Array(AgentBuddyTheme.headerScrim.reversed()),
                startPoint: .top,
                endPoint: .bottom
            )
            .padding(.top, -30)
            .ignoresSafeArea(.container, edges: .bottom)
            .allowsHitTesting(false)
        )
    }
}
