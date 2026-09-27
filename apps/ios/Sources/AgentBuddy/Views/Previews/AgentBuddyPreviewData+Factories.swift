import Foundation

#if DEBUG
import SwiftUI

extension AgentBuddyPreviewData {
    @MainActor
    static func makeAppState(
        selectedModel: String = sampleModels[0].id,
        reasoningEffort: String = "xhigh",
        currentCwd: String = sampleCwd
    ) -> AppState {
        let state = AppState()
        state.selectedModel = selectedModel
        state.reasoningEffort = reasoningEffort
        state.currentCwd = currentCwd
        return state
    }

    @MainActor
    static func makeAppModel(snapshot: AppSnapshotRecord) -> AppModel {
        let model = AppModel()
        model.applySnapshot(snapshot)
        return model
    }

    @MainActor
    static func makeConversationAppModel(messages: [ChatMessage] = sampleMessages) -> AppModel {
        makeAppModel(snapshot: makeConversationSnapshot(messages: messages))
    }

    @MainActor
    static func makeDiscoveryAppModel() -> AppModel {
        makeAppModel(snapshot: makeSnapshot(threads: [], activeThread: nil))
    }

    @MainActor
    static func makeSidebarAppModel() -> AppModel {
        let primaryThread = makeThreadSnapshot(
            threadId: "thread-preview-main",
            preview: "Map the patch repair bottleneck in repo scheduler",
            cwd: sampleCwd,
            model: sampleModels[0].id,
            modelProvider: sampleModels[0].displayName,
            reasoningEffort: "xhigh",
            status: .idle,
            messages: sampleMessages
        )

        let forkThread = makeThreadSnapshot(
            threadId: "thread-preview-fork",
            preview: "Check whether repo-first mode is enabled",
            cwd: sampleCwd + "/shared",
            model: sampleModels[1].id,
            modelProvider: sampleModels[1].displayName,
            reasoningEffort: "medium",
            status: .idle,
            messages: [
                ChatMessage(role: .user, text: "is repo-first mode actually enabled?"),
                ChatMessage(role: .assistant, text: "Not from the current scheduler state. The gate is configured but starved.")
            ],
            parentThreadId: "thread-preview-main",
            agentNickname: "Latest",
            agentRole: "explorer",
            updatedAt: Date().addingTimeInterval(-1800)
        )

        let archivedThread = makeThreadSnapshot(
            threadId: "thread-preview-older",
            preview: "Summarize queue metrics from the last hour",
            cwd: sampleCwd,
            model: sampleModels[0].id,
            modelProvider: sampleModels[0].displayName,
            reasoningEffort: "high",
            status: .idle,
            messages: [ChatMessage(role: .assistant, text: "Queue metrics look stable except for repo_jobs_q1.")],
            updatedAt: Date().addingTimeInterval(-7200)
        )

        return makeAppModel(
            snapshot: makeSnapshot(
                threads: [primaryThread, forkThread, archivedThread],
                activeThread: primaryThread.key
            )
        )
    }

    @MainActor
    static func makeConversationSnapshot(messages: [ChatMessage] = sampleMessages) -> AppSnapshotRecord {
        let thread = makeThreadSnapshot(
            threadId: "thread-preview-main",
            preview: "Map the patch repair bottleneck in repo scheduler",
            cwd: sampleCwd,
            model: sampleModels[0].id,
            modelProvider: sampleModels[0].displayName,
            reasoningEffort: "xhigh",
            status: .idle,
            messages: messages
        )
        return makeSnapshot(threads: [thread], activeThread: thread.key)
    }

    @MainActor
    static func makeThreadSnapshot(
        server: DiscoveredServer = sampleServer,
        threadId: String,
        preview: String,
        cwd: String,
        model: String,
        modelProvider: String,
        reasoningEffort: String,
        status: ThreadSummaryStatus,
        messages: [ChatMessage],
        parentThreadId: String? = nil,
        agentNickname: String? = nil,
        agentRole: String? = nil,
        updatedAt: Date = Date().addingTimeInterval(-300)
    ) -> AppThreadSnapshot {
        AppThreadSnapshot(
            key: ThreadKey(serverId: server.id, threadId: threadId),
            info: ThreadInfo(
                id: threadId,
                title: nil,
                model: model,
                status: status,
                preview: preview,
                cwd: cwd,
                path: cwd + "/.codex/sessions/\(threadId).jsonl",
                modelProvider: modelProvider,
                agentNickname: agentNickname,
                agentRole: agentRole,
                parentThreadId: parentThreadId,
                forkedFromId: nil,
                agentStatus: nil,
                createdAt: nil,
                updatedAt: Int64(updatedAt.timeIntervalSince1970)
            ),
            agentRuntimeKind: "codex",
            collaborationMode: .`default`,
            model: model,
            reasoningEffort: reasoningEffort,
            effectiveApprovalPolicy: nil,
            effectiveSandboxPolicy: nil,
            hydratedConversationItems: makeHydratedConversationItems(from: messages),
            queuedFollowUps: [],
            activeTurnId: status == .active ? "turn-preview" : nil,
            activePlanProgress: nil,
            pendingPlanImplementationPrompt: nil,
            contextTokensUsed: 156_000,
            modelContextWindow: 200_000,
            rateLimits: nil,
            realtimeSessionId: nil,
            goal: nil,
            stats: nil,
            tokenUsage: nil,
            olderTurnsCursor: nil,
            initialTurnsLoaded: true
        )
    }

