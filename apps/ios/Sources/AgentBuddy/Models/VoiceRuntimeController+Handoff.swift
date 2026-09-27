import Foundation

extension VoiceRuntimeController {
    func syncHandoffServers() {
        guard let servers = appModel?.snapshot?.servers else { return }
        handoffManager.reset()
        for server in servers {
            handoffManager.registerServer(
                serverId: server.serverId,
                name: server.displayName,
                hostname: server.host,
                isLocal: server.isLocal,
                isConnected: server.isConnected
            )
        }
        handoffManager.setTurnConfig(model: handoffModel, effort: handoffEffort, fastMode: handoffFastMode)
    }

    func processHandoffActions() {
        let actions = handoffManager.drainActions()
        for action in actions { dispatchSingleHandoffAction(action) }
    }

    private func dispatchSingleHandoffAction(_ action: HandoffAction) {
        switch action {
        case .startThread(let hid, let sid, _, let cwd):
            Task { @MainActor in await self.executeHandoffStartThread(handoffId: hid, serverId: sid, cwd: cwd) }
        case .sendTurn(let hid, let sid, let tid, let transcript, let config):
            Task { @MainActor in await self.executeHandoffSendTurn(handoffId: hid, serverId: sid, threadId: tid, transcript: transcript, model: config.model, effort: config.effort, fastMode: config.fastMode) }
        case .resolveHandoff(let hid, let vtk, let text):
            Task { @MainActor in await self.executeHandoffResolve(handoffId: hid, voiceServerId: vtk.serverId, voiceThreadId: vtk.threadId, text: text) }
        case .finalizeHandoff(let hid, let vtk):
            Task { @MainActor in await self.executeHandoffFinalize(handoffId: hid, voiceServerId: vtk.serverId, voiceThreadId: vtk.threadId) }
        case .setVoicePhase(let phase):
            if var session = activeVoiceSession {
                switch phase {
                case "listening": session.phase = .listening
                case "thinking": session.phase = .thinking
                case "handoff": session.phase = .handoff
                default: break
                }
                activeVoiceSession = session
                syncVoiceCallActivity()
            }
        case .updateHandoffItem, .completeHandoffItem, .error:
            break
        }
    }

    private func executeHandoffStartThread(handoffId: String, serverId: String, cwd: String) async {
        guard let appModel else { return }
        do {
            let key = try await appModel.client.startThread(
                serverId: serverId,
                params: AppThreadLaunchConfig(
                    model: handoffModel,
                    approvalPolicy: .never,
                    sandbox: .dangerFullAccess,
                    developerInstructions: nil,
                    persistExtendedHistory: true
                ).threadStartRequest(
                    cwd: cwd,
                    dynamicTools: appModel.localGenerativeUiToolSpecs(for: serverId)
                )
            )
            SavedThreadsStore.add(.init(threadKey: key))
            appModel.store.setActiveThread(key: key)
            await appModel.refreshSnapshot()
            handoffManager.reportThreadCreated(handoffId: handoffId, serverId: serverId, threadId: key.threadId)
            appModel.store.setVoiceHandoffThread(key: key)
            await syncSharedVoiceSessionFromStore(for: activeVoiceSession?.threadKey)
            processHandoffActions()
        } catch {
            handoffManager.reportThreadFailed(handoffId: handoffId, error: error.localizedDescription)
            processHandoffActions()
        }
    }

    private func executeHandoffSendTurn(
        handoffId: String,
        serverId: String,
        threadId: String,
        transcript: String,
        model: String?,
        effort: String?,
        fastMode: Bool
    ) async {
        guard let appModel else { return }
        let key = ThreadKey(serverId: serverId, threadId: threadId)
        do {
            try await appModel.startTurn(
                key: key,
                payload: AppComposerPayload(
                    text: transcript,
                    additionalInputs: [],
                    approvalPolicy: .never,
                    sandboxPolicy: .dangerFullAccess,
                    model: model,
                    effort: ReasoningEffort(wireValue: effort),
                    serviceTier: fastMode ? .fast : nil
                )
            )
            handoffManager.reportTurnSent(handoffId: handoffId, baseItemCount: 0)
            startHandoffStreamPolling(handoffId: handoffId, key: key)
            processHandoffActions()
        } catch {
            handoffManager.reportTurnFailed(handoffId: handoffId, error: error.localizedDescription)
            processHandoffActions()
        }
    }

    private func executeHandoffResolve(
        handoffId: String,
        voiceServerId: String,
        voiceThreadId: String,
        text: String
    ) async {
        _ = handoffId
        _ = try? await requireAppModel().client.resolveRealtimeHandoff(
            serverId: voiceServerId,
            params: AppResolveRealtimeHandoffRequest(
                threadId: voiceThreadId,
                toolCallOutput: text
            )
        )
        processHandoffActions()
    }

    private func executeHandoffFinalize(
        handoffId: String,
        voiceServerId: String,
        voiceThreadId: String
    ) async {
        _ = try? await requireAppModel().client.finalizeRealtimeHandoff(
            serverId: voiceServerId,
            params: AppFinalizeRealtimeHandoffRequest(
                threadId: voiceThreadId
            )
        )
        handoffManager.reportFinalized(handoffId: handoffId)
        requireAppModel().store.setVoiceHandoffThread(key: nil)
        await syncSharedVoiceSessionFromStore(for: activeVoiceSession?.threadKey)
        processHandoffActions()
    }

    private func startHandoffStreamPolling(handoffId: String, key: ThreadKey) {
        handoffActionPollTask?.cancel()
        handoffActionPollTask = Task { @MainActor [weak self] in
            guard let self else { return }
            var inactivePolls = 0
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 500_000_000)
                guard let thread = self.appModel?.snapshot?.threadSnapshot(for: key) else { break }
                let turnActive = thread.activeTurnId != nil || thread.info.status == .active
                let items: [(id: String, text: String)] = thread.hydratedConversationItems.suffix(20).compactMap { item in
                    let conversationItem = item.conversationItem
                    switch conversationItem.content {
                    case .assistant(let data):
                        return (conversationItem.id, data.text)
                    case .codeReview(let data):
                        guard let first = data.findings.first else { return nil }
                        return (conversationItem.id, "[review] \(first.title)")
                    case .commandExecution(let data):
                        return (conversationItem.id, "[cmd] \(data.command.prefix(80)) \(data.status.displayLabel)")
                    case .mcpToolCall(let data):
                        return (conversationItem.id, "[\(data.tool)] \(data.status.displayLabel)")
                    default:
                        return nil
                    }
                }
                self.handoffManager.pollStreamProgress(handoffId: handoffId, items: items, turnActive: turnActive)
                self.processHandoffActions()
                if turnActive {
                    inactivePolls = 0
                } else {
                    inactivePolls += 1
                    if inactivePolls >= 3 { break }
                }
            }
        }
    }
}
