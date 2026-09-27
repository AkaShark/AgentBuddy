import AuthenticationServices
import Foundation
import UIKit

@MainActor
final class ChatGPTOAuthSessionRunner: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = ChatGPTOAuthSessionRunner()

    private var activeSession: ASWebAuthenticationSession?
    private var activeCallbackServer: ChatGPTOAuthLoopbackServer?
    private var callbackTask: Task<Void, Never>?
    private var continuation: CheckedContinuation<ChatGPTOAuthSessionResult, Error>?
    private var didResolve = false

    func authenticate(
        timeout: Duration,
        label: String = "login",
        prefersEphemeralWebBrowserSession: Bool = true,
        buildAuthorizeURL: @escaping (String) throws -> URL
    ) async throws -> ChatGPTOAuthSessionResult {
        try await cancelActiveAttempt()

        let callbackServer = try ChatGPTOAuthLoopbackServer(
            bindHost: ChatGPTOAuth.callbackBindHost,
            publicHost: ChatGPTOAuth.callbackPublicHost,
            port: ChatGPTOAuth.callbackPort,
            path: ChatGPTOAuth.callbackPath,
            timeout: timeout
        )
        LLog.info("auth", "ChatGPT auth callback listener starting", fields: [
            "label": label,
            "bindHost": ChatGPTOAuth.callbackBindHost,
            "publicHost": ChatGPTOAuth.callbackPublicHost,
            "port": ChatGPTOAuth.callbackPort,
            "path": ChatGPTOAuth.callbackPath
        ])
        let redirectURI = try await callbackServer.start()
        let authorizeURL = try buildAuthorizeURL(redirectURI)
        LLog.info("auth", "ChatGPT auth session prepared", fields: [
            "label": label,
            "redirectURI": redirectURI,
            "authorize": sanitizedAuthorizeURL(authorizeURL),
            "ephemeral": prefersEphemeralWebBrowserSession
        ])

        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            self.didResolve = false
            self.activeCallbackServer = callbackServer
            self.activeRedirectURI = redirectURI
            self.activeLabel = label
            self.callbackTask = Task { [weak self] in
                do {
                    let callbackURL = try await callbackServer.waitForCallback()
                    await MainActor.run {
                        self?.finishSuccess(
                            callbackURL: callbackURL,
                            redirectURI: redirectURI
                        )
                    }
                } catch {
                    await MainActor.run {
                        self?.finishFailure(error)
                    }
                }
            }

            let session = ASWebAuthenticationSession(
                url: authorizeURL,
                callbackURLScheme: nil
            ) { [weak self] callbackURL, error in
                Task { @MainActor in
                    self?.handleSessionCompletion(callbackURL: callbackURL, error: error)
                }
            }
            // Start from a clean web auth session to avoid stale provider state
            // carrying over after failed attempts and tripping invalid_state.
            session.prefersEphemeralWebBrowserSession = prefersEphemeralWebBrowserSession
            session.presentationContextProvider = self
            activeSession = session
            guard session.start() else {
                finishFailure(ChatGPTOAuthError.unableToStartSession)
                return
            }
            LLog.info("auth", "ChatGPT auth session started", fields: [
                "label": label,
                "ephemeral": prefersEphemeralWebBrowserSession
            ])
        }
    }

    private var activeRedirectURI: String?
    private var activeLabel: String?

    private func handleSessionCompletion(callbackURL: URL?, error: Error?) {
        let label = activeLabel ?? "unknown"
        if let callbackURL {
            LLog.info("auth", "ChatGPT auth session returned callback URL", fields: [
                "label": label,
                "scheme": callbackURL.scheme ?? "",
                "host": callbackURL.host ?? "",
                "path": callbackURL.path
            ])
            finishSuccess(
                callbackURL: callbackURL,
                redirectURI: activeRedirectURI ?? "http://\(ChatGPTOAuth.callbackPublicHost):\(ChatGPTOAuth.callbackPort)\(ChatGPTOAuth.callbackPath)"
            )
            return
        }

        activeSession = nil
        guard !didResolve else { return }

        if let authError = error as? ASWebAuthenticationSessionError,
           authError.code == .canceledLogin {
            LLog.warn("auth", "ChatGPT auth session cancelled", fields: [
                "label": label
            ])
            finishFailure(ChatGPTOAuthError.cancelled)
            return
        }
        if let error {
            LLog.warn("auth", "ChatGPT auth session failed", fields: [
                "label": label,
                "error": error.localizedDescription
            ])
            finishFailure(error)
            return
        }
        LLog.warn("auth", "ChatGPT auth session completed without callback", fields: [
            "label": label
        ])
        finishFailure(ChatGPTOAuthError.cancelled)
    }

    private func finishSuccess(callbackURL: URL, redirectURI: String) {
        guard !didResolve else { return }
        didResolve = true
        let continuation = self.continuation
        resetActiveState(cancelSession: true)
        continuation?.resume(
            returning: ChatGPTOAuthSessionResult(
                callbackURL: callbackURL,
                redirectURI: redirectURI
            )
        )
    }

    private func finishFailure(_ error: Error) {
        guard !didResolve else { return }
        didResolve = true
        let continuation = self.continuation
        resetActiveState(cancelSession: true)
        continuation?.resume(throwing: error)
    }

    private func cancelActiveAttempt() async throws {
        guard continuation != nil || activeSession != nil || activeCallbackServer != nil else { return }
        finishFailure(ChatGPTOAuthError.cancelled)
    }

    private func resetActiveState(cancelSession: Bool) {
        if cancelSession {
            activeSession?.cancel()
        }
        activeSession = nil
        callbackTask?.cancel()
        callbackTask = nil
        activeCallbackServer?.stop()
        activeCallbackServer = nil
        continuation = nil
        activeRedirectURI = nil
        activeLabel = nil
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        if let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap(\.windows)
            .first(where: \.isKeyWindow) {
            return window
        }
        return ASPresentationAnchor()
    }

    private func sanitizedAuthorizeURL(_ url: URL) -> String {
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return url.absoluteString
        }
        components.queryItems = components.queryItems?.map { item in
            switch item.name {
            case "code_challenge":
                return URLQueryItem(name: item.name, value: "<redacted>")
            default:
                return item
            }
        }
        return components.url?.absoluteString ?? url.absoluteString
    }
}

struct ChatGPTOAuthSessionResult {
    let callbackURL: URL
    let redirectURI: String
}