    @MainActor
    static func makeSnapshot(
        threads: [AppThreadSnapshot],
        activeThread: ThreadKey?
    ) -> AppSnapshotRecord {
        let server = AppServerSnapshot(
            serverId: sampleServer.id,
            displayName: sampleServer.name,
            host: sampleServer.hostname,
            port: UInt16(sampleServer.port ?? 8390),
            wakeMac: sampleServer.wakeMAC,
            isLocal: false,
            health: .connected,
            transportState: .connected,
            capabilities: AppServerCapabilities(
                canUseTransportActions: true,
                canBrowseDirectories: true,
                canStartThreads: true,
                canResumeThreads: true,
                supportsTurnPagination: true
            ),
            account: .chatgpt(email: "builder@example.com", planType: .plus),
            requiresOpenaiAuth: false,
            rateLimits: nil,
            rateLimitsByRuntime: [],
            availableModels: sampleModels,
            agentRuntimes: [AgentRuntimeInfo(kind: "codex", name: "codex", displayName: "Codex", available: true)],
            connectionProgress: nil,
            usageStats: nil,
            codexVersion: "0.125.0"
        )

        let sessionSummaries = threads.map { thread in
            AppSessionSummary(
                key: thread.key,
                agentRuntimeKind: thread.agentRuntimeKind,
                serverDisplayName: server.displayName,
                serverHost: server.host,
                title: thread.info.title ?? "",
                preview: thread.info.preview ?? "",
                cwd: thread.info.cwd ?? "",
                model: thread.model ?? "",
                modelProvider: thread.info.modelProvider ?? "",
                parentThreadId: thread.info.parentThreadId,
                forkedFromId: thread.info.forkedFromId,
                agentNickname: thread.info.agentNickname,
                agentRole: thread.info.agentRole,
                agentDisplayLabel: AgentLabelFormatter.format(
                    nickname: thread.info.agentNickname,
                    role: thread.info.agentRole,
                    fallbackIdentifier: thread.key.threadId
                ),
                agentStatus: .unknown,
                updatedAt: thread.info.updatedAt,
                hasActiveTurn: thread.hasActiveTurn,
                isResumed: false,
                isSubagent: thread.info.parentThreadId != nil,
                isFork: thread.info.forkedFromId != nil,
                lastResponsePreview: nil,
                lastResponseTurnId: nil,
                lastUserMessage: nil,
                lastToolLabel: nil,
                recentToolLog: [],
                lastTurnStartMs: nil,
                lastTurnEndMs: nil,
                stats: nil,
                tokenUsage: nil,
                goal: nil
            )
        }

        return AppSnapshotRecord(
            servers: [server],
            threads: threads,
            sessionSummaries: sessionSummaries,
            agentDirectoryVersion: 0,
            activeThread: activeThread,
            pendingApprovals: [],
            pendingUserInputs: [],
            voiceSession: AppVoiceSessionSnapshot(
                activeThread: nil,
                sessionId: nil,
                phase: nil,
                lastError: nil,
                transcriptEntries: [],
                handoffThreadKey: nil
            ),
            terminalSessions: [],
            activeTerminalId: nil
        )
    }

    private static func makeHydratedConversationItems(from messages: [ChatMessage]) -> [HydratedConversationItem] {
        messages.map { message in
            let content: HydratedConversationItemContent
            switch message.role {
            case .user:
                content = .user(
                    HydratedUserMessageData(
                        text: message.text,
                        imageDataUris: []
                    )
                )
            case .assistant:
                content = .assistant(
                    HydratedAssistantMessageData(
                        text: message.text,
                        agentNickname: message.agentNickname,
                        agentRole: message.agentRole,
                        phase: nil
                    )
                )
            case .system:
                content = .note(HydratedNoteData(title: "System", body: message.text))
            }

            return HydratedConversationItem(
                id: message.id.uuidString,
                content: content,
                sourceTurnId: message.sourceTurnId,
                sourceTurnIndex: message.sourceTurnIndex.map(UInt32.init),
                timestamp: message.timestamp.timeIntervalSince1970,
                isFromUserTurnBoundary: message.isFromUserTurnBoundary
            )
        }
    }
}
#endif
