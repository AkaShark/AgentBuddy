import SwiftUI

struct ConversationInfoView: View {
    @Environment(AppModel.self) private var appModel
    @Environment(AppState.self) private var appState
    @Environment(ThemeManager.self) private var themeManager
    @Environment(\.dismiss) private var dismiss

    /// When nil, the screen shows server-only info (no session-specific sections).
    let threadKey: ThreadKey?
    /// Server ID used when threadKey is nil (server-only mode).
    let serverId: String?
    var onOpenWallpaper: (() -> Void)?
    var onOpenConversation: ((ThreadKey) -> Void)?
    var onOpenShell: (() -> Void)?

    /// Whether we're in server-only mode (no specific thread).
    private var isServerOnly: Bool { threadKey == nil }

    private var resolvedServerId: String? {
        threadKey?.serverId ?? serverId
    }

    @State var renameText = ""
    @State var isRenaming = false
    @State var stats: AppConversationStats?
    @State var serverUsage: AppServerUsageStats?
    @State var isShowingMountedFolders = false

    var thread: AppThreadSnapshot? {
        guard let threadKey else { return nil }
        return appModel.snapshot?.threads.first { $0.key == threadKey }
    }

    var server: AppServerSnapshot? {
        guard let sid = resolvedServerId else { return nil }
        return appModel.snapshot?.servers.first { $0.serverId == sid }
    }

    private var allServerThreads: [AppThreadSnapshot] {
        guard let snapshot = appModel.snapshot, let sid = resolvedServerId else { return [] }
        return snapshot.threads.filter { $0.key.serverId == sid }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if !isServerOnly {
                    // Hero header
                    heroSection
                        .padding(.bottom, 20)

                    // Action buttons row (Telegram-style)
                    actionButtonsRow
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)

                    // Thin divider
                    Rectangle()
                        .fill(AgentBuddyTheme.separator.opacity(0.4))
                        .frame(height: 0.5)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 20)
                }

                // Content sections
                VStack(spacing: 16) {
                    if isServerOnly {
                        // Server-only mode: just wallpaper button at top
                        serverOnlyActionRow
                            .padding(.top, 8)
                    }
                    if !isServerOnly {
                        contextWindowSection
                        conversationStatsSection
                    }
                    serverChartsSection
                    serverInfoSection
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 40)
            }
        }
        .background(AgentBuddyTheme.backgroundGradient)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(isServerOnly ? "Server Info" : "Info")
                    .agentBuddyFont(size: 16, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
            }
        }
        .onAppear { computeData() }
        .onChange(of: thread?.hydratedConversationItems.count) { computeData() }
        .alert("Rename Thread", isPresented: $isRenaming) {
            TextField("Thread name", text: $renameText)
            Button("Save") { saveRename() }
            Button("Cancel", role: .cancel) { }
        }
        .sheet(isPresented: $isShowingMountedFolders) {
            MountedFoldersView()
        }
    }

    // (Actions are now in the hero section's actionButtonsRow)

    // MARK: - Actions

    func forkConversation() async {
        guard let threadKey else { return }
        do {
            let sourceKey = await appModel.hydrateThreadPermissions(for: threadKey, appState: appState)
                ?? threadKey
            let newKey = try await appModel.client.forkThread(
                serverId: sourceKey.serverId,
                params: AppThreadLaunchConfig(
                    model: thread?.model,
                    approvalPolicy: appState.launchApprovalPolicy(for: sourceKey),
                    sandbox: appState.launchSandboxMode(for: sourceKey),
                    developerInstructions: nil,
                    persistExtendedHistory: true
                ).threadForkRequest(threadId: sourceKey.threadId, cwdOverride: thread?.info.cwd)
            )
            appModel.store.setActiveThread(key: newKey)
            await appModel.refreshThreadSnapshot(key: newKey)
            onOpenConversation?(newKey)
        } catch {
            LLog.error("info", "failed to fork thread", error: error)
        }
    }

    private func saveRename() {
        guard let threadKey else { return }
        let title = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }
        isRenaming = false
        Task {
            do {
                try await appModel.renameThread(
                    serverId: threadKey.serverId,
                    threadId: threadKey.threadId,
                    title: title
                )
            } catch {
                LLog.error("info", "failed to rename thread", error: error)
            }
        }
    }

    private func computeData() {
        if let thread {
            stats = thread.stats
        }
        if let server {
            serverUsage = server.usageStats
        }
    }
}
