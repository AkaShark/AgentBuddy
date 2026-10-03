import XCTest
@testable import AgentBuddy

/// Opt-in physical-device regression against tools/qa/mobile-history-fixture.py.
/// Supply DeviceRegression.local.json in this test bundle: {"url":"ws://<Mac>:18765"}.
final class MobileHistoryDeviceTests: XCTestCase {
    private func endpoint() throws -> String {
        guard let file = Bundle(for: Self.self).url(forResource: "DeviceRegression.local", withExtension: "json") else {
            throw XCTSkip("Start the synthetic history fixture and provide DeviceRegression.local.json")
        }
        let config = try JSONDecoder().decode(Configuration.self, from: Data(contentsOf: file))
        return config.url
    }

    private struct Configuration: Decodable { let url: String }

    private func users(_ snapshot: AppThreadSnapshot?) -> [String] {
        snapshot?.hydratedConversationItems.compactMap {
            if case .user(let value) = $0.content { return value.text }
            return nil
        } ?? []
    }

    func testSleepPaginationForkEditAndFailedRevert() async throws {
        let url = try endpoint()
        let bridge = ServerBridge()
        let client = AppClient()
        let store = AppStore()
        let serverId = "qa-history-ios-\(UUID().uuidString)"
        _ = try await bridge.connectRemoteUrlServer(serverId: serverId, displayName: "History QA", websocketUrl: url)
        defer { bridge.disconnectServer(serverId: serverId) }
        let key = try await client.readThread(serverId: serverId, params: .init(threadId: "history", includeTurns: false))
        _ = try await store.loadThreadTurnsPage(key: key, cursor: nil, limit: 1)
        var snapshot = try await store.threadSnapshot(key: key)
        XCTAssertEqual(users(snapshot), ["prompt-3"])
        while let cursor = snapshot?.olderTurnsCursor {
            _ = try await store.loadThreadTurnsPage(key: key, cursor: cursor, limit: 1)
            snapshot = try await store.threadSnapshot(key: key)
        }
        XCTAssertEqual(users(snapshot), ["prompt-1", "prompt-2", "prompt-3"])
        let items = try XCTUnwrap(snapshot).hydratedConversationItems
        XCTAssertEqual(Set(items.map(\.id)).count, items.count)
        XCTAssertEqual(items.filter {
            if case .note(let value) = $0.content { return value.body.contains("2000 ms") }
            return false
        }.count, 3)
        let fork = try await store.forkThreadFromMessage(key: key, selectedTurnIndex: 1,
            params: .init(model: nil, cwd: nil, approvalPolicy: nil, sandbox: nil,
                          developerInstructions: nil, persistExtendedHistory: true))
        let forkSnapshot = try await store.threadSnapshot(key: fork)
        XCTAssertEqual(users(forkSnapshot), ["prompt-1", "prompt-2"])
        let original = try await store.threadSnapshot(key: key)
        XCTAssertEqual(users(original), ["prompt-1", "prompt-2", "prompt-3"])
        let draft = try await store.editMessage(key: key, selectedTurnIndex: 1)
        XCTAssertEqual(draft, "prompt-2")
        let edited = try await store.threadSnapshot(key: key)
        XCTAssertEqual(users(edited), ["prompt-1"])
        let firstDraft = try await store.editMessage(key: key, selectedTurnIndex: 0)
        XCTAssertEqual(firstDraft, "prompt-1")
        let empty = try await store.threadSnapshot(key: key)
        XCTAssertTrue(users(empty).isEmpty)
        XCTAssertNil(empty?.olderTurnsCursor)

        let failureKey = try await client.readThread(serverId: serverId,
            params: .init(threadId: "history-error", includeTurns: true))
        do {
            _ = try await store.editMessage(key: failureKey, selectedTurnIndex: 0)
            XCTFail("Injected revert error must reach the caller")
        } catch {
            XCTAssertTrue(String(describing: error).contains("Injected failure"))
        }
        let unchanged = try await store.threadSnapshot(key: failureKey)
        XCTAssertEqual(users(unchanged), ["prompt-1", "prompt-2", "prompt-3"])
    }
}
