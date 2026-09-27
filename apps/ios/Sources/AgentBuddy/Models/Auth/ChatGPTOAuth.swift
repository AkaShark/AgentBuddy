import AuthenticationServices
import CryptoKit
import Foundation
import Network
import Security
import UIKit

@MainActor
enum ChatGPTOAuth {
    static let authIssuer = "https://auth.openai.com"
    static let clientID = "app_EMoamEEZ73f0CkXaXp7hrann"
    static let callbackBindHost = "127.0.0.1"
    static let callbackPublicHost = "localhost"
    static let callbackPort: UInt16 = 1455
    static let callbackPath = "/auth/callback"
    static let callbackTimeout: Duration = .seconds(600)

    static func login() async throws -> ChatGPTOAuthTokenBundle {
        let state = UUID().uuidString
        let codeVerifier = generatePKCECodeVerifier()
        let codeChallenge = generatePKCECodeChallenge(codeVerifier)
        let authSession = try await ChatGPTOAuthSessionRunner.shared.authenticate(
            timeout: callbackTimeout
        ) { redirectURI in
            try buildAuthorizeURL(
                state: state,
                codeChallenge: codeChallenge,
                redirectURI: redirectURI
            )
        }
        let tokens = try await completeAuthorization(
            callbackURL: authSession.callbackURL,
            expectedState: state,
            codeVerifier: codeVerifier,
            redirectURI: authSession.redirectURI
        )
        try ChatGPTOAuthTokenStore.shared.save(tokens)
        return tokens
    }

    static func remoteControlEnrollmentStepUpToken() async throws -> String {
        let state = UUID().uuidString
        let codeVerifier = generatePKCECodeVerifier()
        let codeChallenge = generatePKCECodeChallenge(codeVerifier)
        LLog.info("slingshot", "remote-control step-up auth starting", fields: [
            "state": state
        ])
        let authSession = try await ChatGPTOAuthSessionRunner.shared.authenticate(
            timeout: callbackTimeout,
            label: "remote-control-step-up",
            prefersEphemeralWebBrowserSession: false
        ) { redirectURI in
            try buildRemoteControlStepUpAuthorizeURL(
                state: state,
                codeChallenge: codeChallenge,
                redirectURI: redirectURI
            )
        }
        LLog.info("slingshot", "remote-control step-up auth callback received", fields: [
            "redirectURI": authSession.redirectURI
        ])
        let token = try await completeAuthorizationForAccessToken(
            callbackURL: authSession.callbackURL,
            expectedState: state,
            codeVerifier: codeVerifier,
            redirectURI: authSession.redirectURI
        )
        LLog.info("slingshot", "remote-control step-up token received", fields: [
            "tokenLength": token.count
        ])
        return token
    }

    static func isRemoteControlAuthorizationRequired(_ error: Error) -> Bool {
        let message = "\(error.localizedDescription) \(String(describing: error))".lowercased()
        return message.contains("missing slingshot remote-control authorization token")
            || message.contains("missing slingshot client session token")
            || message.contains("remote-control authorization")
    }

    static func loadStoredOrRefreshedTokens() async throws -> ChatGPTOAuthTokenBundle {
        let stored = try ChatGPTOAuthTokenStore.shared.load()
        do {
            return try await refreshStoredTokens(
                previousAccountID: stored?.accountID,
                storedTokens: stored
            )
        } catch {
            if let stored {
                return stored
            }
            throw error
        }
    }

    static func refreshStoredTokens(
        previousAccountID: String?,
        storedTokens: ChatGPTOAuthTokenBundle? = nil
    ) async throws -> ChatGPTOAuthTokenBundle {
        guard let stored = try (storedTokens ?? ChatGPTOAuthTokenStore.shared.load()) else {
            throw ChatGPTOAuthError.missingStoredTokens
        }
        guard let refreshToken = stored.refreshToken, !refreshToken.isEmpty else {
            throw ChatGPTOAuthError.missingRefreshToken
        }

        let refreshed = try await exchangeRefreshToken(
            refreshToken,
            fallbackRefreshToken: refreshToken
        )
        if let previousAccountID, !previousAccountID.isEmpty,
           refreshed.accountID != previousAccountID,
           stored.accountID != previousAccountID {
            throw ChatGPTOAuthError.refreshAccountMismatch
        }
        try ChatGPTOAuthTokenStore.shared.save(refreshed)
        return refreshed
    }

