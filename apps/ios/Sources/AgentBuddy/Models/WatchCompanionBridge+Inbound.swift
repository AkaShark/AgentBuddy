import Foundation

extension WatchCompanionBridge {
    // MARK: - Inbound

    /// Called by the `WCSessionDelegate` proxy on the main actor.
    /// Returns a reply payload (`{ok, error?, ...}`) that the delegate
    /// forwards back through `replyHandler`. Returning `nil` means there's
    /// no specific result for this kind — the delegate will reply with a
    /// generic ack.
    func handleInbound(_ message: [String: Any]) async -> [String: Any]? {
        guard let kind = message["kind"] as? String else {
            return nil
        }
        switch kind {
        case "approval.decision":
            return await handleApprovalDecision(message)

        case "prompt.send":
            return await handlePromptSend(message)

        case "snapshot.request":
            lastPushedPayload = nil
            lastPushedComplication = nil
            pushIfChanged()
            return ["ok": true]

        case "voice.start":
            return await handleVoiceStart(message)

        case "voice.stop":
            return await handleVoiceStop()

        case "voice.toggleMute":
            return await handleVoiceToggleMute()

        case "voice.bargeIn":
            return await handleVoiceBargeIn()

        case "home.hide":
            return handleHomeHide(message)

        case "home.unhide":
            return handleHomeUnhide(message)

        default:
            return nil
        }
    }

    // MARK: Inbound — home visibility

    private func handleHomeHide(_ message: [String: Any]) -> [String: Any] {
        guard let key = threadKey(from: message) else {
            return ["ok": false, "error": "invalid hide payload"]
        }
        SavedThreadsStore.hide(PinnedThreadKey(threadKey: key))
        // The preferences observer fires a re-push; reply immediately so the
        // watch's swipe action feels snappy.
        return ["ok": true]
    }

    private func handleHomeUnhide(_ message: [String: Any]) -> [String: Any] {
        guard let key = threadKey(from: message) else {
            return ["ok": false, "error": "invalid unhide payload"]
        }
        SavedThreadsStore.unhide(PinnedThreadKey(threadKey: key))
        return ["ok": true]
    }

    private func threadKey(from message: [String: Any]) -> ThreadKey? {
        guard
            let serverId = (message["serverId"] as? String).flatMap({ $0.isEmpty ? nil : $0 }),
            let threadId = (message["threadId"] as? String).flatMap({ $0.isEmpty ? nil : $0 })
        else { return nil }
        return ThreadKey(serverId: serverId, threadId: threadId)
    }

    // MARK: Inbound — approvals

    private func handleApprovalDecision(_ message: [String: Any]) async -> [String: Any] {
        guard
            let requestId = message["requestId"] as? String,
            let approve = message["approve"] as? Bool
        else {
            return ["ok": false, "error": "invalid approval payload"]
        }
        do {
            try await AppModel.shared.store.respondToApproval(
                requestId: requestId,
                decision: approve ? .accept : .decline
            )
            return ["ok": true]
        } catch {
            return ["ok": false, "error": error.localizedDescription]
        }
    }

    // MARK: Inbound — prompt

