import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class AppModel {
    struct PendingThreadStateEvent: Sendable {
        let state: AppThreadStateRecord
        let sessionSummary: AppSessionSummary
        let agentDirectoryVersion: UInt64
    }

    struct PendingCommandRowMutation: Sendable {
        let key: ThreadKey
        let itemId: String
        var upsertItem: HydratedConversationItem?
    }

    /// Pre-built Rust objects initialized off the main thread to avoid
    /// priority inversion (tokio runtime init blocks at default QoS).
    private struct RustBridges: @unchecked Sendable {
        let store: AppStore
        let client: AppClient
        let discovery: DiscoveryBridge
        let serverBridge: ServerBridge
        let ssh: SshBridge
        let reconnectController: ReconnectController
    }

    /// Kick off Rust bridge construction on a background thread.
    /// Call from `AppDelegate.didFinishLaunching` before SwiftUI touches `shared`.
    nonisolated static func prewarmRustBridges() {
        _ = _prewarmResult
    }

    private nonisolated static let _prewarmResult: RustBridges = {
        // Boot the iSH kernel BEFORE any Rust bridge construction so the exec
        // hook is wired up before the first command can be issued. Idempotent
        // — the AppDelegate call site is a no-op on second invocation.
        AgentBuddyPlatform.bootstrapLocalRuntimeIfNeeded()

        // Register host-key pinning before any SSH connect or reconnect runs.
        SshHostKeyTrust.register()

        let rc = ReconnectController()
        rc.setCredentialProvider(provider: SwiftSshCredentialProvider())
        rc.setSlingshotCredentialProvider(provider: SwiftSlingshotCredentialProvider())
        rc.setMultiClankerAndQuicEnabled(enabled: true)
        return RustBridges(
            store: AppStore(),
            client: AppClient(),
            discovery: DiscoveryBridge(),
            serverBridge: ServerBridge(),
            ssh: SshBridge(),
            reconnectController: rc
        )
    }()

    static let shared = AppModel()

    struct ComposerPrefillRequest: Identifiable, Equatable {
        let id = UUID()
        let threadKey: ThreadKey
        let text: String
    }

    let store: AppStore
    let client: AppClient
    let discovery: DiscoveryBridge
    let serverBridge: ServerBridge
    let ssh: SshBridge
    let reconnectController: ReconnectController

    var snapshot: AppSnapshotRecord? {
        didSet {
            guard oldValue != snapshot else { return }
            snapshotRevision &+= 1
        }
    }
    private(set) var snapshotRevision: UInt64 = 0
    var lastError: String?
    private(set) var composerPrefillRequest: ComposerPrefillRequest?

    @ObservationIgnored private var subscription: AppStoreSubscription?
    @ObservationIgnored private var updateTask: Task<Void, Never>?
    @ObservationIgnored var loadingModelServerIds: Set<String> = []
    @ObservationIgnored var loadingRateLimitServerIds: Set<String> = []
    @ObservationIgnored var recentConversationMetadataLoads: [String: Date] = [:]
    @ObservationIgnored var pendingThreadRefreshKeys: Set<ThreadKey> = []
    @ObservationIgnored var pendingThreadRefreshTask: Task<Void, Never>?
    @ObservationIgnored var pendingActiveThreadHydrationKey: ThreadKey?
    @ObservationIgnored var pendingActiveThreadHydrationTask: Task<Void, Never>?
    @ObservationIgnored private var pendingSnapshotRefreshTask: Task<Void, Never>?
    @ObservationIgnored var pendingThreadStateEvents: [ThreadKey: PendingThreadStateEvent] = [:]
    @ObservationIgnored var pendingThreadStateTask: Task<Void, Never>?
    @ObservationIgnored var pendingCommandRowMutations: [String: PendingCommandRowMutation] = [:]
    @ObservationIgnored var pendingCommandRowMutationTask: Task<Void, Never>?
    @ObservationIgnored var cachedThreadSnapshots: [ThreadKey: AppThreadSnapshot] = [:]
    @ObservationIgnored var loadingTurnPageThreadKeys: Set<ThreadKey> = []

    init(
        store: AppStore? = nil,
        client: AppClient? = nil,
        discovery: DiscoveryBridge? = nil,
        serverBridge: ServerBridge? = nil,
        ssh: SshBridge? = nil,
        reconnectController: ReconnectController? = nil
    ) {
        let bridges = Self._prewarmResult
        self.store = store ?? bridges.store
        self.client = client ?? bridges.client
        self.discovery = discovery ?? bridges.discovery
        self.serverBridge = serverBridge ?? bridges.serverBridge
        self.ssh = ssh ?? bridges.ssh
        self.reconnectController = reconnectController ?? bridges.reconnectController

        // Register the saved-apps directory with the Rust client so the
        // dynamic-tool finalize hook can auto-upsert on `show_widget` calls.
        // Without this, auto-save silently no-ops.
        self.client.setSavedAppsDirectory(directory: SavedAppsDirectory.path)
        self.client.setSlingshotCredentialsDirectory(directory: MobilePreferencesDirectory.path)

        // Route Swift presentation lookups through the Rust-owned
        // `AgentMetadataStore`. Any view rendering an agent label /
        // icon / capability flag goes through this single shared
        // client, so a probe response in one screen surfaces metadata
        // everywhere.
        let metadataClient = self.client
        AgentRuntimeMetadataProvider.lookup = { [weak metadataClient] name in
            metadataClient?.agentMetadata(name: name)
        }
        AgentRuntimeMetadataProvider.all = { [weak metadataClient] in
            metadataClient?.allAgentMetadata() ?? []
        }
    }

    deinit {
        updateTask?.cancel()
        pendingThreadRefreshTask?.cancel()
        pendingActiveThreadHydrationTask?.cancel()
        pendingSnapshotRefreshTask?.cancel()
        pendingThreadStateTask?.cancel()
        pendingCommandRowMutationTask?.cancel()
    }

    func start() {
        guard updateTask == nil else { return }
        let subscription = store.subscribeUpdates()
        self.subscription = subscription
        updateTask = Task.detached(priority: .userInitiated) { [weak self, subscription] in
            guard let self else { return }
            await self.refreshSnapshot()
            while !Task.isCancelled {
                do {
                    let update = try await subscription.nextUpdate()
                    await self.handleStoreUpdate(update)
                } catch {
                    if Task.isCancelled { break }
                    await self.recordStoreSubscriptionError(error)
                    break
                }
            }
        }
    }

    func stop() {
        updateTask?.cancel()
        updateTask = nil
        pendingThreadRefreshTask?.cancel()
        pendingThreadRefreshTask = nil
        pendingThreadRefreshKeys.removeAll()
        pendingActiveThreadHydrationTask?.cancel()
        pendingActiveThreadHydrationTask = nil
        pendingActiveThreadHydrationKey = nil
        pendingSnapshotRefreshTask?.cancel()
        pendingSnapshotRefreshTask = nil
        pendingThreadStateTask?.cancel()
        pendingThreadStateTask = nil
        pendingThreadStateEvents.removeAll()
        pendingCommandRowMutationTask?.cancel()
        pendingCommandRowMutationTask = nil
        pendingCommandRowMutations.removeAll()
        subscription = nil
    }

    func refreshSnapshot() async {
        pendingSnapshotRefreshTask?.cancel()
        pendingSnapshotRefreshTask = nil
        await performSnapshotRefresh()
    }

    private func performSnapshotRefresh() async {
        do {
            applySnapshot(try await store.snapshot())
        } catch {
            lastError = error.localizedDescription
        }
    }

    private func recordStoreSubscriptionError(_ error: Error) {
        lastError = error.localizedDescription
    }

    func scheduleSnapshotRefreshDebounced() {
        guard pendingSnapshotRefreshTask == nil else { return }
        pendingSnapshotRefreshTask = Task { [weak self] in
            do {
                try await Task.sleep(nanoseconds: 75_000_000)
            } catch {
                return
            }
            guard let self else { return }
            self.pendingSnapshotRefreshTask = nil
            await self.performSnapshotRefresh()
        }
    }

    func queueComposerPrefill(threadKey: ThreadKey, text: String) {
        composerPrefillRequest = ComposerPrefillRequest(threadKey: threadKey, text: text)
    }

    func clearComposerPrefill(id: UUID) {
        guard composerPrefillRequest?.id == id else { return }
        composerPrefillRequest = nil
    }
}
