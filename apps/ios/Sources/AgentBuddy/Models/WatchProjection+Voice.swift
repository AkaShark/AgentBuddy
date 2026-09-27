import Foundation

extension WatchProjection {
    /// Project the live realtime voice session into the watch wire format.
    /// Returns `nil` when there is no active voice session (no
    /// `activeThread` and no `phase`) so the watch can hide the voice screen.
    static func voice(
        from snapshot: AppSnapshotRecord?,
        audioLevel: Double = 0,
        isMuted: Bool = false
    ) -> WatchVoiceState? {
        guard let voice = snapshot?.voiceSession else { return nil }
        // No phase and no active thread means the slot is empty.
        if voice.phase == nil && voice.activeThread == nil && voice.transcriptEntries.isEmpty {
            return nil
        }

        let mode: WatchVoiceState.Mode
        switch voice.phase {
        case .listening:                 mode = .listening
        case .speaking:                  mode = .speaking
        case .thinking, .handoff:        mode = .thinking
        case .error:                     mode = .error
        case .connecting, .none:         mode = .idle
        }

        let recent = voice.transcriptEntries.suffix(4).map { entry in
            let role: WatchTranscriptTurn.Role
            switch entry.speaker {
            case .user:      role = .user
            case .assistant: role = .assistant
            }
            return WatchTranscriptTurn(role: role, text: compact(entry.text), faded: false)
        }

        return WatchVoiceState(
            mode: mode,
            serverId: voice.activeThread?.serverId,
            threadId: voice.activeThread?.threadId,
            recentTurns: Array(recent),
            audioLevel: max(0, min(1, audioLevel)),
            isMuted: isMuted
        )
    }

    static func transcript(for thread: AppThreadSnapshot) -> [WatchTranscriptTurn] {
        let items = thread.hydratedConversationItems
        var turns: [WatchTranscriptTurn] = []
        turns.reserveCapacity(6)

        for item in items.suffix(20) {
            switch item.content {
            case .user(let data) where !data.text.isEmpty:
                turns.append(WatchTranscriptTurn(role: .user, text: compact(data.text), faded: false))
            case .assistant(let data) where !data.text.isEmpty:
                turns.append(WatchTranscriptTurn(role: .assistant, text: compact(data.text), faded: false))
            case .commandExecution(let data):
                let trimmed = data.command.trimmingCharacters(in: .whitespacesAndNewlines)
                let summary = trimmed.isEmpty ? "ran command" : "$ " + compact(trimmed, max: 42)
                turns.append(WatchTranscriptTurn(role: .system, text: summary, faded: false))
            default:
                continue
            }
        }

        return Array(turns.suffix(4))
    }
}
