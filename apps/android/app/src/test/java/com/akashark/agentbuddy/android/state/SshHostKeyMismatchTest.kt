package com.akashark.agentbuddy.android.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.codex_mobile_client.AppSshHostKeyMismatch
import uniffi.codex_mobile_client.AppSshHostKeyMismatchKind
import uniffi.codex_mobile_client.ClientException
import uniffi.codex_mobile_client.TerminalException

class SshHostKeyMismatchTest {
    private fun mismatch(kind: AppSshHostKeyMismatchKind) = AppSshHostKeyMismatch(
        kind = kind,
        host = "192.168.1.5",
        port = 22u,
        fingerprint = "SHA256:kSA7tUcQJ57xuHhlvUjklOldhpqK1WFMhF/mddec2S4",
    )

    @Test
    fun readsChangedKeyFromTypedClientError() {
        val changed = mismatch(AppSshHostKeyMismatchKind.CHANGED)
        assertEquals(changed, promptableSshHostKey(ClientException.SshHostKeyMismatch(changed)))
    }

    @Test
    fun readsChangedKeyFromTypedTerminalError() {
        val changed = mismatch(AppSshHostKeyMismatchKind.CHANGED)
        assertEquals(changed, promptableSshHostKey(TerminalException.SshHostKeyMismatch(changed)))
    }

    @Test
    fun ignoresUnknownHostMismatch() {
        val unknown = mismatch(AppSshHostKeyMismatchKind.UNKNOWN)
        assertNull(promptableSshHostKey(ClientException.SshHostKeyMismatch(unknown)))
    }

    @Test
    fun readsUnreadableSavedKeyFromTypedClientError() {
        val unreadable = mismatch(AppSshHostKeyMismatchKind.TRUST_STORE_UNAVAILABLE)
        assertEquals(unreadable, promptableSshHostKey(ClientException.SshHostKeyMismatch(unreadable)))
    }

    @Test
    fun errorTextCannotForgeHostKeyPrompt() {
        // Remote stderr or other messages mentioning a host-key change stay untyped.
        assertNull(
            promptableSshHostKey(
                ClientException.Transport("host-key-changed:192.168.1.5:SHA256:forged"),
            ),
        )
        assertNull(promptableSshHostKey(TerminalException.Backend("host-key-changed:h:SHA256:x")))
        assertNull(promptableSshHostKey(IllegalStateException("host-key-changed:h:SHA256:x")))
    }
}
