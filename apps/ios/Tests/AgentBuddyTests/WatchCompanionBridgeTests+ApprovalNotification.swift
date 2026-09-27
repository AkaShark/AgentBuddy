import XCTest
import UserNotifications
@testable import AgentBuddy

extension WatchCompanionBridgeTests {

    // MARK: - Approval notification builder

    func testApprovalNotificationRequestSetsCategoryThreadAndUserInfo() {
        let approval = PendingApproval(
            id: "req-42",
            serverId: "macbook",
            kind: .command,
            threadId: "t1",
            turnId: nil,
            itemId: nil,
            command: "git push origin main",
            path: nil,
            grantRoot: nil,
            cwd: nil,
            reason: nil
        )

        let request = WatchCompanionBridge.makeApprovalNotificationRequest(
            approval: approval,
            serverName: "MacBook Pro",
            threadTitle: "fix auth"
        )

        XCTAssertEqual(request.content.categoryIdentifier, WatchApprovalNotification.categoryIdentifier)
        XCTAssertEqual(request.content.threadIdentifier, "macbook")
        XCTAssertEqual(
            request.content.userInfo[WatchApprovalNotification.requestIdKey] as? String,
            "req-42"
        )
        XCTAssertEqual(
            request.content.userInfo[WatchApprovalNotification.serverIdKey] as? String,
            "macbook"
        )
        XCTAssertEqual(
            request.content.userInfo[WatchApprovalNotification.threadIdKey] as? String,
            "t1"
        )
        XCTAssertEqual(request.content.subtitle, "MacBook Pro")
        // Body should weave in the thread title and the command detail.
        XCTAssertTrue(request.content.body.contains("fix auth"))
        XCTAssertTrue(request.content.body.contains("git push origin main"))
        // Identifier is stable so a re-issue replaces the existing banner.
        XCTAssertEqual(request.identifier, "agentbuddy.approval.req-42")
    }

    func testApprovalNotificationRequestOmitsThreadIdWhenAbsent() {
        let approval = PendingApproval(
            id: "req-99",
            serverId: "studio",
            kind: .permissions,
            threadId: nil,
            turnId: nil,
            itemId: nil,
            command: nil,
            path: nil,
            grantRoot: nil,
            cwd: nil,
            reason: "Allow workspace write"
        )

        let request = WatchCompanionBridge.makeApprovalNotificationRequest(
            approval: approval,
            serverName: "studio",
            threadTitle: nil
        )

        XCTAssertNil(request.content.userInfo[WatchApprovalNotification.threadIdKey])
        XCTAssertEqual(request.content.threadIdentifier, "studio")
        XCTAssertEqual(request.content.body, "Allow workspace write")
    }
}