    private func handlePromptSend(_ message: [String: Any]) async -> [String: Any] {
        guard let text = (message["text"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty else {
            return ["ok": false, "error": "empty prompt"]
        }
        let serverId = (message["serverId"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        let threadId = (message["threadId"] as? String).flatMap { $0.isEmpty ? nil : $0 }

        // 1) explicit (serverId, threadId) — drop on that thread if known.
        if let serverId, let threadId {
            let key = ThreadKey(serverId: serverId, threadId: threadId)
            if AppModel.shared.snapshot?.sessionSummaries.contains(where: { $0.key == key }) == true ||
               AppModel.shared.snapshot?.threads.contains(where: { $0.key == key }) == true {
                AppModel.shared.queueComposerPrefill(threadKey: key, text: text)
                return ["ok": true, "threadId": threadId]
            }
        }

        // 2) serverId only — start a new thread on that server, prefill composer.
        if let serverId, threadId == nil {
            do {
                let cwd = preferredCwd(for: serverId)
                let request = AppThreadLaunchConfig(
                    model: nil,
                    approvalPolicy: nil,
                    sandbox: nil,
                    developerInstructions: nil,
                    persistExtendedHistory: true
                ).threadStartRequest(
                    cwd: cwd,
                    dynamicTools: AppModel.shared.localGenerativeUiToolSpecs(for: serverId)
                )
                let key = try await AppModel.shared.client.startThread(
                    serverId: serverId,
                    params: request
                )
                // Pin so the thread shows on the (pinned-only) home — same
                // behavior as the iPhone home composer, voice, and sessions
                // start-thread paths.
                SavedThreadsStore.add(PinnedThreadKey(threadKey: key))
                AppModel.shared.store.setActiveThread(key: key)
                AppModel.shared.queueComposerPrefill(threadKey: key, text: text)
                return ["ok": true, "threadId": key.threadId]
            } catch {
                return ["ok": false, "error": error.localizedDescription]
            }
        }

        // 3) fall back to the iOS-active thread.
        if let key = AppModel.shared.snapshot?.activeThread {
            AppModel.shared.queueComposerPrefill(threadKey: key, text: text)
            return ["ok": true, "threadId": key.threadId]
        }

        return ["ok": false, "error": "no active task"]
    }

    private func preferredCwd(for serverId: String) -> String {
        if let recent = RecentDirectoryStore.shared.recentDirectories(for: serverId, limit: 1).first {
            return recent.path
        }
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
            .first?.path ?? "/"
    }

    // MARK: Inbound — voice

    private func voiceFeatureGate() -> [String: Any]? {
        guard ExperimentalFeatures.shared.isEnabled(.realtimeVoice) else {
            return ["ok": false, "error": "realtime voice disabled"]
        }
        return nil
    }

    private func handleVoiceStart(_ message: [String: Any]) async -> [String: Any] {
        if let blocked = voiceFeatureGate() { return blocked }
        guard let serverId = (message["serverId"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines),
              !serverId.isEmpty else {
            return ["ok": false, "error": "missing serverId"]
        }
        let threadId = (message["threadId"] as? String).flatMap {
            $0.isEmpty ? nil : $0
        }

        let controller = VoiceRuntimeController.shared
        controller.bind(appModel: AppModel.shared)

        do {
            if let threadId {
                let resolved = try await controller.startVoiceOnThread(
                    ThreadKey(serverId: serverId, threadId: threadId)
                )
                return ["ok": true, "threadId": resolved.threadId]
            } else {
                let cwd = preferredCwd(for: serverId)
                let resolved = try await controller.startPinnedLocalVoiceCall(
                    cwd: cwd,
                    model: nil,
                    approvalPolicy: nil,
                    sandboxMode: nil
                )
                return ["ok": true, "threadId": resolved.threadId]
            }
        } catch {
            return ["ok": false, "error": error.localizedDescription]
        }
    }

    private func handleVoiceStop() async -> [String: Any] {
        if let blocked = voiceFeatureGate() { return blocked }
        await VoiceRuntimeController.shared.stopActiveVoiceSession()
        return ["ok": true]
    }

    private func handleVoiceToggleMute() async -> [String: Any] {
        if let blocked = voiceFeatureGate() { return blocked }
        let controller = VoiceRuntimeController.shared
        guard controller.activeVoiceSession != nil else {
            return ["ok": false, "error": "no active voice session"]
        }
        controller.setMicrophoneMuted(!controller.isMicrophoneMuted)
        // Force a fresh push so the watch's `WatchVoiceState.isMuted`
        // reflects the new state on the next pump.
        lastPushedPayload = nil
        pushIfChanged()
        return ["ok": true, "isMuted": controller.isMicrophoneMuted]
    }

    private func handleVoiceBargeIn() async -> [String: Any] {
        // Same situation as mute: there's no client-side cancel-response
        // entry point yet. Reply with an error so the watch UI can hide the
        // affordance.
        return [
            "ok": false,
            "error": "barge-in not yet wired into iOS realtime session",
        ]
    }
}
