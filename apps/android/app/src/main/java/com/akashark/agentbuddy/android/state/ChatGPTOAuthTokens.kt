package com.akashark.agentbuddy.android.state

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.UnknownHostException
import java.net.URL
import com.akashark.agentbuddy.android.state.ChatGPTOAuth.authIssuer

// ChatGPT OAuth tokens: the token bundle, the token-endpoint exchange (with
// network retries and JWT claim parsing) and the encrypted token store.

data class ChatGPTOAuthTokenBundle(
    val accessToken: String,
    val idToken: String,
    val refreshToken: String?,
    val accountId: String,
    val planType: String?,
)

internal object ChatGPTOAuthTokenExchange {
    private const val tokenExchangeMaxAttempts = 5

    internal suspend fun exchangeToken(body: String): ChatGPTOAuthTokenBundle = withContext(Dispatchers.IO) {
        tokenBundleFromPayload(exchangeTokenPayloadWithRetries(body))
    }

    internal suspend fun exchangeAccessToken(body: String): String = withContext(Dispatchers.IO) {
        val payload = exchangeTokenPayloadWithRetries(body)
        val accessToken = payload.optString("access_token").trim()
        if (accessToken.isEmpty()) {
            throw ChatGPTOAuthException("ChatGPT token exchange failed: missing access_token.")
        }
        accessToken
    }

    private fun tokenBundleFromPayload(payload: JSONObject): ChatGPTOAuthTokenBundle {
        val accessToken = payload.optString("access_token").trim()
        val idToken = payload.optString("id_token").trim()
        val refreshToken = payload.optString("refresh_token").trim().ifEmpty { null }
        if (accessToken.isEmpty() || idToken.isEmpty()) {
            throw ChatGPTOAuthException("ChatGPT token exchange failed: missing access_token or id_token.")
        }

        val idClaims = decodeJwtClaims(idToken)
        val accessClaims = decodeJwtClaims(accessToken)
        val accountId = resolveAccountId(idClaims, accessClaims)
        if (accountId.isEmpty()) {
            throw ChatGPTOAuthException("ChatGPT login did not include an account identifier.")
        }

        return ChatGPTOAuthTokenBundle(
            accessToken = accessToken,
            idToken = idToken,
            refreshToken = refreshToken,
            accountId = accountId,
            planType = resolvePlanType(idClaims, accessClaims),
        )
    }

    private suspend fun exchangeTokenPayloadWithRetries(body: String): JSONObject {
        var networkFailure: IOException? = null
        repeat(tokenExchangeMaxAttempts) { attemptIndex ->
            try {
                return exchangeTokenPayload(body)
            } catch (error: UnknownHostException) {
                networkFailure = error
                if (attemptIndex == tokenExchangeMaxAttempts - 1) {
                    throw ChatGPTOAuthException(
                        "ChatGPT token exchange could not reach auth.openai.com. Check the device connection and try again.",
                    )
                }
                logTokenExchangeRetry(error, attemptIndex)
                delay(tokenExchangeRetryDelayMs(attemptIndex))
            } catch (error: IOException) {
                networkFailure = error
                if (attemptIndex == tokenExchangeMaxAttempts - 1) {
                    throw ChatGPTOAuthException(
                        "ChatGPT token exchange failed: ${error.localizedMessage ?: error.message ?: error.javaClass.simpleName}",
                    )
                }
                logTokenExchangeRetry(error, attemptIndex)
                delay(tokenExchangeRetryDelayMs(attemptIndex))
            }
        }
        throw networkFailure ?: ChatGPTOAuthException("ChatGPT token exchange failed before it could start.")
    }

    private fun tokenExchangeRetryDelayMs(attemptIndex: Int): Long =
        when (attemptIndex) {
            0 -> 500L
            1 -> 1_000L
            2 -> 2_000L
            else -> 4_000L
        }

    private fun logTokenExchangeRetry(error: IOException, attemptIndex: Int) {
        LLog.w(
            "ChatGPTOAuth",
            "ChatGPT token exchange network failure; retrying",
            fields = mapOf(
                "attempt" to (attemptIndex + 1),
                "maxAttempts" to tokenExchangeMaxAttempts,
                "errorType" to error.javaClass.simpleName,
                "message" to (error.localizedMessage ?: error.message).orEmpty().take(160),
                "nextDelayMs" to tokenExchangeRetryDelayMs(attemptIndex),
            ),
        )
    }

