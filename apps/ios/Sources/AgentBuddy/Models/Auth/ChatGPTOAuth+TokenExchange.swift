import Foundation

extension ChatGPTOAuth {
    static func exchangeAuthorizationCode(
        code: String,
        codeVerifier: String,
        redirectURI: String
    ) async throws -> ChatGPTOAuthTokenBundle {
        let body = [
            "grant_type=authorization_code",
            "code=\(urlEncode(code))",
            "redirect_uri=\(urlEncode(redirectURI))",
            "client_id=\(urlEncode(clientID))",
            "code_verifier=\(urlEncode(codeVerifier))"
        ].joined(separator: "&")
        return try await exchangeToken(body: body)
    }

    static func exchangeAuthorizationCodeForAccessToken(
        code: String,
        codeVerifier: String,
        redirectURI: String
    ) async throws -> String {
        let body = [
            "grant_type=authorization_code",
            "code=\(urlEncode(code))",
            "redirect_uri=\(urlEncode(redirectURI))",
            "client_id=\(urlEncode(clientID))",
            "code_verifier=\(urlEncode(codeVerifier))"
        ].joined(separator: "&")
        return try await exchangeAccessToken(body: body)
    }

    static func exchangeRefreshToken(
        _ refreshToken: String,
        fallbackRefreshToken: String? = nil
    ) async throws -> ChatGPTOAuthTokenBundle {
        let body = [
            "grant_type=refresh_token",
            "refresh_token=\(urlEncode(refreshToken))",
            "client_id=\(urlEncode(clientID))"
        ].joined(separator: "&")
        return try await exchangeToken(
            body: body,
            fallbackRefreshToken: fallbackRefreshToken
        )
    }

    private static func exchangeToken(
        body: String,
        fallbackRefreshToken: String? = nil
    ) async throws -> ChatGPTOAuthTokenBundle {
        guard let url = URL(string: "\(authIssuer)/oauth/token") else {
            throw ChatGPTOAuthError.invalidAuthorizeURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = body.data(using: .utf8)
        request.timeoutInterval = 20
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        LLog.info("auth", "ChatGPT token exchange request", fields: [
            "url": url.absoluteString,
            "grantType": formValue("grant_type", in: body) ?? "<unknown>"
        ])
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ChatGPTOAuthError.tokenExchangeFailed(status: -1, message: "missing HTTP response")
        }
        let responseText = String(decoding: data, as: UTF8.self)
        LLog.info("auth", "ChatGPT token exchange response", fields: [
            "status": http.statusCode,
            "keys": jsonObjectKeys(data).joined(separator: ",")
        ])
        guard (200...299).contains(http.statusCode) else {
            LLog.warn("auth", "ChatGPT token exchange failed", fields: [
                "status": http.statusCode,
                "body": redactedOAuthResponsePreview(responseText)
            ])
            throw ChatGPTOAuthError.tokenExchangeFailed(
                status: http.statusCode,
                message: String(responseText.prefix(300))
            )
        }

        let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return try tokenBundle(
            from: payload,
            statusCode: http.statusCode,
            fallbackRefreshToken: fallbackRefreshToken
        )
    }

    private static func exchangeAccessToken(body: String) async throws -> String {
        guard let url = URL(string: "\(authIssuer)/oauth/token") else {
            throw ChatGPTOAuthError.invalidAuthorizeURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = body.data(using: .utf8)
        request.timeoutInterval = 20
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        LLog.info("auth", "ChatGPT access-token exchange request", fields: [
            "url": url.absoluteString,
            "grantType": formValue("grant_type", in: body) ?? "<unknown>"
        ])
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ChatGPTOAuthError.tokenExchangeFailed(status: -1, message: "missing HTTP response")
        }
        let responseText = String(decoding: data, as: UTF8.self)
        LLog.info("auth", "ChatGPT access-token exchange response", fields: [
            "status": http.statusCode,
            "keys": jsonObjectKeys(data).joined(separator: ",")
        ])
        guard (200...299).contains(http.statusCode) else {
            LLog.warn("auth", "ChatGPT access-token exchange failed", fields: [
                "status": http.statusCode,
                "body": redactedOAuthResponsePreview(responseText)
            ])
            throw ChatGPTOAuthError.tokenExchangeFailed(
                status: http.statusCode,
                message: String(responseText.prefix(300))
            )
        }

        let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let accessToken = (payload?["access_token"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !accessToken.isEmpty else {
            throw ChatGPTOAuthError.tokenExchangeFailed(
                status: http.statusCode,
                message: "missing access_token"
            )
        }
        return accessToken
    }

    private static func formValue(_ name: String, in body: String) -> String? {
        var components = URLComponents()
        components.query = body
        return components.queryItems?.first(where: { $0.name == name })?.value
    }

    private static func jsonObjectKeys(_ data: Data) -> [String] {
        guard let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return []
        }
        return payload.keys.sorted()
    }

    private static func redactedOAuthResponsePreview(_ text: String) -> String {
        guard
            let data = text.data(using: .utf8),
            var payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return String(text.prefix(300))
        }
        for key in payload.keys where isSensitiveOAuthKey(key) {
            payload[key] = "<redacted>"
        }
        guard
            let redacted = try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]),
            let rendered = String(data: redacted, encoding: .utf8)
        else {
            return String(text.prefix(300))
        }
        return String(rendered.prefix(300))
    }

    private static func isSensitiveOAuthKey(_ key: String) -> Bool {
        let normalized = key.replacingOccurrences(of: "_", with: "").lowercased()
        return normalized.contains("token") || normalized.contains("authorization")
    }

    private static func urlEncode(_ value: String) -> String {
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}
