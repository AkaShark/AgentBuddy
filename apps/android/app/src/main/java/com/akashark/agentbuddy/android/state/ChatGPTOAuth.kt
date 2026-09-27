package com.akashark.agentbuddy.android.state

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

class ChatGPTOAuthException(message: String) : Exception(message)

object ChatGPTOAuth {
    const val MODE_LOGIN = "login"
    const val MODE_REMOTE_CONTROL_ENROLL = "remote_control_enroll"
    const val authIssuer = "https://auth.openai.com"
    private const val clientId = "app_EMoamEEZ73f0CkXaXp7hrann"
    private const val callbackScheme = "http"
    private const val callbackHost = "localhost"
    private const val callbackBindHost = "127.0.0.1"
    private const val callbackPort = 1455
    private const val callbackPath = "/auth/callback"

    data class AuthAttempt(
        val state: String,
        val codeVerifier: String,
        val redirectUri: String,
        val authorizeUrl: String,
        val mode: String = MODE_LOGIN,
    )

    fun createLoginAttempt(): AuthAttempt {
        val state = java.util.UUID.randomUUID().toString()
        val codeVerifier = generatePkceCodeVerifier()
        val codeChallenge = generatePkceCodeChallenge(codeVerifier)
        val redirectUri = "$callbackScheme://$callbackHost:$callbackPort$callbackPath"
        val authorizeUrl = Uri.parse("$authIssuer/oauth/authorize")
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "openid profile email offline_access")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("id_token_add_organizations", "true")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .build()
            .toString()
        return AuthAttempt(
            state = state,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri,
            authorizeUrl = authorizeUrl,
            mode = MODE_LOGIN,
        )
    }

    fun createRemoteControlEnrollmentAttempt(): AuthAttempt {
        val state = java.util.UUID.randomUUID().toString()
        val codeVerifier = generatePkceCodeVerifier()
        val codeChallenge = generatePkceCodeChallenge(codeVerifier)
        val redirectUri = "$callbackScheme://$callbackHost:$callbackPort$callbackPath"
        val authorizeUrl = Uri.parse("$authIssuer/oauth/authorize")
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "codex.remote_control.enroll")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("originator", "Codex Desktop")
            .appendQueryParameter("reauth", "remote_control")
            .appendQueryParameter("max_age", "0")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .build()
            .toString()
        LLog.i(
            "Slingshot",
            "remote-control step-up auth attempt created",
            fields = mapOf("state" to state, "redirectUri" to redirectUri),
        )
        return AuthAttempt(
            state = state,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri,
            authorizeUrl = authorizeUrl,
            mode = MODE_REMOTE_CONTROL_ENROLL,
        )
    }

    fun isRemoteControlAuthorizationRequired(error: Throwable): Boolean {
        val message = "${error.message.orEmpty()} ${error}".lowercase()
        return message.contains("missing slingshot remote-control authorization token") ||
            message.contains("missing slingshot client session token") ||
            message.contains("remote-control authorization")
    }

    suspend fun loadStoredOrRefreshedTokens(context: Context): ChatGPTOAuthTokenBundle? {
        val appContext = context.applicationContext
        val stored = ChatGPTOAuthTokenStore(appContext).load() ?: return null
        return runCatching {
            refreshStoredTokens(appContext, stored.accountId)
        }.getOrElse {
            stored
        }
    }

    suspend fun requireStoredOrRefreshedTokens(
        context: Context,
        missingMessage: String,
    ): ChatGPTOAuthTokenBundle =
        loadStoredOrRefreshedTokens(context) ?: throw IllegalStateException(missingMessage)

    fun isCallbackUri(uri: Uri): Boolean {
        val host = uri.host?.lowercase()
        return uri.scheme == callbackScheme &&
            (host == callbackHost || host == callbackBindHost) &&
            uri.path == callbackPath
    }

    suspend fun completeAuthorization(
        context: Context,
        callbackUri: Uri,
        attempt: AuthAttempt,
    ): ChatGPTOAuthTokenBundle {
        validateCallbackUri(callbackUri)
        val error = callbackUri.getQueryParameter("error")?.trim()
        if (!error.isNullOrEmpty()) {
            val description = callbackUri.getQueryParameter("error_description")?.trim()
            throw ChatGPTOAuthException(
                description?.takeIf { it.isNotEmpty() } ?: error,
            )
        }

        val state = callbackUri.getQueryParameter("state")
        if (state != attempt.state) {
            throw ChatGPTOAuthException("ChatGPT login state did not match the original request.")
        }

        val code = callbackUri.getQueryParameter("code")?.trim()
        if (code.isNullOrEmpty()) {
            throw ChatGPTOAuthException("ChatGPT login did not return an authorization code.")
        }

        val body = listOf(
            "grant_type=authorization_code",
            "code=${Uri.encode(code)}",
            "redirect_uri=${Uri.encode(attempt.redirectUri)}",
            "client_id=${Uri.encode(clientId)}",
            "code_verifier=${Uri.encode(attempt.codeVerifier)}",
        ).joinToString("&")

        val tokens = ChatGPTOAuthTokenExchange.exchangeToken(body)
        ChatGPTOAuthTokenStore(context).save(tokens)
        return tokens
    }

    suspend fun completeRemoteControlEnrollmentAuthorization(
        callbackUri: Uri,
        attempt: AuthAttempt,
    ): String {
        validateAuthorizationCallback(callbackUri, attempt)
        LLog.i(
            "Slingshot",
            "remote-control step-up auth callback received",
            fields = mapOf("state" to attempt.state),
        )
        val code = callbackUri.getQueryParameter("code")?.trim()
            ?: throw ChatGPTOAuthException("ChatGPT login did not return an authorization code.")
        if (code.isEmpty()) {
            throw ChatGPTOAuthException("ChatGPT login did not return an authorization code.")
        }

        val body = listOf(
            "grant_type=authorization_code",
            "code=${Uri.encode(code)}",
            "redirect_uri=${Uri.encode(attempt.redirectUri)}",
            "client_id=${Uri.encode(clientId)}",
            "code_verifier=${Uri.encode(attempt.codeVerifier)}",
        ).joinToString("&")

        val token = ChatGPTOAuthTokenExchange.exchangeAccessToken(body)
        LLog.i(
            "Slingshot",
            "remote-control step-up token received",
            fields = mapOf("tokenLength" to token.length),
        )
        return token
    }

    suspend fun refreshStoredTokens(
        context: Context,
        previousAccountId: String?,
    ): ChatGPTOAuthTokenBundle {
        val stored = withContext(Dispatchers.IO) {
            ChatGPTOAuthTokenStore(context).load()
        } ?: throw ChatGPTOAuthException("No stored ChatGPT login is available to refresh.")
        val refreshToken = stored.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw ChatGPTOAuthException("No ChatGPT refresh token is available.")
        val body = listOf(
            "grant_type=refresh_token",
            "refresh_token=${Uri.encode(refreshToken)}",
            "client_id=${Uri.encode(clientId)}",
        ).joinToString("&")
        val refreshed = ChatGPTOAuthTokenExchange.exchangeToken(body)
        if (!previousAccountId.isNullOrBlank() &&
            refreshed.accountId != previousAccountId &&
            stored.accountId != previousAccountId
        ) {
            throw ChatGPTOAuthException("ChatGPT refresh returned a different account than expected.")
        }
        withContext(Dispatchers.IO) {
            ChatGPTOAuthTokenStore(context).save(refreshed)
        }
        return refreshed
    }

    private fun validateAuthorizationCallback(callbackUri: Uri, attempt: AuthAttempt) {
        validateCallbackUri(callbackUri)
        val error = callbackUri.getQueryParameter("error")?.trim()
        if (!error.isNullOrEmpty()) {
            val description = callbackUri.getQueryParameter("error_description")?.trim()
            throw ChatGPTOAuthException(
                description?.takeIf { it.isNotEmpty() } ?: error,
            )
        }

        val state = callbackUri.getQueryParameter("state")
        if (state != attempt.state) {
            throw ChatGPTOAuthException("ChatGPT login state did not match the original request.")
        }
    }

    private fun validateCallbackUri(callbackUri: Uri) {
        if (!isCallbackUri(callbackUri)) {
            throw ChatGPTOAuthException("ChatGPT login returned an invalid callback.")
        }
    }

    private fun generatePkceCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generatePkceCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