    private fun exchangeTokenPayload(body: String): JSONObject {
        val url = URL("$authIssuer/oauth/token")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }

        try {
            LLog.i(
                "ChatGPTOAuth",
                "ChatGPT token exchange request",
                fields = mapOf(
                    "url" to url.toString(),
                    "grantType" to formValue(body, "grant_type"),
                ),
            )
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }.orEmpty()

            LLog.i(
                "ChatGPTOAuth",
                "ChatGPT token exchange response",
                fields = mapOf(
                    "status" to status,
                    "keys" to jsonObjectKeys(responseText).joinToString(","),
                ),
            )
            if (status !in 200..299) {
                LLog.w(
                    "ChatGPTOAuth",
                    "ChatGPT token exchange failed",
                    fields = mapOf("status" to status, "body" to redactedOAuthResponsePreview(responseText)),
                )
                throw ChatGPTOAuthException(
                    "ChatGPT token exchange failed ($status): ${responseText.take(300)}",
                )
            }

            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun formValue(body: String, name: String): String? =
        body.split("&")
            .firstOrNull { it.substringBefore("=") == name }
            ?.substringAfter("=", "")
            ?.let(Uri::decode)

    private fun jsonObjectKeys(text: String): List<String> = try {
        val payload = JSONObject(text)
        payload.keys().asSequence().toList().sorted()
    } catch (_: Exception) {
        emptyList()
    }

    private fun redactedOAuthResponsePreview(text: String): String = try {
        val payload = JSONObject(text)
        val keys = payload.keys().asSequence().toList()
        for (key in keys) {
            if (isSensitiveOAuthKey(key)) {
                payload.put(key, "<redacted>")
            }
        }
        payload.toString().take(300)
    } catch (_: Exception) {
        text.take(300)
    }

    private fun isSensitiveOAuthKey(key: String): Boolean {
        val normalized = key.replace("_", "").lowercase()
        return normalized.contains("token") || normalized.contains("authorization")
    }

    private fun resolveAccountId(idClaims: JSONObject, accessClaims: JSONObject): String {
        val candidates = listOf(
            idClaims.optString("chatgpt_account_id"),
            accessClaims.optString("chatgpt_account_id"),
            idClaims.optString("organization_id"),
            accessClaims.optString("organization_id"),
        )
        return candidates.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun resolvePlanType(idClaims: JSONObject, accessClaims: JSONObject): String? {
        val candidates = listOf(
            accessClaims.optString("chatgpt_plan_type"),
            idClaims.optString("chatgpt_plan_type"),
        )
        return candidates.firstOrNull { it.isNotBlank() }?.trim()
    }

    private fun decodeJwtClaims(jwt: String): JSONObject {
        val parts = jwt.split(".")
        if (parts.size < 2) return JSONObject()
        return try {
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val obj = JSONObject(String(decoded, Charsets.UTF_8))
            obj.optJSONObject("https://api.openai.com/auth") ?: obj
        } catch (_: Exception) {
            JSONObject()
        }
    }
}

class ChatGPTOAuthTokenStore(context: Context) {
    private val prefs = openEncryptedPrefsOrReset(context, PREFS_NAME)

    fun load(): ChatGPTOAuthTokenBundle? {
        val raw = prefs.getString(KEY_TOKENS, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            ChatGPTOAuthTokenBundle(
                accessToken = obj.getString("accessToken"),
                idToken = obj.getString("idToken"),
                refreshToken = obj.optString("refreshToken").takeIf { it.isNotBlank() },
                accountId = obj.getString("accountId"),
                planType = obj.optString("planType").takeIf { it.isNotBlank() },
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(tokens: ChatGPTOAuthTokenBundle) {
        val payload = JSONObject().apply {
            put("accessToken", tokens.accessToken)
            put("idToken", tokens.idToken)
            put("accountId", tokens.accountId)
            tokens.refreshToken?.let { put("refreshToken", it) }
            tokens.planType?.let { put("planType", it) }
        }
        prefs.edit().putString(KEY_TOKENS, payload.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKENS).apply()
    }

    companion object {
        private const val PREFS_NAME = "agentbuddy_chatgpt_auth"
        private const val KEY_TOKENS = "tokens"
    }
}
