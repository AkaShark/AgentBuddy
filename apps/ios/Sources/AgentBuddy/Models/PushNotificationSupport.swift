import CryptoKit
import Foundation
import UserNotifications

/// Platform glue for host-reported turn completion notifications
/// (`docs/superpowers/specs/2026-09-24-host-push-notifications-design.md`
/// §8.2). Subscription policy lives in Rust (`AppClient.setPushRegistration`
/// / `turnPushState`); these are the pure decisions the app delegate and the
/// lifecycle controller share, kept free of UIKit state so they are testable.
enum PushNotificationSupport {
    /// AgentBuddy-owned Cloudflare Worker, shared with Android. Rust only uses
    /// it for the best-effort direct revoke when a host is unreachable.
    static let workerBaseURL = "https://agentbuddy-push-proxy.aaksharker.workers.dev"

    /// Payload keys of host-reported (APNs, §7.3) and local completion
    /// notifications.
    static let serverIdKey = "agentbuddy.notification.serverId"
    static let threadIdKey = "agentbuddy.notification.threadId"
    static let turnIdKey = "agentbuddy.notification.turnId"

    static let completionCategoryIdentifier = "agentbuddy.task.complete"

    // MARK: - APNs environment

    /// APNs environment of this build's device token. An embedded provisioning
    /// profile decides (development → sandbox, ad hoc → production); without
    /// one the build came from the App Store or TestFlight, which always use
    /// production.
    static func apnsEnvironment(embeddedProvisioningProfile profile: Data?) -> AppApnsEnvironment {
        guard let profile else { return .production }
        return apsEnvironmentEntitlement(inProvisioningProfile: profile) == "development"
            ? .sandbox
            : .production
    }

    static func currentApnsEnvironment(bundle: Bundle = .main) -> AppApnsEnvironment {
        let profile = bundle.url(forResource: "embedded", withExtension: "mobileprovision")
            .flatMap { try? Data(contentsOf: $0) }
        return apnsEnvironment(embeddedProvisioningProfile: profile)
    }

    /// `Entitlements.aps-environment` of a provisioning profile. The profile is
    /// a CMS-signed blob whose payload is a plain XML plist, so the plist is
    /// sliced out instead of decoding the CMS envelope.
    static func apsEnvironmentEntitlement(inProvisioningProfile profile: Data) -> String? {
        guard let start = profile.range(of: Data("<?xml".utf8))?.lowerBound
                ?? profile.range(of: Data("<plist".utf8))?.lowerBound,
              let end = profile.range(of: Data("</plist>".utf8), in: start..<profile.endIndex)?.upperBound,
              let plist = try? PropertyListSerialization.propertyList(
                  from: profile.subdata(in: start..<end),
                  format: nil
              ) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any] else {
            return nil
        }
        return entitlements["aps-environment"] as? String
    }

    // MARK: - Registration

    /// Host pushes are only worth subscribing while the user lets them show.
    static func allowsCompletionPush(_ status: UNAuthorizationStatus) -> Bool {
        switch status {
        case .authorized, .provisional:
            return true
        default:
            return false
        }
    }

    /// Registration handed to Rust; `nil` (no token or no permission) makes
    /// Rust revoke this device's subscriptions.
    static func registration(
        token: Data?,
        authorizationStatus: UNAuthorizationStatus,
        environment: AppApnsEnvironment
    ) -> AppPushRegistration? {
        guard let token, !token.isEmpty, allowsCompletionPush(authorizationStatus) else { return nil }
        return AppPushRegistration(
            platform: .ios,
            token: tokenHex(token),
            apnsEnvironment: environment,
            workerBaseUrl: workerBaseURL
        )
    }

    static func tokenHex(_ token: Data) -> String {
        token.map { String(format: "%02x", $0) }.joined()
    }

    /// Short, non-reversible token id for logs; the token itself is never logged.
    static func tokenFingerprint(_ token: Data) -> String {
        SHA256.hash(data: token).prefix(4).map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Routing

    static func threadKey(from userInfo: [AnyHashable: Any]) -> ThreadKey? {
        guard let serverId = trimmedNonEmpty(userInfo[serverIdKey] as? String),
              let threadId = trimmedNonEmpty(userInfo[threadIdKey] as? String) else {
            return nil
        }
        return ThreadKey(serverId: serverId, threadId: threadId)
    }

    /// Foreground presentation: stay quiet for the conversation the user is
    /// reading, show everything else.
    static func presentationOptions(
        for key: ThreadKey?,
        visibleThread: ThreadKey?,
        isAppActive: Bool
    ) -> UNNotificationPresentationOptions {
        if isAppActive, let key, key == visibleThread {
            return []
        }
        return [.banner, .list, .sound]
    }

    // MARK: - Local completion fallback

    /// The local completion notification is only a fallback: skip it when the
    /// host will report the turn (subscribed, or the subscription is in
    /// flight — a host that learns of an already-finished turn reports it
    /// right away).
    static func shouldPostLocalCompletion(for pushState: AppTurnPushState) -> Bool {
        switch pushState {
        case .subscribed, .pending:
            return false
        case .notApplicable, .unsupported, .failed:
            return true
        }
    }

    // MARK: - Unsupported host hint

    /// UserDefaults key recording that the "update the desktop app" hint was
    /// dismissed for `serverId`.
    static func unsupportedHostHintDismissedKey(serverId: String) -> String {
        "agentbuddy.hostPushHint.dismissed.\(serverId)"
    }

    /// A host without `push.v1` gets an explicit hint instead of a silent
    /// keep-alive fallback (§9), but only while the user allows notifications
    /// and has not dismissed it for that server.
    static func shouldShowUnsupportedHostHint(
        support: AppHostPushSupport,
        authorizationStatus: UNAuthorizationStatus,
        dismissed: Bool
    ) -> Bool {
        support == .unsupportedHost && allowsCompletionPush(authorizationStatus) && !dismissed
    }

    /// One notification per turn, so a repeated post replaces instead of stacking.
    static func localCompletionIdentifier(for key: ThreadKey, turnId: String?) -> String {
        let base = "agentbuddy.turn.\(key.serverId).\(key.threadId)"
        guard let turnId = trimmedNonEmpty(turnId) else { return base }
        return "\(base).\(turnId)"
    }

    private static func trimmedNonEmpty(_ value: String?) -> String? {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }
}
