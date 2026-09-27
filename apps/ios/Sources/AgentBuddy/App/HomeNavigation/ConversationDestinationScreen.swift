import SwiftUI
import os

private let conversationRouteSignpostLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "com.akashark.agentbuddy.ios",
    category: "ConversationRoute"
)

struct ConversationDestinationScreen: View {
    @Environment(AppModel.self) private var appModel
    @Environment(AppState.self) private var appState
    @AppStorage("workDir") private var workDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? "/"
    @State private var screenModel = ConversationScreenModel()
    let threadKey: ThreadKey
    let bottomInset: CGFloat
    let onResumeSessions: (String) -> Void
    let onOpenConversation: (ThreadKey) -> Void
    var onInfo: (() -> Void)?

    private var conversationThread: AppThreadSnapshot? {
        appModel.threadSnapshot(for: threadKey)
    }

    private var resolvedThreadKey: ThreadKey {
        conversationThread?.key ?? threadKey
    }

    private var pendingUserInputsForThread: [PendingUserInputRequest] {
        guard let snapshot = appModel.snapshot else { return [] }
        let key = resolvedThreadKey
        return snapshot.pendingUserInputs.filter {
            $0.isRelevant(to: key)
        }
    }

    private var relevantServerSnapshot: AppServerSnapshot? {
        appModel.snapshot?.serverSnapshot(for: resolvedThreadKey.serverId)
    }

    private func bindScreenModel(for thread: AppThreadSnapshot) {
        screenModel.bind(
            thread: thread,
            appModel: appModel,
            agentDirectoryVersion: appModel.snapshot?.agentDirectoryVersion ?? 0
        )
    }

    private var navigationTitle: String {
        conversationThread?.displayTitle ?? "Conversation"
    }

    var body: some View {
        Group {
            if let conversationThread {
                @Bindable var bindableScreenModel = screenModel
                ConversationView(
                    thread: conversationThread,
                    activeThreadKey: resolvedThreadKey,
                    transcript: screenModel.transcript,
                    followScrollToken: screenModel.followScrollToken,
                    pinnedContextItems: screenModel.pinnedContextItems,
                    composer: screenModel.composer,
                    composerInputText: $bindableScreenModel.composerInputText,
                    composerAttachedImage: $bindableScreenModel.composerAttachedImage,
                    topInset: 0,
                    bottomInset: bottomInset,
                    onOpenConversation: onOpenConversation,
                    onResumeSessions: onResumeSessions,
                    minigameOverlay: screenModel.minigameOverlay,
                    onTypingTap: { screenModel.requestMinigame() },
                    onMinigameDismiss: { screenModel.dismissMinigame() },
                    onMinigameRetry: {
                        screenModel.dismissMinigame()
                        screenModel.requestMinigame()
                    }
                )
                .onAppear {
                    bindScreenModel(for: conversationThread)
                }
                .onChange(of: conversationThread) { _, updatedThread in
                    bindScreenModel(for: updatedThread)
                }
                .onChange(of: appModel.snapshotRevision) { _, _ in
                    bindScreenModel(for: conversationThread)
                }
                .onChange(of: pendingUserInputsForThread) { _, _ in
                    bindScreenModel(for: conversationThread)
                }
                .onChange(of: relevantServerSnapshot) { _, _ in
                    bindScreenModel(for: conversationThread)
                }
                .onChange(of: appModel.composerPrefillRequest) { _, _ in
                    bindScreenModel(for: conversationThread)
                }
            } else {
                VStack(spacing: 16) {
                    Spacer()
                    ProgressView()
                        .tint(AgentBuddyTheme.accent)
                    Text("Loading thread...")
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let conversationThread {
                ToolbarItem(placement: .principal) {
                    HeaderView(thread: conversationThread)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    ConversationToolbarControls(
                        thread: conversationThread,
                        control: .menu,
                        onInfo: onInfo
                    )
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(.container, edges: .bottom)
        .task(id: threadKey) {
            os_signpost(
                .event,
                log: conversationRouteSignpostLog,
                name: "ThreadOpenStarted",
                "server=%{public}@ thread=%{public}@",
                threadKey.serverId,
                threadKey.threadId
            )
            appModel.activateThread(threadKey)
            if appModel.threadSnapshot(for: threadKey) == nil {
                _ = await appModel.ensureThreadLoaded(key: threadKey)
            }
            await appModel.loadConversationMetadataIfNeeded(serverId: threadKey.serverId)
            if let thread = conversationThread,
               let cwd = thread.info.cwd?.trimmingCharacters(in: .whitespacesAndNewlines),
               !cwd.isEmpty {
                workDir = cwd
                appState.currentCwd = cwd
            }
        }
    }
}
