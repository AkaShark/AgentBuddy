package com.akashark.agentbuddy.android.state

import com.akashark.agentbuddy.android.core.bridge.UniffiInit
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import uniffi.codex_mobile_client.AppClient
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import uniffi.codex_mobile_client.AppSnapshotRecord
import uniffi.codex_mobile_client.AppStore
import uniffi.codex_mobile_client.AppStoreSubscription
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.AppStoreUpdateRecord
import uniffi.codex_mobile_client.DiscoveryBridge
import uniffi.codex_mobile_client.MessageParser
import uniffi.codex_mobile_client.ReconnectController
import uniffi.codex_mobile_client.ServerBridge
import uniffi.codex_mobile_client.SshBridge
import uniffi.codex_mobile_client.TerminalSshTrustStore
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppStartThreadRequest
import uniffi.codex_mobile_client.registerAndroidTools
import uniffi.codex_mobile_client.setSshTrustStore

class LocalAccountLoginRequiredException(val serverId: String) :
    IllegalStateException("Local account login is required.")

/**
 * Central app state singleton. Thin wrapper over Rust [AppStore] — all business
 * logic, reconciliation, and state management lives in Rust.
 *
 * Exposes a [snapshot] StateFlow that the UI observes. Updated automatically
 * via the Rust subscription stream.
 */
class AppModel private constructor(context: android.content.Context) {

    data class ComposerPrefillRequest(
        val requestId: Long,
        val threadKey: ThreadKey,
        val text: String,
    )

    /**
     * Live composer draft (typed-but-unsent text plus any pasted/picked
     * attachment) for a thread. Cached on `AppModel` so the draft survives
     * `ComposerBar` recomposition / view-tree teardown when the user
     * backgrounds the app — otherwise the local `remember { mutableStateOf }`
     * inside `ComposerBar` is dropped and the user's text disappears.
     */
    data class ComposerDraft(
        val text: String = "",
        val attachment: ComposerImageAttachment? = null,
        val fileAttachments: List<ComposerFileAttachment> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = text.isEmpty() && attachment == null && fileAttachments.isEmpty()

        companion object {
            val EMPTY = ComposerDraft()
        }
    }

    companion object {
        @Volatile
        private var _instance: AppModel? = null

        val shared: AppModel
            get() = _instance ?: throw IllegalStateException("AppModel not initialized — call init(context) first")

        /** The live instance, without creating one (e.g. from the FCM service thread). */
        val sharedOrNull: AppModel?
            get() = _instance

        fun init(context: android.content.Context): AppModel {
            if (_instance == null) {
                _instance = AppModel(context.applicationContext)
            }
            return _instance!!
        }

        /**
         * Matches the iOS page sizes. Server clamps this at 100.
         */
        const val INITIAL_TURN_PAGE_LIMIT: UInt = 5u
        const val OLDER_TURN_PAGE_LIMIT: UInt = 5u
        internal const val SESSION_LIST_PAGE_LIMIT: UInt = 80u
    }

    // --- Rust bridges (singletons behind the scenes) -------------------------

