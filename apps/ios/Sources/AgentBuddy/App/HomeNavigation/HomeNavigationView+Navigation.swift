import SwiftUI

extension HomeNavigationView {
    private func openServerSessions(_ server: HomeDashboardServer) {
        appState.sessionsSelectedServerFilterId = server.id
        appState.sessionsShowOnlyForks = false
        hasSeededInitialConversationRoute = true
        navigationPath.append(.sessions(serverId: server.id, title: server.displayName))
    }

    func seedInitialConversationIfNeeded(activeKey: ThreadKey?) {
        guard !hasSeededInitialConversationRoute,
              !isStartingVoice,
              navigationPath.isEmpty,
              let activeKey else { return }

        Task { @MainActor in
            await conversationWarmup.prewarmIfNeeded()
            guard !hasSeededInitialConversationRoute,
                  !isStartingVoice,
                  navigationPath.isEmpty,
                  appModel.snapshot?.activeThread == activeKey else {
                return
            }
            hasSeededInitialConversationRoute = true
            navigationPath = [.conversation(activeKey)]
        }
    }

    static func visibleConversationKey(in path: [HomeNavigationRoute]) -> ThreadKey? {
        guard case let .conversation(key) = path.last else { return nil }
        return key
    }

    /// A notification tap resolved its thread; show it on top of the stack.
    func openNotificationThreadIfRequested() {
        guard let key = AppRuntimeController.shared.consumeNotificationNavigationRequest(),
              navigationPath.last != .conversation(key) else { return }
        replaceTopConversation(with: key)
    }

    func openConversation(_ key: ThreadKey) {
        hasSeededInitialConversationRoute = true
        appState.showModelSelector = false
        guard navigationPath.last != .conversation(key) else { return }
        navigationPath.append(.conversation(key))
    }

    func openRealtimeVoice(_ key: ThreadKey) {
        hasSeededInitialConversationRoute = true
        appState.showModelSelector = false
        guard navigationPath.last != .realtimeVoice(key) else { return }
        navigationPath.append(.realtimeVoice(key))
    }

    func popToConversationInfo() {
        // Pop wallpaper selection and/or adjust screens, back to conversation info
        while let last = navigationPath.last {
            if case .conversationInfo = last { break }
            navigationPath.removeLast()
        }
    }

    func popToServerInfo() {
        while let last = navigationPath.last {
            if case .serverInfo = last { break }
            navigationPath.removeLast()
        }
    }

    func replaceTopConversation(with key: ThreadKey) {
        hasSeededInitialConversationRoute = true
        if case .conversation = navigationPath.last {
            navigationPath.removeLast()
        }
        openConversation(key)
    }

    /// Push the hero composer into the detail pane. On compact this pushes
    /// `.newThread` as a destination; on split it's a no-op because the
    /// detail root already *is* the hero view (just pop back to it).
    func openNewThread() {
        if isEmbeddedInSplit {
            if !navigationPath.isEmpty {
                navigationPath.removeAll()
            }
            return
        }
        if case .newThread = navigationPath.last { return }
        if case .conversation = navigationPath.last {
            navigationPath.removeLast()
        }
        navigationPath.append(.newThread)
    }

    /// Swap the hero composer out for the freshly-created conversation in
    /// a single animation frame so the composer's apparent position is
    /// preserved by the glass morph.
    func replaceHeroWithConversation(key: ThreadKey) {
        if case .newThread = navigationPath.last {
            navigationPath.removeLast()
        }
        openConversation(key)
    }

    func popCurrentRoute() {
        guard !navigationPath.isEmpty else { return }
        appState.showModelSelector = false
        navigationPath.removeLast()
    }

    func showSessions(for serverId: String) {
        appState.sessionsSelectedServerFilterId = serverId
        appState.sessionsShowOnlyForks = false
        appState.showModelSelector = false
        hasSeededInitialConversationRoute = true

        if let existingIndex = navigationPath.lastIndex(where: { route in
            guard case let .sessions(id, _) = route else { return false }
            return id == serverId
        }) {
            navigationPath = Array(navigationPath.prefix(through: existingIndex))
            return
        }

        if case .conversation = navigationPath.last {
            navigationPath.removeLast()
        } else if case .realtimeVoice = navigationPath.last {
            navigationPath.removeLast()
        }
        navigationPath.append(.sessions(serverId: serverId, title: serverTitle(for: serverId)))
    }

    private func serverTitle(for serverId: String) -> String {
        if let server = homeDashboardModel.connectedServers.first(where: { $0.id == serverId }) {
            return server.displayName
        }
        if let thread = homeDashboardModel.recentSessions.first(where: { $0.serverId == serverId }) {
            return thread.serverDisplayName
        }
        return "Sessions"
    }
}
