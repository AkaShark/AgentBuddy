import XCTest
@testable import AgentBuddy

extension WatchProjectionTests {

    // MARK: - Factories

    func makeSummary(
        serverId: String,
        threadId: String,
        updatedAt: Int64?,
        hasActiveTurn: Bool,
        title: String = "",
        lastToolLabel: String? = nil,
        lastResponsePreview: String? = nil,
        lastUserMessage: String? = nil,
        preview: String = "",
        serverDisplayName: String = "Test Server"
    ) -> AppSessionSummary {
        AppSessionSummary(
            key: ThreadKey(serverId: serverId, threadId: threadId),
            agentRuntimeKind: .codex,
            serverDisplayName: serverDisplayName,
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
            lastTurnStartMs: nil,
            lastTurnEndMs: nil,
            stats: nil,
            tokenUsage: nil,
            goal: nil
        )
    }

    func makePendingApproval(
        id: String,
        threadId: String?,
        kind: ApprovalKind,
        command: String? = nil,
        path: String? = nil,
        grantRoot: String? = nil,
        cwd: String? = nil,
        reason: String? = nil
    ) -> PendingApproval {
        PendingApproval(
            id: id,
            serverId: "srv",
            kind: kind,
            threadId: threadId,
            turnId: nil,
            itemId: nil,
            command: command,
            path: path,
            grantRoot: grantRoot,
            cwd: cwd,
            reason: reason
        )
    }

    func makeThread(
        serverId: String,
        threadId: String,
        items: [HydratedConversationItem]
    ) -> AppThreadSnapshot {
        AppThreadSnapshot(
            key: ThreadKey(serverId: serverId, threadId: threadId),
            info: ThreadInfo(
                id: threadId,
                title: nil,
                model: nil,
                status: .idle,
                preview: nil,
                cwd: nil,
                path: nil,
                modelProvider: nil,
                agentNickname: nil,
                agentRole: nil,
                parentThreadId: nil,
                forkedFromId: nil,
                agentStatus: nil,
                createdAt: nil,
                updatedAt: nil
            ),
            agentRuntimeKind: .codex,
            collaborationMode: .default,
            model: nil,
            reasoningEffort: nil,
            effectiveApprovalPolicy: nil,
            effectiveSandboxPolicy: nil,
            hydratedConversationItems: items,
            queuedFollowUps: [],
            activeTurnId: nil,
            activePlanProgress: nil,
            pendingPlanImplementationPrompt: nil,
            contextTokensUsed: nil,
            modelContextWindow: nil,
            rateLimits: nil,
            realtimeSessionId: nil,
            goal: nil,
            stats: nil,
            tokenUsage: nil,
            olderTurnsCursor: nil,
            initialTurnsLoaded: true
        )
    }

    func makeUserItem(id: String, text: String) -> HydratedConversationItem {
        HydratedConversationItem(
            id: id,
            content: .user(HydratedUserMessageData(text: text, imageDataUris: [])),
            sourceTurnId: nil,
            sourceTurnIndex: nil,
            timestamp: nil,
            isFromUserTurnBoundary: false
        )
    }

    func makeAssistantItem(id: String, text: String) -> HydratedConversationItem {
        HydratedConversationItem(
            id: id,
            content: .assistant(HydratedAssistantMessageData(
                text: text,
                agentNickname: nil,
                agentRole: nil,
                phase: nil
            )),
            sourceTurnId: nil,
            sourceTurnIndex: nil,
            timestamp: nil,
            isFromUserTurnBoundary: false
        )
    }

    func makeCommandItem(
        id: String,
        command: String,
        status: AppOperationStatus
    ) -> HydratedConversationItem {
        HydratedConversationItem(
            id: id,
            content: .commandExecution(HydratedCommandExecutionData(
                command: command,
                cwd: "/tmp",
                status: status,
                output: nil,
                exitCode: nil,
                durationMs: nil,
                processId: nil,
                actions: []
            )),
            sourceTurnId: nil,
            sourceTurnIndex: nil,
            timestamp: nil,
            isFromUserTurnBoundary: false
        )
    }

    func makeFileChangeItem(
        id: String,
        path: String,
        kind: String,
        status: AppOperationStatus,
        diff: String = "",
        additions: UInt32 = 0,
        deletions: UInt32 = 0
    ) -> HydratedConversationItem {
        HydratedConversationItem(
            id: id,
            content: .fileChange(HydratedFileChangeData(
                status: status,
                changes: [HydratedFileChangeEntryData(
                    path: path,
                    kind: kind,
                    diff: diff,
                    additions: additions,
                    deletions: deletions
                )]
            )),
            sourceTurnId: nil,
            sourceTurnIndex: nil,
            timestamp: nil,
            isFromUserTurnBoundary: false
        )
    }

    func makeFileChangeItemMulti(
        id: String,
        status: AppOperationStatus,
        entries: [HydratedFileChangeEntryData]
    ) -> HydratedConversationItem {
        HydratedConversationItem(
            id: id,
            content: .fileChange(HydratedFileChangeData(
                status: status,
                changes: entries
            )),
            sourceTurnId: nil,
            sourceTurnIndex: nil,
            timestamp: nil,
            isFromUserTurnBoundary: false
        )
    }

    func makeWebSearchItem(
        id: String,
        query: String,
        isInProgress: Bool
    ) -> HydratedConversationItem {
        HydratedConversationItem(
            id: id,
            content: .webSearch(HydratedWebSearchData(
                query: query,
                actionJson: nil,
                isInProgress: isInProgress
            )),
            sourceTurnId: nil,
            sourceTurnIndex: nil,
            timestamp: nil,
            isFromUserTurnBoundary: false
        )
    }

    func makeMcpItem(
        id: String,
        tool: String,
        contentSummary: String?,
        status: AppOperationStatus
    ) -> HydratedConversationItem {
        HydratedConversationItem(
            id: id,
            content: .mcpToolCall(HydratedMcpToolCallData(
                server: "srv",
                tool: tool,
                status: status,
                durationMs: nil,
                argumentsJson: nil,
                contentSummary: contentSummary,
                structuredContentJson: nil,
                rawOutputJson: nil,
                errorMessage: nil,
                progressMessages: [],
                computerUse: nil
            )),
            sourceTurnId: nil,
            sourceTurnIndex: nil,
            timestamp: nil,
            isFromUserTurnBoundary: false
        )
    }

    func makeSnapshot(voiceSession: AppVoiceSessionSnapshot) -> AppSnapshotRecord {
        AppSnapshotRecord(
            servers: [],
            threads: [],
            sessionSummaries: [],
            agentDirectoryVersion: 0,
            activeThread: nil,
            pendingApprovals: [],
            pendingUserInputs: [],
            voiceSession: voiceSession,
            terminalSessions: [],
            activeTerminalId: nil
        )
    }

    func makeDynamicItem(
        id: String,
        tool: String,
        contentSummary: String?,
        status: AppOperationStatus
    ) -> HydratedConversationItem {
        HydratedConversationItem(
            id: id,
            content: .dynamicToolCall(HydratedDynamicToolCallData(
                namespace: nil,
                tool: tool,
                status: status,
                durationMs: nil,
                success: nil,
                argumentsJson: nil,
                contentSummary: contentSummary,
                display: nil
            )),
            sourceTurnId: nil,
            sourceTurnIndex: nil,
            timestamp: nil,
            isFromUserTurnBoundary: false
        )
    }
}
