import UserNotifications
import XCTest
@testable import AgentBuddy

final class PushNotificationSupportTests: XCTestCase {
    // MARK: - APNs environment

    func testDevelopmentProfileUsesSandbox() {
        let profile = makeProvisioningProfile(apsEnvironment: "development")

        XCTAssertEqual(PushNotificationSupport.apsEnvironmentEntitlement(inProvisioningProfile: profile), "development")
        XCTAssertEqual(PushNotificationSupport.apnsEnvironment(embeddedProvisioningProfile: profile), .sandbox)
    }

    func testProductionProfileUsesProduction() {
        let profile = makeProvisioningProfile(apsEnvironment: "production")

        XCTAssertEqual(PushNotificationSupport.apsEnvironmentEntitlement(inProvisioningProfile: profile), "production")
        XCTAssertEqual(PushNotificationSupport.apnsEnvironment(embeddedProvisioningProfile: profile), .production)
    }

    func testMissingProfileUsesProduction() {
        // App Store and TestFlight builds carry no embedded.mobileprovision.
        XCTAssertEqual(PushNotificationSupport.apnsEnvironment(embeddedProvisioningProfile: nil), .production)
    }

    func testProfileWithoutPushEntitlementUsesProduction() {
        let profile = makeProvisioningProfile(apsEnvironment: nil)

        XCTAssertNil(PushNotificationSupport.apsEnvironmentEntitlement(inProvisioningProfile: profile))
        XCTAssertEqual(PushNotificationSupport.apnsEnvironment(embeddedProvisioningProfile: profile), .production)
    }

    func testUnreadableProfileUsesProduction() {
        let profile = Data([0x30, 0x82, 0x01, 0x00, 0xde, 0xad, 0xbe, 0xef])

        XCTAssertNil(PushNotificationSupport.apsEnvironmentEntitlement(inProvisioningProfile: profile))
        XCTAssertEqual(PushNotificationSupport.apnsEnvironment(embeddedProvisioningProfile: profile), .production)
    }

    // MARK: - Registration

    func testRegistrationRequiresTokenAndPermission() {
        let token = Data([0x0a, 0xff, 0x01])

        let authorized = PushNotificationSupport.registration(
            token: token,
            authorizationStatus: .authorized,
            environment: .sandbox
        )
        XCTAssertEqual(
            authorized,
            AppPushRegistration(
                platform: .ios,
                token: "0aff01",
                apnsEnvironment: .sandbox,
                workerBaseUrl: PushNotificationSupport.workerBaseURL
            )
        )
        XCTAssertNotNil(
            PushNotificationSupport.registration(token: token, authorizationStatus: .provisional, environment: .production)
        )
        XCTAssertNil(
            PushNotificationSupport.registration(token: token, authorizationStatus: .denied, environment: .production)
        )
        XCTAssertNil(
            PushNotificationSupport.registration(token: token, authorizationStatus: .notDetermined, environment: .production)
        )
        XCTAssertNil(
            PushNotificationSupport.registration(token: nil, authorizationStatus: .authorized, environment: .production)
        )
    }

    func testTokenFingerprintIsShortAndDoesNotLeakToken() {
        let token = Data(repeating: 0xa1, count: 32)
        let fingerprint = PushNotificationSupport.tokenFingerprint(token)

        XCTAssertEqual(fingerprint.count, 8)
        XCTAssertFalse(PushNotificationSupport.tokenHex(token).contains(fingerprint))
        XCTAssertEqual(fingerprint, PushNotificationSupport.tokenFingerprint(token))
    }

    // MARK: - Routing

    func testThreadKeyParsesHostPushPayload() {
        let key = PushNotificationSupport.threadKey(from: [
            "aps": ["alert": ["title": "t", "body": "b"]],
            PushNotificationSupport.serverIdKey: " alleycat:abc ",
            PushNotificationSupport.threadIdKey: "thread-1",
            PushNotificationSupport.turnIdKey: "turn-1"
        ])

        XCTAssertEqual(key, ThreadKey(serverId: "alleycat:abc", threadId: "thread-1"))
    }

    func testThreadKeyRejectsMissingOrBlankIds() {
        XCTAssertNil(PushNotificationSupport.threadKey(from: [:]))
        XCTAssertNil(PushNotificationSupport.threadKey(from: [PushNotificationSupport.serverIdKey: "srv"]))
        XCTAssertNil(PushNotificationSupport.threadKey(from: [
            PushNotificationSupport.serverIdKey: "srv",
            PushNotificationSupport.threadIdKey: "  "
        ]))
        XCTAssertNil(PushNotificationSupport.threadKey(from: [
            PushNotificationSupport.serverIdKey: "srv",
            PushNotificationSupport.threadIdKey: 42
        ]))
    }

    func testForegroundNotificationForVisibleThreadIsSuppressed() {
        let key = ThreadKey(serverId: "alleycat:abc", threadId: "thread-1")

        XCTAssertEqual(
            PushNotificationSupport.presentationOptions(for: key, visibleThread: key, isAppActive: true),
            []
        )
    }

