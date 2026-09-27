import Foundation

extension VoiceRuntimeController {
    func scheduleSharedVoiceSessionSync(for key: ThreadKey?) {
        Task { @MainActor [weak self] in
            await self?.syncSharedVoiceSessionFromStore(for: key)
        }
    }

    func syncSharedVoiceSessionFromStore(for key: ThreadKey?) async {
        guard let appModel else { return }
        let expectedKey = key ?? activeVoiceSession?.threadKey
        await appModel.refreshSnapshot()
        guard var session = activeVoiceSession else { return }
        guard expectedKey == nil || session.threadKey == expectedKey else { return }

        let shared = appModel.snapshot?.voiceSession
        if shared?.activeThread == session.threadKey || shared?.phase == .error {
            applySharedVoiceSession(shared, to: &session)
            activeVoiceSession = session
            syncVoiceCallActivity()
            return
        }

        // Keep waiting while the local session is still optimistically
        // connecting. Once the shared store has shown a live/error session,
        // a later FullResync with no active voice means the session is over.
        if session.phase != .connecting {
            endVoiceSessionImmediately()
        }
    }
}

private extension VoiceRuntimeController {
    func applySharedVoiceSession(_ shared: AppVoiceSessionSnapshot?, to session: inout VoiceSessionState) {
        session.sessionId = shared?.sessionId
        session.phase = shared?.phase.map(voiceSessionPhase) ?? .connecting
        session.lastError = shared?.lastError
        session.handoffRemoteThreadKey = shared?.handoffThreadKey
        switch session.phase {
        case .listening:
            session.isListening = true
            session.isSpeaking = false
        case .speaking:
            session.isListening = false
            session.isSpeaking = true
        case .connecting, .thinking, .handoff, .error:
            session.isListening = false
            session.isSpeaking = false
        }

        let entries = (shared?.transcriptEntries ?? []).map {
            VoiceSessionTranscriptEntry(
                id: $0.itemId,
                speaker: voiceSpeakerLabel($0.speaker),
                text: $0.text,
                timestamp: existingTranscriptTimestamp(id: $0.itemId, in: session) ?? Date()
            )
        }
        session.transcriptHistory = entries.filter {
            !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }

        if let last = session.transcriptHistory.last {
            session.transcriptText = last.text
            session.transcriptSpeaker = last.speaker
            session.transcriptLiveMessageID = last.id
        } else {
            session.transcriptText = nil
            session.transcriptSpeaker = nil
            session.transcriptLiveMessageID = nil
        }
    }

    func voiceSessionPhase(_ phase: AppVoiceSessionPhase) -> VoiceSessionPhase {
        switch phase {
        case .connecting:
            return .connecting
        case .listening:
            return .listening
        case .speaking:
            return .speaking
        case .thinking:
            return .thinking
        case .handoff:
            return .handoff
        case .error:
            return .error
        }
    }

    func voiceSpeakerLabel(_ speaker: AppVoiceSpeaker) -> String {
        switch speaker {
        case .user:
            return "You"
        case .assistant:
            return "Codex"
        }
    }

    func existingTranscriptTimestamp(id: String, in session: VoiceSessionState) -> Date? {
        session.transcriptHistory.first(where: { $0.id == id })?.timestamp
    }
}
