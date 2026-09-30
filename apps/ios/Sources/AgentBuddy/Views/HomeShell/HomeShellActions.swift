import Foundation

/// Every user action the home shell can trigger, grouped in one value so the
/// tabs depend on a single narrow interface instead of dozens of loose
/// callbacks. `HomeNavigationView` builds it from its existing handlers; the
/// handlers keep owning navigation, Rust calls and error reporting.
struct HomeShellActions {
    // MARK: Tasks
    var openSession: @MainActor (HomeDashboardRecentSession) async -> Void
    var showAllTasks: (() -> Void)?
    var pinThread: (ThreadKey) -> Void
    var unpinThread: (ThreadKey) -> Void
    var hideThread: (ThreadKey) -> Void
    var deleteThread: (ThreadKey) async -> Void
    var sendQuickReply: @MainActor (ThreadKey, String) async -> Void
    var cancelThread: @MainActor (ThreadKey) async -> Void
    var forkThread: @MainActor (HomeDashboardRecentSession) async -> Void
    var hydrateThread: (ThreadKey, Bool) async -> Void
    var searchThreads: @Sendable (_ query: String, _ runtimeKind: AgentRuntimeKind?, _ serverId: String?, _ forceRepair: Bool) async -> Void

    // MARK: New task
    /// Opens the new-task composer. A non-nil project is selected first.
    var newTask: (AppProject?) -> Void
    var startVoice: (() -> Void)?

    // MARK: Projects
    var selectProject: (AppProject) -> Void
    var createProject: () -> Void
    /// Opens the All tasks screen limited to one project.
    var showProjectTasks: (AppProject) -> Void

    // MARK: Hosts
    /// Scopes the task list to a host, or reconnects it when disconnected.
    var selectServer: (HomeDashboardServer) -> Void
    var clearServerScope: () -> Void
    var addServer: () -> Void
    var pairWithQRCode: () -> Void
    var reconnectServer: (HomeDashboardServer) -> Void
    var restartAppServer: (HomeDashboardServer) -> Void
    var renameServer: (_ serverId: String, _ name: String) -> Void
    var removeServer: (_ serverId: String) -> Void

    // MARK: App
    var showSettings: () -> Void
    var showApps: (() -> Void)?
    var showTerminal: (() -> Void)?
}
