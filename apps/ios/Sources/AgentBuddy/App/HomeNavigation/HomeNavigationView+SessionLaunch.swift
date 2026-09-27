import SwiftUI
import os

private let homeNavigationSignpostLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "com.akashark.agentbuddy.ios",
    category: "HomeNavigation"
)

extension HomeNavigationView {
    func defaultNewSessionServerId(preferredServerId: String? = nil) -> String? {
        SessionLaunchSupport.defaultConnectedServerId(
            connectedServerIds: connectedServerOptions.map(\.id),
            activeThreadKey: appModel.snapshot?.activeThread,
            preferredServerId: preferredServerId
        )
    }

    func createAndSelectProject(serverId: String, cwd: String) {
        homeDashboardModel.selectFreshProject(serverId: serverId, cwd: cwd)
        RecentDirectoryStore.shared.record(path: cwd, for: serverId)
    }

    func handleNewSessionTap() {
        if let defaultServerId = defaultNewSessionServerId(preferredServerId: appState.sessionsSelectedServerFilterId) {
            // For local on-device server, skip directory picker and use /home/codex.
            if let server = homeDashboardModel.connectedServers.first(where: { $0.id == defaultServerId }),
               server.isLocal {
                let cwd = AgentBuddyPlatform.defaultLocalWorkingDirectory()
                Task { await startNewSession(serverId: defaultServerId, cwd: cwd) }
                return
            }
            directoryPickerSheet = SessionLaunchSupport.DirectoryPickerSheetModel(selectedServerId: defaultServerId)
        } else {
            appState.showServerPicker = true
        }
    }

    func openSessionAtIndex(_ summary: AppSessionSummary) async {
        guard openingRecentSessionKey == nil else { return }
        openingRecentSessionKey = summary.key
        actionErrorMessage = nil
        defer { openingRecentSessionKey = nil }

        await conversationWarmup.prewarmIfNeeded()
        workDir = summary.cwd
        appState.currentCwd = summary.cwd
        do {
            let resumeKey = await appModel.hydrateThreadPermissions(for: summary.key, appState: appState)
                ?? summary.key
            let nextKey = try await appModel.resumeThread(
                key: resumeKey,
                launchConfig: launchConfig(for: resumeKey),
                cwdOverride: summary.cwd
            )
            appModel.activateThread(nextKey)
            replaceTopConversation(with: nextKey)
        } catch {
            actionErrorMessage = error.localizedDescription
        }
    }

    func openRecentSession(_ thread: HomeDashboardRecentSession) async {
        guard openingRecentSessionKey == nil else { return }

        openingRecentSessionKey = thread.key
        actionErrorMessage = nil
        defer { openingRecentSessionKey = nil }

        await conversationWarmup.prewarmIfNeeded()
        workDir = thread.cwd
        appState.currentCwd = thread.cwd
        let openedKey: ThreadKey?
        do {
            let resumeKey = await appModel.hydrateThreadPermissions(for: thread.key, appState: appState)
                ?? thread.key
            let nextKey = try await appModel.resumeThread(
                key: resumeKey,
                launchConfig: launchConfig(for: resumeKey),
                cwdOverride: thread.cwd
            )
            appModel.activateThread(nextKey)
            openedKey = nextKey
        } catch {
            actionErrorMessage = error.localizedDescription
            openedKey = nil
        }
        guard let openedKey else {
            actionErrorMessage = actionErrorMessage ?? "Failed to open conversation."
            return
        }
        openConversation(openedKey)
    }

    private func startNewSession(serverId: String, cwd: String) async {
        guard !isStartingNewSession else { return }
        let signpostID = OSSignpostID(log: homeNavigationSignpostLog)
        os_signpost(
            .begin,
            log: homeNavigationSignpostLog,
            name: "StartNewSession",
            signpostID: signpostID,
            "server=%{public}@ cwd=%{public}@",
            serverId,
            cwd
        )
        isStartingNewSession = true
        defer {
            isStartingNewSession = false
            os_signpost(.end, log: homeNavigationSignpostLog, name: "StartNewSession", signpostID: signpostID)
        }
        actionErrorMessage = nil
        let startedKey: ThreadKey
        do {
            guard try await appModel.ensureLocalAuthForThreadStart(serverId: serverId) else {
                return
            }
            await conversationWarmup.prewarmIfNeeded()
            workDir = cwd
            appState.currentCwd = cwd
            let key = try await appModel.client.startThread(
                serverId: serverId,
                params: launchConfig().threadStartRequest(
                    cwd: cwd,
                    dynamicTools: appModel.localGenerativeUiToolSpecs(for: serverId)
                )
            )
            startedKey = key
            RecentDirectoryStore.shared.record(path: cwd, for: serverId)
            homeDashboardModel.pinThread(key)
            appModel.store.setActiveThread(key: startedKey)
            await appModel.refreshThreadSnapshot(key: startedKey)
        } catch {
            actionErrorMessage = error.localizedDescription
            return
        }

        guard let resolvedKey = await appModel.ensureThreadLoaded(key: startedKey)
            ?? appModel.snapshot?.threadSnapshot(for: startedKey)?.key else {
            actionErrorMessage = appModel.lastError ?? "Failed to load the new session."
            return
        }

        openConversation(resolvedKey)
    }

    func launchConfig(for threadKey: ThreadKey? = nil) -> AppThreadLaunchConfig {
        let selectedModel = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
        let hasSelectedModel = !selectedModel.isEmpty
        return AppThreadLaunchConfig(
            agentRuntimeKind: hasSelectedModel ? appState.selectedAgentRuntimeKind : nil,
            model: hasSelectedModel ? selectedModel : nil,
            approvalPolicy: appState.launchApprovalPolicy(for: threadKey),
            sandbox: appState.launchSandboxMode(for: threadKey),
            developerInstructions: nil,
            persistExtendedHistory: true
        )
    }
}
