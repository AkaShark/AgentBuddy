import SwiftUI

extension HomeNavigationView {
    func pinThread(_ key: ThreadKey) {
        let shouldUnsubscribeDisplacedRecent = homeDashboardModel.pinnedKeys.isEmpty
        let displacedKeys = shouldUnsubscribeDisplacedRecent
            ? Set(homeDashboardModel.recentSessions.map(\.key)).subtracting([key])
            : []
        homeDashboardModel.pinThread(key)
        unsubscribeHomeThreads(Array(displacedKeys))
    }

    func unpinThread(_ key: ThreadKey) {
        homeDashboardModel.unpinThread(key)
    }

    func hideThread(_ key: ThreadKey) {
        homeDashboardModel.hideThread(key)
        unsubscribeHomeThreads([key])
    }

    private func unsubscribeHomeThreads(_ keys: [ThreadKey]) {
        let uniqueKeys = Array(Set(keys))
        guard !uniqueKeys.isEmpty else { return }
        Task {
            for key in uniqueKeys {
                do {
                    try await appModel.store.unsubscribeThread(key: key)
                } catch {
                    LLog.warn(
                        "transport",
                        "failed to unsubscribe hidden/displaced home thread",
                        fields: [
                            "serverId": key.serverId,
                            "threadId": key.threadId,
                            "error": String(describing: error)
                        ]
                    )
                }
            }
        }
    }

    func deleteThread(_ key: ThreadKey) async {
        _ = try? await appModel.client.archiveThread(
            serverId: key.serverId,
            params: AppArchiveThreadRequest(threadId: key.threadId)
        )
        await appModel.refreshThreadSnapshot(key: key)
    }

    /// Long-press → "Fork" on a home session card. Head-of-thread fork:
    /// duplicates the full thread server-side (no rollback) and navigates
    /// to the new copy. Mirrors `ConversationInfoView.forkConversation`.
    @MainActor
    func forkSessionFromHome(_ session: HomeDashboardRecentSession) async {
        let threadKey = session.key
        do {
            let sourceKey = await appModel.hydrateThreadPermissions(for: threadKey, appState: appState) ?? threadKey
            let source = appModel.snapshot?.threadSnapshot(for: sourceKey)
            let newKey = try await appModel.client.forkThread(
                serverId: sourceKey.serverId,
                params: AppThreadLaunchConfig(
                    model: source?.model,
                    approvalPolicy: appState.launchApprovalPolicy(for: sourceKey),
                    sandbox: appState.launchSandboxMode(for: sourceKey),
                    developerInstructions: nil,
                    persistExtendedHistory: true
                ).threadForkRequest(threadId: sourceKey.threadId, cwdOverride: source?.info.cwd)
            )
            appModel.store.setActiveThread(key: newKey)
            await appModel.refreshThreadSnapshot(key: newKey)
            openConversation(newKey)
        } catch {
            actionErrorMessage = error.localizedDescription
        }
    }

    @MainActor
    func cancelThread(_ threadKey: ThreadKey) async {
        // Look up the thread's active turn id — interrupt requires both.
        guard let thread = appModel.snapshot?.threadSnapshot(for: threadKey),
              let turnId = thread.activeTurnId?
                .trimmingCharacters(in: .whitespacesAndNewlines),
              !turnId.isEmpty else {
            return
        }
        do {
            _ = try await appModel.client.interruptTurn(
                serverId: threadKey.serverId,
                params: AppInterruptTurnRequest(
                    threadId: threadKey.threadId,
                    turnId: turnId
                )
            )
            await appModel.refreshThreadSnapshot(key: threadKey)
        } catch {
            actionErrorMessage = error.localizedDescription
        }
    }

    @MainActor
    func sendQuickReply(_ threadKey: ThreadKey, text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        // The server needs the thread resumed before `startTurn` can find
        // it — same path `openRecentSession` takes. On a cold launch the
        // thread is in hydrated snapshot state but not yet registered with
        // the upstream session, so a quick-reply without resume would fail
        // with "thread cannot be found".
        let resumeKey = await appModel.hydrateThreadPermissions(for: threadKey, appState: appState)
            ?? threadKey
        let activeKey: ThreadKey
        do {
            activeKey = try await appModel.resumeThread(
                key: resumeKey,
                launchConfig: launchConfig(for: resumeKey),
                cwdOverride: nil
            )
        } catch {
            actionErrorMessage = error.localizedDescription
            return
        }
        let payload = AppComposerPayload(
            text: trimmed,
            additionalInputs: [],
            approvalPolicy: appState.launchApprovalPolicy(for: activeKey),
            sandboxPolicy: appState.turnSandboxPolicy(for: activeKey),
            model: nil,
            effort: nil,
            serviceTier: nil
        )
        do {
            try await appModel.startTurn(key: activeKey, payload: payload)
            await appModel.refreshThreadSnapshot(key: activeKey)
        } catch {
            actionErrorMessage = error.localizedDescription
        }
    }

    @Sendable
    func loadSearchThreads(
        query: String,
        runtimeKind: AgentRuntimeKind?,
        serverId selectedServerId: String?,
        forceRepair: Bool
    ) async {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let sourceKinds: [AppThreadSourceKind] = [.cli, .vsCode, .appServer]
        let selectedServerFilterId = selectedServerId?.trimmingCharacters(in: .whitespacesAndNewlines)
        await withTaskGroup(of: Void.self) { group in
            for server in homeDashboardModel.connectedServers {
                if let selectedServerFilterId, !selectedServerFilterId.isEmpty, server.id != selectedServerFilterId {
                    continue
                }
                if let runtimeKind,
                   !server.agentRuntimes.contains(where: { $0.available && $0.kind == runtimeKind }) {
                    continue
                }
                let serverId = server.id
                group.addTask {
                    _ = try? await appModel.client.listThreads(
                        serverId: serverId,
                        params: AppListThreadsRequest(
                            cursor: nil,
                            limit: 80,
                            sortKey: .updatedAt,
                            sortDirection: .desc,
                            modelProviders: nil,
                            sourceKinds: sourceKinds,
                            archived: false,
                            cwd: nil,
                            searchTerm: trimmedQuery.isEmpty ? nil : trimmedQuery,
                            useStateDbOnly: !forceRepair,
                            runtimeKinds: runtimeKind.map { [$0] }
                        )
                    )
                }
            }
        }
    }
}
