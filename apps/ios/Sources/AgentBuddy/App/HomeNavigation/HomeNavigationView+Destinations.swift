import SwiftUI

extension HomeNavigationView {
    var primaryNavigationStack: some View {
        NavigationStack(path: $navigationPath) {
            Group {
                if isHomeRouteActive {
                    if isEmbeddedInSplit {
                        splitDetailRoot
                    } else {
                        homeShell
                    }
                } else {
                    AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
                }
            }
            .overlay(alignment: .bottomLeading) {
                // Phone home carries voice inside its composer pill; only the
                // split layout still floats the orb over the detail pane.
                if isHomeRouteActive,
                   isEmbeddedInSplit,
                   experimentalFeatures.isEnabled(.realtimeVoice) {
                    homeVoiceLauncher
                }
            }
            .navigationDestination(for: HomeNavigationRoute.self) { route in
                switch route {
                case let .sessions(serverId, title):
                    SessionsScreen(
                        onOpenConversation: { key in
                            openConversation(key)
                        },
                        onInfo: {
                            navigationPath.append(.serverInfo(serverId: serverId))
                        }
                    )
                        .navigationTitle(title)
                        .navigationBarTitleDisplayMode(.inline)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
                        .onAppear {
                            appState.sessionsSelectedServerFilterId = serverId
                            appState.sessionsShowOnlyForks = false
                        }
                case let .conversation(threadKey):
                    ConversationDestinationScreen(
                        threadKey: threadKey,
                        bottomInset: bottomInset,
                        onResumeSessions: { showSessions(for: $0) },
                        onOpenConversation: { replaceTopConversation(with: $0) },
                        onInfo: { navigationPath.append(.conversationInfo(threadKey)) }
                    )
                case .newThread:
                    NewThreadHeroView(
                        project: homeDashboardModel.selectedProject,
                        connectedServers: homeDashboardModel.connectedServers,
                        selectedServerId: homeDashboardModel.selectedServerId,
                        onSelectServer: { serverId in
                            homeDashboardModel.selectedServerId = serverId
                        },
                        onOpenProjectPicker: { showProjectPicker = true },
                        onThreadCreated: { key in
                            homeDashboardModel.pinThread(key)
                            replaceHeroWithConversation(key: key)
                        },
                        onCancel: {
                            if case .newThread = navigationPath.last {
                                navigationPath.removeLast()
                            }
                        }
                    )
                case let .replayRecording(recordingUrl):
                    ReplayDestinationScreen(
                        recordingUrl: recordingUrl,
                        bottomInset: bottomInset
                    )
                case let .realtimeVoice(threadKey):
                    RealtimeVoiceScreen(
                        threadKey: threadKey,
                        onEnd: {
                            popCurrentRoute()
                            Task { await voiceRuntime.stopActiveVoiceSession() }
                        },
                        onToggleSpeaker: {
                            Task { try? await voiceRuntime.toggleActiveVoiceSessionSpeaker() }
                        }
                    )
                    .toolbar(.hidden, for: .navigationBar)
                    .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
                case let .conversationInfo(threadKey):
                    ConversationInfoView(
                        threadKey: threadKey,
                        serverId: nil,
                        onOpenWallpaper: { navigationPath.append(.wallpaperSelection(threadKey)) },
                        onOpenConversation: { replaceTopConversation(with: $0) }
                    )
                case let .wallpaperSelection(threadKey):
                    WallpaperSelectionView(
                        threadKey: threadKey,
                        onSelectWallpaper: { config, image in
                            pendingWallpaperConfig = config
                            pendingWallpaperImage = image
                            navigationPath.append(.wallpaperAdjust(threadKey))
                        },
                        onClose: {
                            // Pop back to conversation info
                            popToConversationInfo()
                        }
                    )
                    .toolbar(.hidden, for: .navigationBar)
                    .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
                case let .wallpaperAdjust(threadKey):
                    WallpaperAdjustView(
                        threadKey: threadKey,
                        initialConfig: pendingWallpaperConfig ?? WallpaperConfig(),
                        customImage: pendingWallpaperImage,
                        onDone: {
                            // Pop back to conversation info
                            popToConversationInfo()
                        }
                    )
                    .toolbar(.hidden, for: .navigationBar)
                    .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
                case let .serverInfo(serverId):
                    ConversationInfoView(
                        threadKey: nil,
                        serverId: serverId,
                        onOpenWallpaper: { navigationPath.append(.serverWallpaperSelection(serverId: serverId)) },
                        onOpenShell: remoteShellLauncher(for: serverId)
                    )
                case let .serverWallpaperSelection(serverId):
                    WallpaperSelectionView(
                        threadKey: nil,
                        serverId: serverId,
                        onSelectWallpaper: { config, image in
                            pendingWallpaperConfig = config
                            pendingWallpaperImage = image
                            navigationPath.append(.serverWallpaperAdjust(serverId: serverId))
                        },
                        onClose: {
                            popToServerInfo()
                        }
                    )
                    .toolbar(.hidden, for: .navigationBar)
                    .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
                case let .serverWallpaperAdjust(serverId):
                    WallpaperAdjustView(
                        threadKey: nil,
                        serverId: serverId,
                        initialConfig: pendingWallpaperConfig ?? WallpaperConfig(),
                        customImage: pendingWallpaperImage,
                        onDone: {
                            popToServerInfo()
                        }
                    )
                    .toolbar(.hidden, for: .navigationBar)
                    .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
                case .appsList:
                    AppsListView()
                case .savedApp(let appId):
                    SavedAppDetailView(appId: appId)
                case let .terminal(preferredAlleycatNodeId):
                    TerminalScreen(
                        cwd: preferredTerminalWorkingDirectory(),
                        preferredAlleycatNodeId: preferredAlleycatNodeId
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
    }
}
