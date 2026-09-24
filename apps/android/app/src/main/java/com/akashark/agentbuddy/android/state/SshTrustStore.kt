package com.akashark.agentbuddy.android.state

import android.content.Context
import com.akashark.agentbuddy.android.util.LLog
import uniffi.codex_mobile_client.AppSshHostKeyMismatch
import uniffi.codex_mobile_client.AppSshHostKeyMismatchKind
import uniffi.codex_mobile_client.ClientException
import uniffi.codex_mobile_client.SshTrustLookup
import uniffi.codex_mobile_client.TerminalException
import uniffi.codex_mobile_client.TerminalSshTrustBackend

/// Persistent host-key fingerprint pinning backed by EncryptedSharedPreferences.
/// Implements the Rust [`TerminalSshTrustBackend`] callback interface so the
/// shared terminal SSH backend can consult and update pins on every connect.
class SshTrustStore(context: Context) : TerminalSshTrustBackend {
    private val prefs = openEncryptedPrefsOrReset(context, PREFS_NAME) { error ->
        // Every previously pinned host now looks new and will be re-pinned on
        // its next connect, so make the lost pins visible in the logs.
        LLog.e(TAG, "SSH host-key pin store was unreadable and has been reset", error)
    }

    /// Only a missing entry means "not pinned"; a failed read is reported as
    /// unavailable so Rust refuses the connect instead of re-pinning.
    override fun read(host: String, port: UShort): SshTrustLookup =
        try {
            prefs.getString(key(host, port), null)
                ?.let { SshTrustLookup.Pinned(it) }
                ?: SshTrustLookup.NotPinned
        } catch (e: Exception) {
            LLog.e(TAG, "SSH host-key pin read failed", e, fields = mapOf("host" to host, "port" to port))
            SshTrustLookup.Unavailable(e.message ?: e.javaClass.simpleName)
        }

    override fun write(host: String, port: UShort, fingerprint: String) {
        prefs.edit().putString(key(host, port), fingerprint).apply()
    }

    /// Also backs "忘记已保存的主机密钥" for an unreadable pin, so a failure is
    /// logged rather than thrown across the Rust callback boundary.
    override fun remove(host: String, port: UShort) {
        try {
            prefs.edit().remove(key(host, port)).apply()
        } catch (e: Exception) {
            LLog.e(TAG, "SSH host-key pin remove failed", e, fields = mapOf("host" to host, "port" to port))
        }
    }

    private fun key(host: String, port: UShort): String = "${host.lowercase()}:$port"

    companion object {
        private const val PREFS_NAME = "agentbuddy_ssh_trust"
        private const val TAG = "SshTrustStore"
    }
}

/// The host-key mismatch the user can act on, carried by a typed Rust SSH error
/// ([ClientException] / [TerminalException]): a changed key (「信任新密钥」) or an
/// unreadable saved key (「忘记已保存的主机密钥」). Null for any other error.
fun promptableSshHostKey(error: Throwable): AppSshHostKeyMismatch? {
    val mismatch = when (error) {
        is ClientException.SshHostKeyMismatch -> error.mismatch
        is TerminalException.SshHostKeyMismatch -> error.mismatch
        else -> null
    }
    return mismatch?.takeIf { it.isPromptable }
}

/// Changed key or unreadable saved key; both are shown by `SshHostKeyChangedDialog`.
val AppSshHostKeyMismatch.isPromptable: Boolean
    get() = kind == AppSshHostKeyMismatchKind.CHANGED ||
        kind == AppSshHostKeyMismatchKind.TRUST_STORE_UNAVAILABLE
