import SwiftUI

struct ReplayDestinationScreen: View {
    @Environment(AppModel.self) private var appModel
    let recordingUrl: URL
    let bottomInset: CGFloat
    @State private var screenModel = ConversationScreenModel()
    @State private var replayThreadKey: ThreadKey?
    @State private var recorder = MessageRecorder.shared

    private var conversationThread: AppThreadSnapshot? {
        guard let key = replayThreadKey else { return nil }
        return appModel.threadSnapshot(for: key)
    }

    var body: some View {
        Group {
            if let thread = conversationThread, let key = replayThreadKey {
                @Bindable var bindableScreenModel = screenModel
                ConversationView(
                    thread: thread,
                    activeThreadKey: key,
                    transcript: screenModel.transcript,
                    followScrollToken: screenModel.followScrollToken,
                    pinnedContextItems: screenModel.pinnedContextItems,
                    composer: screenModel.composer,
                    composerInputText: $bindableScreenModel.composerInputText,
                    composerAttachedImage: $bindableScreenModel.composerAttachedImage,
                    topInset: 0,
                    bottomInset: bottomInset,
                    onOpenConversation: nil,
                    onResumeSessions: { _ in }
                )
                .onAppear { bindScreenModel(for: thread) }
                .onChange(of: thread) { _, t in bindScreenModel(for: t) }
                .onChange(of: appModel.snapshotRevision) { _, _ in
                    if let t = conversationThread { bindScreenModel(for: t) }
                }
            } else {
                VStack(spacing: 16) {
                    Spacer()
                    ProgressView()
                        .tint(AgentBuddyTheme.accent)
                    Text(recorder.isReplaying ? "Replaying..." : "Starting replay...")
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            }
        }
        .navigationTitle("Replay")
        .navigationBarTitleDisplayMode(.inline)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(.container, edges: .bottom)
        .task {
            let targetKey: ThreadKey
            if let server = appModel.snapshot?.servers.first {
                targetKey = ThreadKey(serverId: server.serverId, threadId: UUID().uuidString)
            } else {
                targetKey = ThreadKey(serverId: "replay", threadId: UUID().uuidString)
            }
            replayThreadKey = targetKey
            appModel.activateThread(targetKey)
            recorder.startReplay(url: recordingUrl, store: appModel.store, targetKey: targetKey)
        }
        .onDisappear {
            recorder.stopReplay()
        }
    }

    private func bindScreenModel(for thread: AppThreadSnapshot) {
        screenModel.bind(
            thread: thread,
            appModel: appModel,
            agentDirectoryVersion: appModel.snapshot?.agentDirectoryVersion ?? 0
        )
    }
}
