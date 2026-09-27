import AVFoundation
import Foundation

extension VoiceRuntimeController {
    /// Synchronously prepares the in-memory voice session (resolves the
    /// thread, sets `activeVoiceSession = .connecting`, attaches a fresh
    /// `RealtimeWebRtcSession`) and returns the resolved `ThreadKey`. The
    /// slow WebRTC handshake + RPC start runs in a detached background task
    /// so callers can push the voice navigation route immediately.
    func prepareAndLaunchRealtimeVoiceSession(
        for key: ThreadKey,
        model: String? = nil
    ) async throws -> ThreadKey {
        LLog.info("voice", "prepareAndLaunchRealtimeVoiceSession entry", fields: [
            "server_id": key.serverId,
            "thread_id": key.threadId,
        ])

        // Request microphone permission before starting the realtime session.
        // Without permission the audio engine cannot capture input, and the
        // server-side realtime session may hang waiting for audio frames.
        let micGranted = await AVAudioApplication.requestRecordPermission()
        LLog.info("voice", "mic permission resolved", fields: ["granted": micGranted])
        guard micGranted else {
            throw NSError(
                domain: "AgentBuddy",
                code: 3311,
                userInfo: [NSLocalizedDescriptionKey: "Microphone access is required for voice mode"]
            )
        }

        let appModel = requireAppModel()
        syncHandoffServers()
        await cleanupKnownRealtimeVoiceSessions(beforeStartingOn: key)

        var resolvedKey = key
        var thread = appModel.snapshot?.threadSnapshot(for: key)
        if thread == nil {
            if let loadedKey = await appModel.ensureThreadLoaded(key: key) {
                resolvedKey = loadedKey
                thread = appModel.threadSnapshot(for: loadedKey)
            }
        }

        guard let thread else {
            LLog.error("voice", "thread snapshot unresolved", fields: [
                "server_id": key.serverId,
                "thread_id": key.threadId,
            ])
            throw NSError(
                domain: "AgentBuddy",
                code: 3302,
                userInfo: [NSLocalizedDescriptionKey: "Voice mode requires an active server thread"]
            )
        }
        LLog.info("voice", "thread resolved", fields: [
            "server_id": resolvedKey.serverId,
            "thread_id": resolvedKey.threadId,
        ])

        let runtimeSessionId = "litter-voice-\(UUID().uuidString.lowercased())"
        let explicitTitle = thread.info.title?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let threadTitle = explicitTitle.isEmpty ? thread.resolvedPreview : explicitTitle
        let resolvedModel = thread.resolvedModel
        activeVoiceSession = VoiceSessionState.initial(
            threadKey: resolvedKey,
            threadTitle: threadTitle,
            model: resolvedModel.isEmpty ? (model ?? "Codex") : resolvedModel
        )
        syncVoiceCallActivity()
        LLog.info("voice", "activeVoiceSession set to .connecting")

        let session = RealtimeWebRtcSession()
        session.onRouteChanged = { [weak self] route in
            self?.handleRealtimeRouteChanged(route)
        }
        self.realtimeSession = session
        // Every new session starts unmuted. The watch can re-mute via
        // `voice.toggleMute` once it observes the live `WatchVoiceState`.
        isMicrophoneMuted = false

        // Detached background launch so the caller can push UI immediately
        // and the user sees the .connecting state while WebRTC handshakes.
        Task { @MainActor [weak self] in
            await self?.runRealtimeVoiceLaunch(
                resolvedKey: resolvedKey,
                runtimeSessionId: runtimeSessionId,
                session: session
            )
        }

        return resolvedKey
    }

