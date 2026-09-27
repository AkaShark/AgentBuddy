import SwiftUI
import os

private let sessionsScreenSignpostLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "com.akashark.agentbuddy.ios",
    category: "SessionsScreen"
)

extension SessionsScreen {
    func loadSessionsIfNeeded(force: Bool = false) async {
        guard autoLoadSessions else { return }
        guard force || !hasLoadedInitialSessions else { return }
        await loadSessions()
    }

    func refreshSessions() {
        Task {
            await loadSessions()
        }
    }

    private func loadSessions() async {
        let signpostID = OSSignpostID(log: sessionsScreenSignpostLog)
        os_signpost(.begin, log: sessionsScreenSignpostLog, name: "LoadSessions", signpostID: signpostID)
        defer { os_signpost(.end, log: sessionsScreenSignpostLog, name: "LoadSessions", signpostID: signpostID) }

        guard !connectedServerIds.isEmpty else {
            isLoading = false
            return
        }
        guard !isSessionLoadInFlight else {
            return
        }
        isSessionLoadInFlight = true
        defer { isSessionLoadInFlight = false }

        isLoading = true
        for serverId in connectedServerIds {
            _ = try? await appModel.client.listThreads(
                serverId: serverId,
                params: AppListThreadsRequest(
                    cursor: nil,
                    limit: Self.sessionListPageLimit,
                    sortKey: .updatedAt,
                    sortDirection: .desc,
                    archived: nil,
                    cwd: nil,
                    searchTerm: nil,
                    useStateDbOnly: false,
                    runtimeKinds: nil
                )
            )
        }
        await appModel.refreshSnapshot()

        // Seed recent directories from loaded sessions.
        if let snapshot = appModel.snapshot {
            for server in snapshot.servers {
                let entries = snapshot.sessionSummaries
                    .filter { $0.key.serverId == server.serverId && !$0.cwd.isEmpty }
                    .map { summary in
                        let date = summary.updatedAt.map { Date(timeIntervalSince1970: TimeInterval($0)) } ?? Date.distantPast
                        return RecentDirectoryEntry(
                            serverId: server.serverId,
                            path: summary.cwd,
                            lastUsedAt: date,
                            useCount: 0
                        )
                    }
                if !entries.isEmpty {
                    RecentDirectoryStore.shared.mergeSessionDirectories(entries, for: server.serverId)
                }
            }
        }

        hasLoadedInitialSessions = true
        isLoading = false
    }

    func resumeSession(_ thread: AppSessionSummary) async {
        guard resumingKey == nil else { return }
        resumingKey = thread.key
        sessionActionErrorMessage = nil
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
            if !thread.cwd.isEmpty {
                RecentDirectoryStore.shared.record(path: thread.cwd, for: thread.key.serverId)
            }
            appModel.activateThread(nextKey)
            openedKey = nextKey
        } catch {
            sessionActionErrorMessage = error.localizedDescription
            openedKey = nil
        }
        resumingKey = nil
        guard let openedKey else {
            sessionActionErrorMessage = sessionActionErrorMessage ?? "Failed to open conversation."
            return
        }
        onOpenConversation(openedKey)
    }

    func startNewSession(serverId: String, cwd: String) async {
        guard !isStartingNewSession else { return }
        isStartingNewSession = true
        defer { isStartingNewSession = false }
        sessionActionErrorMessage = nil
        do {
            guard try await appModel.ensureLocalAuthForThreadStart(serverId: serverId) else {
                return
            }
            await conversationWarmup.prewarmIfNeeded()
            workDir = cwd
            appState.currentCwd = cwd
            let startedKey = try await appModel.client.startThread(
                serverId: serverId,
                params: launchConfig().threadStartRequest(
                    cwd: cwd,
                    dynamicTools: appModel.localGenerativeUiToolSpecs(for: serverId)
                )
            )
            RecentDirectoryStore.shared.record(path: cwd, for: serverId)
            SavedThreadsStore.add(.init(threadKey: startedKey))
            appModel.store.setActiveThread(key: startedKey)
            await appModel.refreshThreadSnapshot(key: startedKey)

            // startThread already created the thread and applied it to the store;
            // prefer the snapshot key if available, otherwise use the returned key
            // directly instead of calling ensureThreadLoaded (which does expensive
            // retry loops with thread/read + thread/list RPCs).
            let resolvedKey = appModel.snapshot?.threadSnapshot(for: startedKey)?.key ?? startedKey
            onOpenConversation(resolvedKey)
        } catch {
            sessionActionErrorMessage = error.localizedDescription
        }
    }

    func forkThread(_ thread: AppSessionSummary) async {
        guard !isForkingActiveThread else { return }
        isForkingActiveThread = true
        defer { isForkingActiveThread = false }
        do {
            let sourceKey = await appModel.hydrateThreadPermissions(for: thread.key, appState: appState)
                ?? thread.key
            let nextKey = try await appModel.client.forkThread(
                serverId: sourceKey.serverId,
                params: launchConfig(for: sourceKey).threadForkRequest(
                    threadId: sourceKey.threadId,
                    cwdOverride: thread.cwd
                )
            )
            appModel.store.setActiveThread(key: nextKey)
            await appModel.refreshThreadSnapshot(key: nextKey)
            workDir = thread.cwd
            appState.currentCwd = thread.cwd
            onOpenConversation(nextKey)
        } catch {
            sessionActionErrorMessage = error.localizedDescription
        }
    }

    private func launchConfig(for threadKey: ThreadKey? = nil) -> AppThreadLaunchConfig {
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

    func submitRename() async {
        guard let key = renamingThreadKey else { return }
        let nextTitle = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !nextTitle.isEmpty else { return }
        do {
            try await appModel.renameThread(
                serverId: key.serverId,
                threadId: key.threadId,
                title: nextTitle
            )
        } catch {
            sessionActionErrorMessage = error.localizedDescription
        }
        renamingThreadKey = nil
        renameCurrentTitle = ""
        renameDraft = ""
    }

    func confirmArchiveSession() async {
        guard let key = archiveTargetKey else { return }
        do {
            _ = try await appModel.client.archiveThread(
                serverId: key.serverId,
                params: AppArchiveThreadRequest(threadId: key.threadId)
            )
            if appModel.snapshot?.activeThread == nil {
                workDir = ""
                appState.currentCwd = ""
            }
        } catch {
            sessionActionErrorMessage = error.localizedDescription
        }
        archiveTargetKey = nil
    }
}