    val store: AppStore
    val client: AppClient
    val discovery: DiscoveryBridge
    val serverBridge: ServerBridge
    val ssh: SshBridge
    val sshSessionStore: SshSessionStore
    /** Process-wide SSH host-key pins shared with every Rust SSH connect path. */
    val sshTrustStore: TerminalSshTrustStore
    val parser: MessageParser
    val reconnectController: ReconnectController
    val launchState: AppLaunchState
    /** Observes Wi-Fi ↔ cellular handoffs etc. and hints iroh. */
    val reachability: NetworkReachabilityObserver
    /** Persists the iroh device secret key across cold launches. */
    val alleycatCredentials: AlleycatCredentialStore
    val appContext: android.content.Context = context
    init {
        UniffiInit.ensure(context)
        Thread({
            AndroidProotBootstrap.bootstrap(context)
        }, "agentbuddy-proot-bootstrap").start()
        registerBundledCliTools()
        LLog.bootstrap(context)
        store = AppStore()
        client = AppClient()
        // The show_widget auto-save hook on the Rust side persists to this
        // directory. Without setting it at launch the hook is a silent no-op.
        client.setSavedAppsDirectory(SavedAppsDirectory.path(context))
        client.setSlingshotCredentialsDirectory(MobilePreferencesDirectory.path(context))
        discovery = DiscoveryBridge()
        serverBridge = ServerBridge()
        ssh = SshBridge()
        sshSessionStore = SshSessionStore(ssh)
        // Register host-key pinning before any SSH connect or reconnect runs.
        sshTrustStore = TerminalSshTrustStore(SshTrustStore(context))
        setSshTrustStore(sshTrustStore)
        parser = MessageParser()
        reconnectController = ReconnectController()
        reconnectController.setCredentialProvider(
            KotlinSshCredentialProvider(SshCredentialStore(context))
        )
        reconnectController.setSlingshotCredentialProvider(
            KotlinSlingshotCredentialProvider(ChatGPTOAuthTokenStore(context))
        )
        reconnectController.setMultiClankerAndQuicEnabled(true)
        launchState = AppLaunchState(context)
        reachability = NetworkReachabilityObserver(context, this)
        reachability.start()

        // Push any persisted iroh device secret key to the Rust client
        // BEFORE any alleycat operation triggers the endpoint bind, so
        // the same `EndpointId` is reused across cold launches.
        alleycatCredentials = AlleycatCredentialStore(context)
        runCatching { alleycatCredentials.loadDeviceSecretKey() }
            .getOrNull()
            ?.let { client.setAlleycatSecretKey(it) }
    }

    /**
     * After an alleycat operation has triggered the Rust endpoint
     * bind, read back the device key bytes from Rust and persist if
     * not already saved. Idempotent.
     */
    fun persistAlleycatSecretKeyIfNeeded() {
        val bytes = client.alleycatSecretKey() ?: return
        val existing = runCatching { alleycatCredentials.loadDeviceSecretKey() }.getOrNull()
        if (existing != null && existing.contentEquals(bytes)) return
        runCatching { alleycatCredentials.saveDeviceSecretKey(bytes) }
            .onFailure { LLog.w("AppModel", "saveDeviceSecretKey failed: ${it.message}") }
    }

    // --- Observable state ----------------------------------------------------

    internal val _snapshot = MutableStateFlow<AppSnapshotRecord?>(null)
    val snapshot: StateFlow<AppSnapshotRecord?> = _snapshot.asStateFlow()

    internal val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    // Touched from the main thread, the subscription loop and `scope` jobs
    // concurrently, so these must be thread-safe.
    internal val loadingModelServerIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    internal val loadingRateLimitServerIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    internal val recentConversationMetadataLoads = ConcurrentHashMap<String, Long>()
    internal val cachedThreadSnapshots = ConcurrentHashMap<ThreadKey, AppThreadSnapshot>()

    /**
     * Serializes every read-modify-write of [_snapshot] (plus the thread
     * cache it merges from) so concurrent writers cannot drop each other's
     * updates. Guarded sections are non-suspending and short.
     */
    internal val snapshotLock = Any()
    @Volatile internal var savedServerNamesCache: Pair<Long, Map<String, String>>? = null
    @Volatile internal var lastPersistedWakeMacs: Pair<Long, List<Triple<String, String, String?>>>? = null
    internal val sessionListMutex = Mutex()
    internal var pendingActiveThreadHydrationKey: ThreadKey? = null
    internal var pendingActiveThreadHydrationJob: Job? = null

    // --- Composer prefill queue (for edit message / slash commands) -----------

    private val nextComposerPrefillRequestId = AtomicLong(0)
    private val _composerPrefillRequest = MutableStateFlow<ComposerPrefillRequest?>(null)
    val composerPrefillRequest: StateFlow<ComposerPrefillRequest?> = _composerPrefillRequest.asStateFlow()

    fun queueComposerPrefill(threadKey: ThreadKey, text: String) {
        _composerPrefillRequest.value = ComposerPrefillRequest(
            requestId = nextComposerPrefillRequestId.incrementAndGet(),
            threadKey = threadKey,
            text = text,
        )
    }

    fun clearComposerPrefill(requestId: Long) {
        if (_composerPrefillRequest.value?.requestId == requestId) {
            _composerPrefillRequest.value = null
        }
    }

    // --- Live composer drafts (per-thread typed-but-unsent text + attachment)

    private val _composerDrafts = MutableStateFlow<Map<ThreadKey, ComposerDraft>>(emptyMap())
    val composerDrafts: StateFlow<Map<ThreadKey, ComposerDraft>> = _composerDrafts.asStateFlow()

