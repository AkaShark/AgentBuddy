import Foundation

extension ChatGPTOAuth {
    static func tokenBundle(
        from payload: [String: Any]?,
        statusCode: Int,
        fallbackRefreshToken: String? = nil
    ) throws -> ChatGPTOAuthTokenBundle {
        let accessToken = (payload?["access_token"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let idToken = (payload?["id_token"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let refreshTokenString = (payload?["refresh_token"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let refreshToken = refreshTokenString.flatMap { token in
            token.isEmpty ? nil : token
        } ?? fallbackRefreshToken
        guard !accessToken.isEmpty, !idToken.isEmpty else {
            throw ChatGPTOAuthError.tokenExchangeFailed(
                status: statusCode,
                message: "missing access_token or id_token"
            )
        }

        let idClaims = decodeJWTClaims(idToken)
        let accessClaims = decodeJWTClaims(accessToken)
        let accountID = resolveAccountID(idClaims: idClaims, accessClaims: accessClaims)
        guard !accountID.isEmpty else {
            throw ChatGPTOAuthError.missingAccountID
        }
        let planType = resolvePlanType(idClaims: idClaims, accessClaims: accessClaims)

        return ChatGPTOAuthTokenBundle(
            accessToken: accessToken,
            idToken: idToken,
            refreshToken: refreshToken,
            accountID: accountID,
            planType: planType
        )
    }

    private static func resolveAccountID(
        idClaims: [String: Any],
        accessClaims: [String: Any]
    ) -> String {
        let candidates: [String?] = [
            idClaims["chatgpt_account_id"] as? String,
            accessClaims["chatgpt_account_id"] as? String,
            idClaims["organization_id"] as? String,
            accessClaims["organization_id"] as? String
        ]
        if let accountID = candidates
            .compactMap({ $0?.trimmingCharacters(in: .whitespacesAndNewlines) })
            .first(where: { !$0.isEmpty }) {
            return accountID
        }
        return ""
    }

    private static func resolvePlanType(
        idClaims: [String: Any],
        accessClaims: [String: Any]
    ) -> String? {
        let candidates: [String?] = [
            accessClaims["chatgpt_plan_type"] as? String,
            idClaims["chatgpt_plan_type"] as? String
        ]
        return candidates
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })
    }

    private static func decodeJWTClaims(_ jwt: String) -> [String: Any] {
        let parts = jwt.split(separator: ".")
        guard parts.count > 1 else { return [:] }
        let payload = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padded = payload.padding(
            toLength: ((payload.count + 3) / 4) * 4,
            withPad: "=",
            startingAt: 0
        )
        guard let data = Data(base64Encoded: padded),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return [:]
        }
        if let authClaims = object["https://api.openai.com/auth"] as? [String: Any] {
            return authClaims
        }
        return object
    }
}