    static func buildAuthorizeURL(
        state: String,
        codeChallenge: String,
        redirectURI: String
    ) throws -> URL {
        var components = URLComponents(string: "\(authIssuer)/oauth/authorize")
        components?.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(
                name: "scope",
                value: "openid profile email offline_access"
            ),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "id_token_add_organizations", value: "true"),
            URLQueryItem(name: "codex_cli_simplified_flow", value: "true")
        ]
        guard let url = components?.url else {
            throw ChatGPTOAuthError.invalidAuthorizeURL
        }
        return url
    }

    static func buildRemoteControlStepUpAuthorizeURL(
        state: String,
        codeChallenge: String,
        redirectURI: String
    ) throws -> URL {
        var components = URLComponents(string: "\(authIssuer)/oauth/authorize")
        components?.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "scope", value: "codex.remote_control.enroll"),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "originator", value: "Codex Desktop"),
            URLQueryItem(name: "reauth", value: "remote_control"),
            URLQueryItem(name: "max_age", value: "0"),
            URLQueryItem(name: "codex_cli_simplified_flow", value: "true")
        ]
        guard let url = components?.url else {
            throw ChatGPTOAuthError.invalidAuthorizeURL
        }
        return url
    }

    static func completeAuthorization(
        callbackURL: URL,
        expectedState: String,
        codeVerifier: String,
        redirectURI: String
    ) async throws -> ChatGPTOAuthTokenBundle {
        let components = try validateCallbackURL(callbackURL)

        let queryItems = Dictionary(
            uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") }
        )
        if let error = queryItems["error"], !error.isEmpty {
            let description = queryItems["error_description"]?.trimmingCharacters(in: .whitespacesAndNewlines)
            throw ChatGPTOAuthError.oauthError(description?.isEmpty == false ? description! : error)
        }
        guard queryItems["state"] == expectedState else {
            throw ChatGPTOAuthError.stateMismatch
        }
        guard let code = queryItems["code"], !code.isEmpty else {
            throw ChatGPTOAuthError.missingAuthorizationCode
        }

        return try await exchangeAuthorizationCode(
            code: code,
            codeVerifier: codeVerifier,
            redirectURI: redirectURI
        )
    }

    static func completeAuthorizationForAccessToken(
        callbackURL: URL,
        expectedState: String,
        codeVerifier: String,
        redirectURI: String
    ) async throws -> String {
        let components = try validateCallbackURL(callbackURL)

        let queryItems = Dictionary(
            uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") }
        )
        if let error = queryItems["error"], !error.isEmpty {
            let description = queryItems["error_description"]?.trimmingCharacters(in: .whitespacesAndNewlines)
            throw ChatGPTOAuthError.oauthError(description?.isEmpty == false ? description! : error)
        }
        guard queryItems["state"] == expectedState else {
            throw ChatGPTOAuthError.stateMismatch
        }
        guard let code = queryItems["code"], !code.isEmpty else {
            throw ChatGPTOAuthError.missingAuthorizationCode
        }

        return try await exchangeAuthorizationCodeForAccessToken(
            code: code,
            codeVerifier: codeVerifier,
            redirectURI: redirectURI
        )
    }

    static func validateCallbackURL(_ callbackURL: URL) throws -> URLComponents {
        guard let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false) else {
            throw ChatGPTOAuthError.invalidCallbackURL
        }
        let isHTTP = callbackURL.scheme == "http"
        let hostMatches =
            components.host == callbackBindHost ||
            components.host == callbackPublicHost
        let pathMatches = components.path == callbackPath
        guard isHTTP, hostMatches, pathMatches else {
            throw ChatGPTOAuthError.invalidCallbackURL
        }
        return components
    }

    private static func generatePKCECodeVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else {
            return UUID().uuidString.replacingOccurrences(of: "-", with: "")
        }
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func generatePKCECodeChallenge(_ codeVerifier: String) -> String {
        let digest = SHA256.hash(data: Data(codeVerifier.utf8))
        return Data(digest).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