    fun composerDraft(threadKey: ThreadKey): ComposerDraft =
        _composerDrafts.value[threadKey] ?: ComposerDraft.EMPTY

    fun setComposerDraft(threadKey: ThreadKey, draft: ComposerDraft) {
        _composerDrafts.update { current ->
            if (draft.isEmpty) {
                if (threadKey in current) current - threadKey else current
            } else {
                current + (threadKey to draft)
            }
        }
    }

    fun updateComposerDraft(threadKey: ThreadKey, transform: (ComposerDraft) -> ComposerDraft) {
        setComposerDraft(threadKey, transform(composerDraft(threadKey)))
    }

    fun clearComposerDraft(threadKey: ThreadKey) {
        setComposerDraft(threadKey, ComposerDraft.EMPTY)
    }

    // --- Thinking-indicator minigame -----------------------------------------

    internal val _minigameOverlay = MutableStateFlow<MinigameOverlayState>(MinigameOverlayState.Idle)
    val minigameOverlay: StateFlow<MinigameOverlayState> = _minigameOverlay.asStateFlow()
    internal var minigameJob: Job? = null

    fun requestMinigame(
        parentThreadId: String,
        serverId: String,
        lastUserMessage: String?,
        lastAssistantMessage: String?,
    ) = requestMinigameImpl(parentThreadId, serverId, lastUserMessage, lastAssistantMessage)

    fun dismissMinigame() = dismissMinigameImpl()

    // --- Subscription lifecycle ----------------------------------------------

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var subscriptionJob: Job? = null
    private val lifecycleLock = Any()
    private var activeClients: Int = 0

    fun start() {
        val shouldStart = synchronized(lifecycleLock) {
            activeClients += 1
            subscriptionJob?.isActive != true
        }
        if (!shouldStart) return
        subscriptionJob = scope.launch {
            try {
                val subscription: AppStoreSubscription = store.subscribeUpdates()
                refreshSnapshot()
                while (true) {
                    try {
                        val update: AppStoreUpdateRecord = subscription.nextUpdate()
                        handleUpdate(update)
                    } catch (e: Exception) {
                        LLog.e("AppModel", "AppStore subscription loop failed", e)
                        throw e
                    }
                }
            } catch (e: Exception) {
                LLog.e("AppModel", "AppModel.start() subscription failed", e)
                _lastError.value = e.message
            }
        }
    }

    fun stop() {
        val shouldStop = synchronized(lifecycleLock) {
            activeClients = (activeClients - 1).coerceAtLeast(0)
            activeClients == 0
        }
        if (!shouldStop) return
        subscriptionJob?.cancel()
        subscriptionJob = null
        pendingActiveThreadHydrationJob?.cancel()
        pendingActiveThreadHydrationJob = null
        pendingActiveThreadHydrationKey = null
    }

    // --- Snapshot refresh -----------------------------------------------------

    suspend fun refreshSnapshot() {
        try {
            val snap = store.snapshot()
            applySnapshot(snap)
            val serverSummary = snap.servers.joinToString(separator = " | ") { server ->
                "${server.serverId}:${server.displayName}:${server.host}:${server.port}:${server.health}"
            }
            LLog.d(
                "AppModel",
                "snapshot refreshed",
                fields = mapOf("servers" to snap.servers.size, "summary" to serverSummary),
            )
        } catch (e: Exception) {
            _lastError.value = e.message
        }
    }

    suspend fun restartLocalServer() = restartLocalServerImpl()

    suspend fun refreshSessions(serverIds: Collection<String>? = null) = refreshSessionsImpl(serverIds)

    suspend fun refreshThreadSearchSessions(
        query: String,
        runtimeKind: AgentRuntimeKind?,
        forceRepair: Boolean,
    ) = refreshThreadSearchSessionsImpl(query, runtimeKind, forceRepair)

    suspend fun loadConversationMetadataIfNeeded(serverId: String) =
        loadConversationMetadataIfNeededImpl(serverId)

    suspend fun loadAvailableModelsIfNeeded(serverId: String) = loadAvailableModelsIfNeededImpl(serverId)

    suspend fun loadRateLimitsIfNeeded(serverId: String) = loadRateLimitsIfNeededImpl(serverId)

