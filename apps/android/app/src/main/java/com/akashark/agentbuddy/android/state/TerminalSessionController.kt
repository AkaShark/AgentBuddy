package com.akashark.agentbuddy.android.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSshHostKeyMismatch
import uniffi.codex_mobile_client.AppSshHostKeyMismatchKind
import uniffi.codex_mobile_client.AppStore
import uniffi.codex_mobile_client.TerminalBackendKind
import uniffi.codex_mobile_client.TerminalException
import uniffi.codex_mobile_client.TerminalOutputListener
import uniffi.codex_mobile_client.TerminalSession
import uniffi.codex_mobile_client.TerminalSize

class TerminalSessionController(
    private val scope: CoroutineScope,
    private val appStore: AppStore = AppModel.shared.store,
) {
    enum class Phase {
        IDLE,
        CONNECTING,
        RUNNING,
        EXITED,
        FAILED,
    }

    /**
     * An SSH open refused by the host-key check: [AppSshHostKeyMismatchKind.UNKNOWN]
     * for a first connect (inline trust button), [AppSshHostKeyMismatchKind.CHANGED]
     * when the pinned key differs (confirmation dialog),
     * [AppSshHostKeyMismatchKind.TRUST_STORE_UNAVAILABLE] when the pinned key could
     * not be read (dialog offering to forget it).
     */
    data class SshHostTrustChallenge(
        val mismatch: AppSshHostKeyMismatch,
        val backend: TerminalBackendKind,
    ) {
        val fingerprint: String get() = mismatch.fingerprint
    }

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var output by mutableStateOf("")
        private set
    var exitCode by mutableStateOf<Int?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var sshTrustChallenge by mutableStateOf<SshHostTrustChallenge?>(null)
        private set
    var sshHostKeyChange by mutableStateOf<SshHostTrustChallenge?>(null)
        private set

    var sessionId: String? = null
        private set
    private var listener: TerminalOutputListener? = null
    @Volatile
    private var outputByteSink: ((ByteArray) -> Unit)? = null
    @Volatile
    private var eventGeneration: Int = 0
    private var terminalCols: UShort = 80u
    private var terminalRows: UShort = 24u

    private fun activeSession(): TerminalSession? =
        sessionId?.let { appStore.terminalSessionHandle(it) }

    val canSendInput: Boolean
        get() = phase == Phase.RUNNING

    fun openLocalProot(cwd: String? = null) {
        open(TerminalBackendKind.LocalProot(normalized(cwd)))
    }

    fun open(backend: TerminalBackendKind) {
        if (sessionId != null || phase == Phase.CONNECTING) return
        eventGeneration += 1
        val generation = eventGeneration
        phase = Phase.CONNECTING
        errorMessage = null
        exitCode = null
        sshTrustChallenge = null
        sshHostKeyChange = null
        scope.launch {
            try {
                val size = TerminalSize(cols = terminalCols, rows = terminalRows)
                val id = if (backend is TerminalBackendKind.RemoteSsh) {
                    appStore.openTerminalSessionWithTrustStore(
                        backend,
                        size,
                        AppModel.shared.sshTrustStore,
                    )
                } else {
                    appStore.openTerminalSession(backend, size)
                }
                sessionId = id
                appStore.setActiveTerminalId(id)
                val opened = appStore.terminalSessionHandle(id) ?: run {
                    sessionId = null
                    errorMessage = "Session disappeared after open"
                    phase = Phase.FAILED
                    return@launch
                }
                val outputListener = object : TerminalOutputListener {
                    override fun onBytes(data: ByteArray) {
                        if (generation != eventGeneration) return
                        val sink = outputByteSink
                        if (sink != null) {
                            sink(data.copyOf())
                            return
                        }
                        scope.launch(Dispatchers.Main.immediate) {
                            if (generation == eventGeneration) {
                                appendOutput(data)
                            }
                        }
                    }

                    override fun onExit(code: Int) {
                        scope.launch(Dispatchers.Main.immediate) {
                            if (generation == eventGeneration) {
                                exitCode = code
                                phase = Phase.EXITED
                            }
                        }
                    }
                }
                opened.subscribeOutput(outputListener)
                listener = outputListener
                phase = Phase.RUNNING
            } catch (error: Exception) {
                sessionId = null
                val mismatch = (error as? TerminalException.SshHostKeyMismatch)?.mismatch
                if (mismatch == null) {
                    errorMessage = error.message ?: "Unable to open terminal"
                } else {
                    val challenge = SshHostTrustChallenge(mismatch, backend)
                    when (mismatch.kind) {
                        AppSshHostKeyMismatchKind.UNKNOWN -> {
                            sshTrustChallenge = challenge
                            errorMessage = "Unknown SSH host key ${mismatch.fingerprint}"
                        }
                        AppSshHostKeyMismatchKind.CHANGED -> {
                            sshHostKeyChange = challenge
                            errorMessage = "SSH host key changed ${mismatch.fingerprint}"
                        }
                        AppSshHostKeyMismatchKind.TRUST_STORE_UNAVAILABLE -> {
                            sshHostKeyChange = challenge
                            errorMessage = "无法读取已保存的 SSH 主机密钥"
                        }
                    }
                }
                phase = Phase.FAILED
            }
        }
    }

    fun trustUnknownSshHostAndRetry() {
        val challenge = sshTrustChallenge ?: return
        val mismatch = challenge.mismatch
        AppModel.shared.sshTrustStore.pin(mismatch.host, mismatch.port, mismatch.fingerprint)
        reopen(challenge.backend)
    }

    /**
     * Reopens after the host-key dialog pinned the displayed key (or forgot the
     * unreadable saved one).
     */
    fun retryAfterHostKeyChange() {
        val challenge = sshHostKeyChange ?: return
        reopen(challenge.backend)
    }

    fun dismissHostKeyChange() {
        sshHostKeyChange = null
    }

    private fun reopen(backend: TerminalBackendKind) {
        sshTrustChallenge = null
        sshHostKeyChange = null
        errorMessage = null
        phase = Phase.IDLE
        open(backend)
    }

    fun switchBackend(backend: TerminalBackendKind) {
        close()
        output = ""
        open(backend)
    }

    fun send(value: String) {
        sendBytes(value.toByteArray(Charsets.UTF_8))
    }

    fun sendBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val activeSession = activeSession() ?: return
        if (!canSendInput) return
        scope.launch {
            try {
                activeSession.writeInput(bytes)
            } catch (error: Exception) {
                errorMessage = error.message ?: "Unable to write terminal input"
                phase = Phase.FAILED
            }
        }
    }

    fun sendLine(value: String) {
        send("$value\n")
    }

    fun clearOutput() {
        output = ""
    }

    fun setOutputByteSink(sink: ((ByteArray) -> Unit)?) {
        outputByteSink = sink
    }

    fun resize(cols: Int, rows: Int, notifyBackend: Boolean = true) {
        if (cols <= 0 || rows <= 0) return
        terminalCols = cols.coerceIn(1, UShort.MAX_VALUE.toInt()).toUShort()
        terminalRows = rows.coerceIn(1, UShort.MAX_VALUE.toInt()).toUShort()
        val activeSession = activeSession() ?: return
        if (!notifyBackend || !canSendInput) return
        val size = TerminalSize(cols = terminalCols, rows = terminalRows)
        scope.launch {
            try {
                activeSession.resize(size)
            } catch (error: Exception) {
                errorMessage = error.message ?: "Unable to resize terminal"
                phase = Phase.FAILED
            }
        }
    }

    fun close() {
        eventGeneration += 1
        val id = sessionId ?: return
        sessionId = null
        listener = null
        phase = Phase.IDLE
        scope.launch {
            runCatching { appStore.closeTerminalSession(id) }
        }
    }

    private fun appendOutput(data: ByteArray) {
        val sink = outputByteSink
        sink?.invoke(data.copyOf())
        if (sink != null) return
        output += data.toString(Charsets.UTF_8)
        trimOutputIfNeeded()
    }

    private fun normalized(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.ifEmpty { null }
    }

    private fun trimOutputIfNeeded() {
        val maxCount = 64_000
        if (output.length > maxCount) {
            output = output.takeLast(maxCount)
        }
    }
}
