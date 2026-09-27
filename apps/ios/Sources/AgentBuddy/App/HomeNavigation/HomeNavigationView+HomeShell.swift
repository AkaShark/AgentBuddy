import SwiftUI

extension HomeNavigationView {
    /// Phone root: 任务 / 项目 / 主机 shell. The iPad / Mac split view keeps its
    /// sidebar + detail layout.
    var homeShell: some View {
        HomeShellView(
            model: homeDashboardModel,
            actions: homeShellActions,
            openingKey: openingRecentSessionKey,
            isStartingVoice: isStartingVoice,
            onStartTaskOnServer: { server in
                homeDashboardModel.selectedServerId = server.id
                presentNewTask(project: nil)
            }
        )
    }

    var homeShellActions: HomeShellActions {
        HomeShellActions(
            openSession: openRecentSession,
            openThread: { key in openConversation(key) },
            showAllTasks: allTasksServerId.map { serverId in { showSessions(for: serverId) } },
            pinThread: pinThread,
            unpinThread: unpinThread,
            hideThread: hideThread,
            deleteThread: deleteThread,
            sendQuickReply: { key, text in await sendQuickReply(key, text: text) },
            cancelThread: cancelThread,
            forkThread: forkSessionFromHome,
            hydrateThread: { key, loadInitialTurns in
                _ = await hydrateThread(key, loadInitialTurns: loadInitialTurns)
            },
            searchThreads: loadSearchThreads,
            newTask: { project in presentNewTask(project: project) },
            startVoice: experimentalFeatures.isEnabled(.realtimeVoice) ? { startHomeVoiceSession() } : nil,
            selectProject: { project in
                homeDashboardModel.selectedServerId = project.serverId
                homeDashboardModel.selectedProject = project
            },
            createProject: presentProjectCreation,
            selectServer: handleSelectServer,
            clearServerScope: { homeDashboardModel.clearScope() },
            addServer: { appState.showServerPicker = true },
            pairWithQRCode: {
                appState.serverPickerEntryPoint = .pairWithQRCode
                appState.showServerPicker = true
            },
            reconnectServer: reconnectServer,
            restartAppServer: restartAppServer,
            renameServer: { serverId, name in renameServer(serverId, newName: name) },
            removeServer: disconnectServer,
            showSettings: { appState.showSettings = true },
            showApps: savedAppsStore.apps.isEmpty ? nil : { navigationPath.append(.appsList) },
            showTerminal: terminalLauncher
        )
    }

    /// "全部任务" opens the full sessions screen scoped to the host in view.
    private var allTasksServerId: String? {
        homeDashboardModel.selectedServerId
            ?? homeDashboardModel.connectedServers.first(where: \.canLaunchSessions)?.id
            ?? homeDashboardModel.connectedServers.first?.id
    }

    func presentNewTask(project: AppProject?) {
        if let project {
            homeDashboardModel.selectedServerId = project.serverId
            homeDashboardModel.selectedProject = project
        }
        guard !homeDashboardModel.connectedServers.isEmpty else {
            appState.showServerPicker = true
            return
        }
        isNewTaskSheetPresented = true
    }

    func presentProjectCreation() {
        let serverId = homeDashboardModel.selectedServerId ?? defaultNewSessionServerId()
        if let serverId {
            directoryPickerSheet = SessionLaunchSupport.DirectoryPickerSheetModel(selectedServerId: serverId)
        } else {
            appState.showServerPicker = true
        }
    }

    /// Phone new-task sheet. Keeps the previous phone behaviour: the new task
    /// is pinned and the user stays on home, where it appears under
    /// "In progress" and streams in place.
    var newTaskSheet: some View {
        NewTaskSheet(
            model: homeDashboardModel,
            onThreadCreated: { key in
                homeDashboardModel.pinThread(key)
                isNewTaskSheetPresented = false
            },
            onCreateProject: {
                isNewTaskSheetPresented = false
                presentProjectCreation()
            },
            onDismiss: { isNewTaskSheetPresented = false }
        )
        .environment(appModel)
        .environment(appState)
    }
}
