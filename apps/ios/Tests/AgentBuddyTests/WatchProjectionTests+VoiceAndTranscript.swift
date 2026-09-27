import XCTest
@testable import AgentBuddy

extension WatchProjectionTests {

    // MARK: - 6. transcript(for:)

    func testTranscriptCapsAtFourTurnsAndPrefixesCommands() {
        var items: [HydratedConversationItem] = []
        for i in 0..<3 {
            items.append(makeUserItem(id: "u\(i)", text: "user-\(i)"))
            items.append(makeAssistantItem(id: "a\(i)", text: "assistant-\(i)"))
        }
        items.append(makeCommandItem(id: "cmd", command: "ls -la", status: .completed))

        let thread = makeThread(serverId: "srv", threadId: "t", items: items)
        let result = WatchProjection.transcript(for: thread)

        XCTAssertEqual(result.count, 4)
        // The full mapped sequence is:
        //   user-0, assistant-0, user-1, assistant-1, user-2, assistant-2, $ ls -la
        // After Array(turns.suffix(4)) we keep the last four:
        XCTAssertEqual(
            result.map(\.text),
            ["assistant-1", "user-2", "assistant-2", "$ ls -la"]
        )
        XCTAssertEqual(result.map(\.role), [.assistant, .user, .assistant, .system])
    }

    func testTranscriptFiltersEmptyUserAndAssistantText() {
        let items: [HydratedConversationItem] = [
            makeUserItem(id: "u-empty", text: ""),
            makeUserItem(id: "u-real",  text: "hello"),
            makeAssistantItem(id: "a-empty", text: ""),
            makeAssistantItem(id: "a-real",  text: "hi")
        ]
        let thread = makeThread(serverId: "srv", threadId: "t", items: items)
        let result = WatchProjection.transcript(for: thread)

        XCTAssertEqual(result.map(\.text), ["hello", "hi"])
        XCTAssertEqual(result.map(\.role), [.user, .assistant])
    }

    func testTranscriptCommandWithBlankCommandFallsBackToRanCommand() {
        let thread = makeThread(
            serverId: "srv",
            threadId: "t",
            items: [makeCommandItem(id: "c", command: "   ", status: .completed)]
        )
        let result = WatchProjection.transcript(for: thread)
        XCTAssertEqual(result.first?.text, "ran command")
        XCTAssertEqual(result.first?.role, .system)
    }

    // MARK: - 8. voice(from:)

    func testVoiceReturnsNilWhenSnapshotIsNil() {
        XCTAssertNil(WatchProjection.voice(from: nil))
    }

    func testVoiceReturnsNilWhenSessionIsCompletelyEmpty() {
        let snapshot = makeSnapshot(voiceSession: AppVoiceSessionSnapshot(
            activeThread: nil,
            sessionId: nil,
            phase: nil,
            lastError: nil,
            transcriptEntries: [],
            handoffThreadKey: nil
        ))
        XCTAssertNil(WatchProjection.voice(from: snapshot))
    }

    func testVoiceMapsPhasesAndExposesActiveThreadAndAudio() {
        let phases: [(AppVoiceSessionPhase?, WatchVoiceState.Mode)] = [
            (.listening,  .listening),
            (.speaking,   .speaking),
            (.thinking,   .thinking),
            (.handoff,    .thinking),
            (.error,      .error),
            (.connecting, .idle)
        ]

        for (phase, expected) in phases {
            let snapshot = makeSnapshot(voiceSession: AppVoiceSessionSnapshot(
                activeThread: ThreadKey(serverId: "srv", threadId: "th"),
                sessionId: "sess",
                phase: phase,
                lastError: nil,
                transcriptEntries: [],
                handoffThreadKey: nil
            ))
            let voice = WatchProjection.voice(from: snapshot, audioLevel: 0.6, isMuted: true)
            XCTAssertEqual(voice?.mode, expected, "phase=\(String(describing: phase))")
            XCTAssertEqual(voice?.serverId, "srv")
            XCTAssertEqual(voice?.threadId, "th")
            XCTAssertEqual(voice?.audioLevel, 0.6)
            XCTAssertEqual(voice?.isMuted, true)
        }
    }

    func testVoiceClampsAudioLevelToZeroOneRange() {
        let snapshot = makeSnapshot(voiceSession: AppVoiceSessionSnapshot(
            activeThread: ThreadKey(serverId: "srv", threadId: "th"),
            sessionId: nil,
            phase: .listening,
            lastError: nil,
            transcriptEntries: [],
            handoffThreadKey: nil
        ))

        XCTAssertEqual(WatchProjection.voice(from: snapshot, audioLevel: -2)?.audioLevel, 0)
        XCTAssertEqual(WatchProjection.voice(from: snapshot, audioLevel: 5)?.audioLevel, 1)
        XCTAssertEqual(WatchProjection.voice(from: snapshot, audioLevel: 0.5)?.audioLevel, 0.5)
    }

    func testVoiceTranscriptKeepsLastFourTurnsAndMapsSpeakers() {
        let entries: [AppVoiceTranscriptEntry] = [
            AppVoiceTranscriptEntry(itemId: "1", speaker: .user,      text: "first"),
            AppVoiceTranscriptEntry(itemId: "2", speaker: .assistant, text: "second"),
            AppVoiceTranscriptEntry(itemId: "3", speaker: .user,      text: "third"),
            AppVoiceTranscriptEntry(itemId: "4", speaker: .assistant, text: "fourth"),
            AppVoiceTranscriptEntry(itemId: "5", speaker: .user,      text: "fifth"),
            AppVoiceTranscriptEntry(itemId: "6", speaker: .assistant, text: "sixth")
        ]
        let snapshot = makeSnapshot(voiceSession: AppVoiceSessionSnapshot(
            activeThread: ThreadKey(serverId: "srv", threadId: "th"),
            sessionId: nil,
            phase: .speaking,
            lastError: nil,
            transcriptEntries: entries,
            handoffThreadKey: nil
        ))

        let voice = WatchProjection.voice(from: snapshot)
        XCTAssertEqual(voice?.recentTurns.count, 4)
        XCTAssertEqual(voice?.recentTurns.map(\.text), ["third", "fourth", "fifth", "sixth"])
        XCTAssertEqual(voice?.recentTurns.map(\.role), [.user, .assistant, .user, .assistant])
    }

    func testVoiceProjectsSessionWithOnlyTranscriptAndNoActiveThread() {
        let snapshot = makeSnapshot(voiceSession: AppVoiceSessionSnapshot(
            activeThread: nil,
            sessionId: nil,
            phase: nil,
            lastError: nil,
            transcriptEntries: [
                AppVoiceTranscriptEntry(itemId: "1", speaker: .user, text: "hi")
            ],
            handoffThreadKey: nil
        ))

        let voice = WatchProjection.voice(from: snapshot)
        XCTAssertNotNil(voice)
        XCTAssertEqual(voice?.mode, .idle)
        XCTAssertNil(voice?.serverId)
        XCTAssertNil(voice?.threadId)
        XCTAssertEqual(voice?.recentTurns.first?.text, "hi")
    }
}