    func testOtherNotificationsArePresented() {
        let key = ThreadKey(serverId: "alleycat:abc", threadId: "thread-1")
        let otherThread = ThreadKey(serverId: "alleycat:abc", threadId: "thread-2")
        let sameThreadOtherHost = ThreadKey(serverId: "alleycat:def", threadId: "thread-1")
        let presented: UNNotificationPresentationOptions = [.banner, .list, .sound]

        XCTAssertEqual(
            PushNotificationSupport.presentationOptions(for: key, visibleThread: otherThread, isAppActive: true),
            presented
        )
        XCTAssertEqual(
            PushNotificationSupport.presentationOptions(for: key, visibleThread: sameThreadOtherHost, isAppActive: true),
            presented
        )
        XCTAssertEqual(
            PushNotificationSupport.presentationOptions(for: key, visibleThread: nil, isAppActive: true),
            presented
        )
        XCTAssertEqual(
            PushNotificationSupport.presentationOptions(for: key, visibleThread: key, isAppActive: false),
            presented
        )
        XCTAssertEqual(
            PushNotificationSupport.presentationOptions(for: nil, visibleThread: nil, isAppActive: true),
            presented
        )
    }

    // MARK: - Local completion fallback

    func testLocalCompletionOnlyWhenHostWillNotReport() {
        XCTAssertFalse(PushNotificationSupport.shouldPostLocalCompletion(for: .subscribed))
        XCTAssertFalse(PushNotificationSupport.shouldPostLocalCompletion(for: .pending))
        XCTAssertTrue(PushNotificationSupport.shouldPostLocalCompletion(for: .failed))
        XCTAssertTrue(PushNotificationSupport.shouldPostLocalCompletion(for: .unsupported))
        XCTAssertTrue(PushNotificationSupport.shouldPostLocalCompletion(for: .notApplicable))
    }

    func testLocalCompletionIdentifierIsPerTurn() {
        let key = ThreadKey(serverId: "alleycat:abc", threadId: "thread-1")

        XCTAssertEqual(
            PushNotificationSupport.localCompletionIdentifier(for: key, turnId: "turn-1"),
            "agentbuddy.turn.alleycat:abc.thread-1.turn-1"
        )
        XCTAssertEqual(
            PushNotificationSupport.localCompletionIdentifier(for: key, turnId: nil),
            "agentbuddy.turn.alleycat:abc.thread-1"
        )
    }

    // MARK: - Unsupported host hint

    func testUnsupportedHostHintOnlyForLegacyHostWithNotificationsAllowed() {
        XCTAssertTrue(PushNotificationSupport.shouldShowUnsupportedHostHint(
            support: .unsupportedHost, authorizationStatus: .authorized, dismissed: false
        ))
        XCTAssertTrue(PushNotificationSupport.shouldShowUnsupportedHostHint(
            support: .unsupportedHost, authorizationStatus: .provisional, dismissed: false
        ))
        XCTAssertFalse(PushNotificationSupport.shouldShowUnsupportedHostHint(
            support: .unsupportedHost, authorizationStatus: .authorized, dismissed: true
        ))
        XCTAssertFalse(PushNotificationSupport.shouldShowUnsupportedHostHint(
            support: .unsupportedHost, authorizationStatus: .denied, dismissed: false
        ))
        XCTAssertFalse(PushNotificationSupport.shouldShowUnsupportedHostHint(
            support: .unsupportedHost, authorizationStatus: .notDetermined, dismissed: false
        ))
        for support in [AppHostPushSupport.notApplicable, .unknown, .supported] {
            XCTAssertFalse(PushNotificationSupport.shouldShowUnsupportedHostHint(
                support: support, authorizationStatus: .authorized, dismissed: false
            ))
        }
    }

    func testUnsupportedHostHintDismissalIsPerServer() {
        XCTAssertNotEqual(
            PushNotificationSupport.unsupportedHostHintDismissedKey(serverId: "alleycat:aa"),
            PushNotificationSupport.unsupportedHostHintDismissedKey(serverId: "alleycat:bb")
        )
    }

    // MARK: - Helpers

    /// A provisioning profile shaped like the real thing: the XML plist
    /// wrapped in (fake) CMS signature bytes on both sides.
    private func makeProvisioningProfile(apsEnvironment: String?) -> Data {
        let apsEntry = apsEnvironment.map {
            "<key>aps-environment</key>\n<string>\($0)</string>\n"
        } ?? ""
        let plist = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
        <key>AppIDName</key>
        <string>AgentBuddy</string>
        <key>Entitlements</key>
        <dict>
        <key>application-identifier</key>
        <string>HNKUYWPBVC.com.akashark.agentbuddy</string>
        \(apsEntry)</dict>
        <key>Name</key>
        <string>iOS Team Provisioning Profile</string>
        </dict>
        </plist>
        """
        var data = Data([0x30, 0x80, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x07, 0x02, 0xa0, 0x80])
        data.append(Data(plist.utf8))
        data.append(Data([0x00, 0x00, 0xa0, 0x82, 0x0b, 0xff, 0x30, 0x82]))
        return data
    }
}
