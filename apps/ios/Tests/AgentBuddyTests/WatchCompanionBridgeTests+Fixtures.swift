import XCTest
@testable import AgentBuddy

extension WatchCompanionBridgeTests {

    // MARK: - Factories

    func makeServer(id: String, connected: Bool = true) -> AppServerSnapshot {
        AppServerSnapshot(
            serverId: id,
            displayName: id,
            host: "\(id).local",
            port: 8390,
            wakeMac: nil,
            isLocal: false,
            health: connected ? .connected : .disconnected,
            transportState: connected ? .connected : .disconnected,
            capabilities: AppServerCapabilities(
                canUseTransportActions: connected,
                canBrowseDirectories: connected,
                canStartThreads: connected,
                canResumeThreads: connected,
                supportsTurnPagination: false
            ),
            account: nil,
            requiresOpenaiAuth: false,
            rateLimits: nil,
            rateLimitsByRuntime: [],
            availableModels: nil,
            agentRuntimes: [AgentRuntimeInfo(kind: .codex, name: "codex", displayName: "Codex", available: true)],
            connectionProgress: nil,
            usageStats: nil,
            codexVersion: nil
        )
    }

    func makeSummary(
        serverId: String,
        threadId: String,
        updatedAt: Int64?,
        hasActiveTurn: Bool,
        title: String = "",
        preview: String = "",
        lastResponsePreview: String? = nil,
        lastUserMessage: String? = nil,
        lastToolLabel: String? = nil,
        lastTurnStartMs: Int64? = nil
    ) -> AppSessionSummary {
        AppSessionSummary(
            key: ThreadKey(serverId: serverId, threadId: threadId),
            agentRuntimeKind: .codex,
            serverDisplayName: serverId,
            serverHost: "\(serverId).local",
            title: title,
            preview: preview,
            cwd: "/tmp",
            model: "",
            modelProvider: "",
            parentThreadId: nil,
            forkedFromId: nil,
            agentNickname: nil,
            agentRole: nil,
            agentDisplayLabel: nil,
            agentStatus: .unknown,
            updatedAt: updatedAt,
            hasActiveTurn: hasActiveTurn,
            isResumed: false,
            isSubagent: false,
            isFork: false,
            lastResponsePreview: lastResponsePreview,
            lastResponseTurnId: nil,
            lastUserMessage: lastUserMessage,
            lastToolLabel: lastToolLabel,
            recentToolLog: [],
            lastTurnStartMs: lastTurnStartMs,
            lastTurnEndMs: nil,
            stats: nil,
            tokenUsage: nil,
            goal: nil
        )
    }

    func makeRecord(
        servers: [AppServerSnapshot],
        sessionSummaries: [AppSessionSummary],
        activeThread: ThreadKey? = nil,
        pendingApprovals: [PendingApproval] = []
    ) -> AppSnapshotRecord {
        AppSnapshotRecord(
            servers: servers,
            threads: [],
            sessionSummaries: sessionSummaries,
            agentDirectoryVersion: 0,
            activeThread: activeThread,
            pendingApprovals: pendingApprovals,
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
}
