import Foundation

extension VoiceRuntimeController {
    func handleRealtimeStarted(key: ThreadKey, notification: AppRealtimeStartedNotification) {
        guard var session = activeVoiceSession, session.threadKey == key else { return }
        session.sessionId = notification.sessionId
        session.phase = .listening
        session.isListening = true
        activeVoiceSession = session

        scheduleSharedVoiceSessionSync(for: key)
    }

    func handleRealtimeSdp(key: ThreadKey, notification: AppRealtimeSdpNotification) {
        LLog.info("voice", "handleRealtimeSdp entry", fields: [
            "thread_id": key.threadId,
            "sdp_len": notification.sdp.count,
        ])
        guard activeVoiceSession?.threadKey == key else {
            LLog.warn("voice", "RealtimeSdp ignored — thread key mismatch")
            return
        }
        guard let session = realtimeSession else {
            LLog.warn("voice", "received RealtimeSdp without an active WebRTC session")
            return
        }
        Task { @MainActor [weak self] in
            do {
                try await session.applyAnswer(notification.sdp)
                LLog.info("voice", "applyAnswer completed")
            } catch {
                LLog.error("voice", "applyAnswer failed", error: error)
                self?.failVoiceSession("Failed to apply realtime answer: \(error.localizedDescription)")
            }
        }
    }

    func handleRealtimeRouteChanged(_ route: VoiceSessionAudioRoute) {
        guard var session = activeVoiceSession else { return }
        session.route = route
        activeVoiceSession = session
        syncVoiceCallActivity()
    }

    func handleRealtimeTranscriptUpdated(key: ThreadKey, update: AppVoiceTranscriptUpdate) {
        guard activeVoiceSession?.threadKey == key else { return }
        scheduleSharedVoiceSessionSync(for: key)
    }

    func handleRealtimeHandoffRequested(key: ThreadKey, request: AppVoiceHandoffRequest) {
        guard activeVoiceSession?.threadKey == key else { return }

        syncHandoffServers()
        handoffManager.handleHandoffRequest(
            handoffId: request.handoffId,
            voiceServerId: key.serverId,
            voiceThreadId: key.threadId,
            inputTranscript: request.inputTranscript,
            activeTranscript: request.activeTranscript,
            serverHint: request.serverHint,
            fallbackTranscript: request.fallbackTranscript
        )
        processHandoffActions()
        scheduleSharedVoiceSessionSync(for: key)
    }

    func handleRealtimeSpeechStarted(key: ThreadKey) {
        guard activeVoiceSession?.threadKey == key else { return }
        scheduleSharedVoiceSessionSync(for: key)
    }

    func handleRealtimeOutputAudioDelta(key: ThreadKey, notification: AppRealtimeOutputAudioDeltaNotification) {
        // Dead path under WebRTC transport: audio arrives over the peer
        // connection rather than as RPC deltas. Retained only for exhaustive
        // match on the shared update enum.
        _ = key
        _ = notification
    }

    func handleRealtimeError(key: ThreadKey, notification: AppRealtimeErrorNotification) {
        guard activeVoiceSession?.threadKey == key else { return }
        // Ignore transient "active response in progress" errors — they don't
        // indicate a broken session.
        if notification.message.contains("active response in progress") {
            return
        }
        failVoiceSession(notification.message)
    }

    func handleRealtimeClosed(key: ThreadKey, notification: AppRealtimeClosedNotification) {
        guard activeVoiceSession?.threadKey == key else { return }

        let reason = notification.reason?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let userRequested = voiceStopRequestedThreadKey == key

        // User-initiated stop: clean end, even if the session was already in
        // an error state.
        if userRequested {
            voiceStopRequestedThreadKey = nil
            endVoiceSessionImmediately()
            return
        }

        // Close arriving after a RealtimeError carries reason="requested"
        // because the server auto-closes the broken session. Keep the
        // session alive in its error state so the user can see what went
        // wrong and dismiss via the End button.
        if activeVoiceSession?.phase == .error {
            return
        }

        // Unexpected close — end the session with an error so the UI doesn't
        // get stuck in a stale "Listening" / "Speaking" state.
        let message: String
        switch reason {
        case "":
            message = "Voice session closed unexpectedly"
        case "requested":
            message = "Voice session ended by the server"
        case "transport_closed":
            message = "Realtime transport closed unexpectedly"
        case "error":
            message = "Realtime session ended with an error"
        default:
            message = "Voice session closed: \(reason)"
        }
        failVoiceSession(message)
    }
}