    private func runRealtimeVoiceLaunch(
        resolvedKey: ThreadKey,
        runtimeSessionId: String,
        session: RealtimeWebRtcSession
    ) async {
        // If the active session was torn down before the launch task started,
        // the caller already stopped/replaced things — bail.
        guard activeVoiceSession?.threadKey == resolvedKey,
              realtimeSession === session else {
            LLog.warn("voice", "runRealtimeVoiceLaunch aborted — session changed before launch")
            session.stop()
            return
        }

        let appModel = requireAppModel()

        let offerSdp: String
        do {
            LLog.info("voice", "calling RealtimeWebRtcSession.start()")
            offerSdp = try await session.start()
            LLog.info("voice", "RealtimeWebRtcSession.start() returned", fields: ["sdp_len": offerSdp.count])
        } catch {
            LLog.error("voice", "RealtimeWebRtcSession.start() failed", error: error)
            session.stop()
            if realtimeSession === session { realtimeSession = nil }
            failVoiceSession(error.localizedDescription)
            return
        }

        // Re-check between awaits; the user may have hung up.
        guard activeVoiceSession?.threadKey == resolvedKey,
              realtimeSession === session else {
            LLog.warn("voice", "runRealtimeVoiceLaunch aborted — session changed after WebRTC start")
            session.stop()
            return
        }

        do {
            let dynamicTools = try CrossServerTools.buildDynamicToolSpecs().map { try $0.rpcSpec() }
            LLog.info("voice", "calling client.startRealtimeSession (webrtc transport)")
            _ = try await appModel.client.startRealtimeSession(
                serverId: resolvedKey.serverId,
                params: AppStartRealtimeSessionRequest(
                    threadId: resolvedKey.threadId,
                    prompt: realtimePrompt(),
                    sessionId: runtimeSessionId,
                    transport: .webrtc(sdp: offerSdp),
                    clientControlledHandoff: true,
                    dynamicTools: dynamicTools
                )
            )
            LLog.info("voice", "client.startRealtimeSession returned")
        } catch {
            LLog.error("voice", "client.startRealtimeSession failed", error: error)
            session.stop()
            if realtimeSession === session { realtimeSession = nil }
            _ = try? await appModel.client.stopRealtimeSession(
                serverId: resolvedKey.serverId,
                params: AppStopRealtimeSessionRequest(threadId: resolvedKey.threadId)
            )
            failVoiceSession(error.localizedDescription)
        }
    }

    private func realtimePrompt() -> String {
        let remoteServers = appModel?.snapshot?.servers
            .filter { !$0.isLocal && $0.isConnected }
            .map { (name: $0.displayName, hostname: $0.host) } ?? []
        return VoiceSessionControl.buildPrompt(remoteServers: remoteServers)
    }

    private var knownRealtimeVoiceThreadKeys: [ThreadKey] {
        var keys = Set<ThreadKey>()
        if let activeKey = activeVoiceSession?.threadKey, !activeKey.threadId.isEmpty {
            keys.insert(activeKey)
        }
        if let stopKey = voiceStopRequestedThreadKey, !stopKey.threadId.isEmpty {
            keys.insert(stopKey)
        }
        if let persistedLocalThreadId = persistedLocalVoiceThreadId(), !persistedLocalThreadId.isEmpty {
            keys.insert(ThreadKey(serverId: Self.localServerID, threadId: persistedLocalThreadId))
        }
        return Array(keys)
    }

    private func cleanupKnownRealtimeVoiceSessions(beforeStartingOn key: ThreadKey? = nil) async {
        for candidate in knownRealtimeVoiceThreadKeys where candidate != key {
            guard isServerConnected(candidate.serverId) else { continue }
            _ = try? await requireAppModel().client.stopRealtimeSession(
                serverId: candidate.serverId,
                params: AppStopRealtimeSessionRequest(threadId: candidate.threadId)
            )
        }
    }

    func failVoiceSession(_ message: String) {
        realtimeSession?.stop()
        realtimeSession = nil
        voiceInputDecayToken = nil
        voiceOutputDecayToken = nil

        guard var session = activeVoiceSession else {
            endVoiceSessionImmediately()
            return
        }

        session.phase = .error
        session.lastError = message
        session.isListening = false
        session.isSpeaking = false
        session.inputLevel = 0
        session.outputLevel = 0
        session.transcriptLiveMessageID = nil
        activeVoiceSession = session
        syncVoiceCallActivity()
    }

    func endVoiceSessionImmediately() {
        let activeKey = activeVoiceSession?.threadKey
        voiceInputDecayToken = nil
        voiceOutputDecayToken = nil
        voiceStopRequestedThreadKey = nil
        realtimeSession?.stop()
        realtimeSession = nil
        isMicrophoneMuted = false
        _ = activeKey
        activeVoiceSession = nil
        endVoiceCallActivity()
    }

    func updateVoiceSessionForPendingStop(_ key: ThreadKey) {
        guard var session = activeVoiceSession, session.threadKey == key else { return }
        session.isListening = false
        session.isSpeaking = false
        session.inputLevel = 0
        session.outputLevel = 0
        session.transcriptSpeaker = "System"
        session.transcriptText = "Hanging up..."
        session.lastError = nil
        activeVoiceSession = session
        syncVoiceCallActivity()
    }
}