    suspend fun restoreStoredLocalAuthState(serverId: String) = restoreStoredLocalAuthStateImpl(serverId)

    suspend fun ensureLocalAuthForThreadStart(serverId: String): Boolean =
        ensureLocalAuthForThreadStartImpl(serverId)

    suspend fun restoreStoredLocalChatGptAuth(serverId: String): Boolean =
        restoreStoredLocalChatGptAuthImpl(serverId)

    suspend fun hydrateThreadPermissions(key: ThreadKey): ThreadKey? = hydrateThreadPermissionsImpl(key)

    fun activateThread(key: ThreadKey?) {
        restoreCachedThreadSnapshotIfNeeded(key)
        updateActiveThread(key)
        store.setActiveThread(key)
        scheduleDeferredActiveThreadHydrationIfNeeded(key)
    }

    suspend fun startThread(
        serverId: String,
        params: AppStartThreadRequest,
    ): ThreadKey = startThreadImpl(serverId, params)

    suspend fun startTurn(
        key: ThreadKey,
        payload: AppComposerPayload,
    ) = startTurnImpl(key, payload)

    suspend fun externalResumeThread(
        key: ThreadKey,
        hostId: String? = null,
    ) = externalResumeThreadImpl(key, hostId)

    /**
     * Force a fresh `thread/resume` (with `excludeTurns = false`) so the
     * store reconciles `active_turn_id` against the server's authoritative
     * turn list. Use after a long resume / push wake — the in-flight turn
     * the local snapshot shows as running may have completed during the
     * background window with no `TurnCompleted` event delivered.
     */
    suspend fun forceRefreshThreadAuthoritative(key: ThreadKey) = forceRefreshThreadAuthoritativeImpl(key)

    suspend fun refreshThreadIncludingTurns(key: ThreadKey): ThreadKey = refreshThreadIncludingTurnsImpl(key)

    /**
     * Load the first page of turns for a thread. Intended to be called when
     * the conversation view appears for a thread whose
     * `initialTurnsLoaded == false`. Rust reconciles the page into the
     * store, and owns the fallback for servers that do not support paginated
     * turn loading.
     */
    internal val initialTurnsLoadingKeys: MutableSet<ThreadKey> = ConcurrentHashMap.newKeySet()
    internal val olderTurnsLoadingKeys: MutableSet<ThreadKey> = ConcurrentHashMap.newKeySet()

    /**
     * Launch an initial-turn load on the AppModel-owned scope so it survives
     * recomposition / LaunchedEffect key changes. The suspend body is not
     * cancelled mid-flight when the caller goes out of scope — RPC result +
     * store reconciliation always complete.
     */
    fun loadInitialTurns(key: ThreadKey, limit: UInt = INITIAL_TURN_PAGE_LIMIT) = loadInitialTurnsImpl(key, limit)

    fun loadInitialTurnsIfNeeded(key: ThreadKey, limit: UInt = INITIAL_TURN_PAGE_LIMIT) =
        loadInitialTurnsIfNeededImpl(key, limit)

    /**
     * Fetch the next older page using the thread's stored
     * `older_turns_cursor`. No-op when the cursor is null.
     *
     * Returns a [Job] so the caller can `join()` to drive UI state (e.g.
     * spinner on the "加载更早的消息" button).
     */
    fun loadOlderTurns(key: ThreadKey, limit: UInt = OLDER_TURN_PAGE_LIMIT): Job = loadOlderTurnsImpl(key, limit)

    suspend fun ensureThreadLoaded(
        key: ThreadKey,
        maxAttempts: Int = 5,
    ): ThreadKey? = ensureThreadLoadedImpl(key, maxAttempts)

    suspend fun refreshThreadSnapshot(key: ThreadKey) = refreshThreadSnapshotImpl(key)

    fun threadSnapshot(key: ThreadKey): AppThreadSnapshot? =
        _snapshot.value?.threads?.firstOrNull { it.key == key } ?: cachedThreadSnapshots[key]
}

private fun registerBundledCliTools() {
    val tools = emptyMap<String, String>()
    try {
        registerAndroidTools(tools)
        android.util.Log.i(
            "AppModel",
            "Registered ${tools.size} bundled CLI tools: ${tools.keys}",
        )
    } catch (e: Throwable) {
        android.util.Log.w("AppModel", "registerAndroidTools failed: ${e.message}")
    }
}
