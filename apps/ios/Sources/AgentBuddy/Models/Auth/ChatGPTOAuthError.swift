import Foundation
import Security

enum ChatGPTOAuthError: LocalizedError {
    case invalidAuthorizeURL
    case invalidCallbackURL
    case missingAuthorizationCode
    case oauthError(String)
    case stateMismatch
    case unableToStartSession
    case cancelled
    case callbackTimedOut
    case missingRefreshToken
    case missingStoredTokens
    case missingAccountID
    case tokenExchangeFailed(status: Int, message: String)
    case refreshAccountMismatch
    case keychain(OSStatus)

    var errorDescription: String? {
        switch self {
        case .invalidAuthorizeURL:
            return "Failed to build the ChatGPT login URL."
        case .invalidCallbackURL:
            return "ChatGPT login returned an invalid callback."
        case .missingAuthorizationCode:
            return "ChatGPT login did not return an authorization code."
        case .oauthError(let message):
            return message
        case .stateMismatch:
            return "ChatGPT login state did not match the original request."
        case .unableToStartSession:
            return "Unable to start the ChatGPT login session."
        case .cancelled:
            return "ChatGPT login was cancelled."
        case .callbackTimedOut:
            return "ChatGPT login timed out before returning to AgentBuddy."
        case .missingRefreshToken:
            return "No ChatGPT refresh token is available."
        case .missingStoredTokens:
            return "No stored ChatGPT login is available to refresh."
        case .missingAccountID:
            return "ChatGPT login did not include an account identifier."
        case .tokenExchangeFailed(let status, let message):
            return "ChatGPT token exchange failed (\(status)): \(message)"
        case .refreshAccountMismatch:
            return "ChatGPT refresh returned a different account than expected."
        case .keychain(let status):
            return "Keychain error (\(status))"
        }
    }

    var isTransientKeychainAvailabilityFailure: Bool {
        guard case .keychain(let status) = self else { return false }
        return status == errSecInteractionNotAllowed || status == errSecNotAvailable
    }
}
