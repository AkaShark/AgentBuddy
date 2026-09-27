import SwiftUI
import PhotosUI
import UIKit
import UserNotifications
import os
import HairballUI

let conversationViewSignpostLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "com.akashark.agentbuddy.ios",
    category: "ConversationView"
)

struct ConversationView: View {
    @Environment(AppState.self) var appState
    @Environment(AppModel.self) var appModel
    @Environment(\.scenePhase) private var scenePhase
    let thread: AppThreadSnapshot
    let activeThreadKey: ThreadKey
    let transcript: ConversationTranscriptSnapshot
    let followScrollToken: Int
    let pinnedContextItems: [ConversationItem]
    let composer: ConversationComposerSnapshot
    @Binding var composerInputText: String
    @Binding var composerAttachedImage: UIImage?
    var topInset: CGFloat = 0
    var bottomInset: CGFloat = 0
    var onOpenConversation: ((ThreadKey) -> Void)? = nil
    var onResumeSessions: ((String) -> Void)? = nil
    var minigameOverlay: MinigameOverlayState = .idle
    var onTypingTap: (() -> Void)? = nil
    var onMinigameDismiss: (() -> Void)? = nil
    var onMinigameRetry: (() -> Void)? = nil
    @AppStorage("workDir") var workDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? "/"
    @AppStorage("conversationTextSizeStep") private var conversationTextSizeStep = ConversationTextSize.large.rawValue
    @AppStorage("fastMode") var fastMode = false
    @State var messageActionError: String?
    @State private var hasLoggedFirstRender = false
    @State var localSendScrollToken = 0
    @State var showsUnsupportedHostHint = false

    var items: [ConversationItem] {
        transcript.items
    }

    private var threadStatus: ConversationStatus {
        transcript.threadStatus
    }

    private var agentDirectoryVersion: UInt64 {
        transcript.agentDirectoryVersion
    }

    var pendingModelOverride: String? {
        let trimmed = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var pendingAgentRuntimeKindOverride: AgentRuntimeKind? {
        pendingModelOverride == nil ? nil : appState.selectedAgentRuntimeKind
    }

    var pendingReasoningOverride: String? {
        if thread.ampReasoningEffortLocked {
            return nil
        }
        let trimmed = appState.reasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private var supportsTurnPagination: Bool {
        appModel.snapshot?
            .serverSnapshot(for: activeThreadKey.serverId)?
            .capabilities
            .supportsTurnPagination ?? false
    }

    var body: some View {
        ConversationMessageList(
            items: items,
            threadStatus: threadStatus,
            threadHasServerData: thread.hasPreviewOrTitle,
            transcriptRenderDigest: transcript.renderDigest,
            followScrollToken: followScrollToken,
            sendScrollToken: localSendScrollToken,
            activeThreadKey: activeThreadKey,
            agentDirectoryVersion: agentDirectoryVersion,
            topInset: thread.isSubagent ? topInset + 32 : topInset,
            olderTurnsCursor: thread.olderTurnsCursor,
            initialTurnsLoaded: thread.initialTurnsLoaded || !supportsTurnPagination,
            textSizeStep: $conversationTextSizeStep,
            resolveTargetLabel: resolveTargetLabel,
            onWidgetPrompt: sendWidgetPrompt,
            onEditUserItem: editMessage,
            onForkFromUserItem: forkFromMessage,
            onOpenConversation: onOpenConversation,
            onLoadOlderTurns: { key in
                Task { await appModel.loadOlderTurns(threadId: key) }
            }
        )
        .buddyHardTopScrollEdge()
        .overlay(alignment: .bottomLeading) {
            if let onTypingTap,
               minigameOverlay == .idle,
               ExperimentalFeatures.shared.isEnabled(.thinkingMinigame) {
                MinigameLaunchButton(action: onTypingTap)
                    .padding(.leading, 12)
                    .padding(.bottom, 8)
                    .transition(.scale.combined(with: .opacity))
            }
        }
        .activeThreadKey(activeThreadKey)
        .background { ChatWallpaperBackground(threadKey: activeThreadKey) }
        .overlay(alignment: .top) {
            if thread.isSubagent {
                SubagentBreadcrumbBar(
                    thread: thread,
                    topInset: topInset,
                    onNavigateToParent: {
                        if let parentId = thread.info.parentThreadId {
                            onOpenConversation?(ThreadKey(serverId: thread.serverId, threadId: parentId))
                        }
                    }
                )
            }
        }
        .overlay(alignment: .topLeading) {
            if DebugSettings.shared.enabled {
                ConversationDebugButton(topInset: topInset, activeThreadKey: activeThreadKey)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if minigameOverlay == .idle {
                VStack(spacing: 0) {
                    if showsUnsupportedHostHint {
                        UnsupportedHostPushHintView(onDismiss: dismissUnsupportedHostHint)
                            .padding(.horizontal, 12)
                            .padding(.bottom, 6)
                    }
                    ConversationBottomChrome(
                        pinnedContextItems: pinnedContextItems,
                        composer: composer,
                        composerInputText: $composerInputText,
                        composerAttachedImage: $composerAttachedImage,
                        onSend: sendMessage,
                        onFileSearch: searchComposerFiles,
                        bottomInset: bottomInset,
                        onOpenConversation: onOpenConversation,
                        onResumeSessions: onResumeSessions
                    )
                }
            } else {
                MinigameOverlayView(
                    state: minigameOverlay,
                    onClose: { onMinigameDismiss?() },
                    onRetry: { onMinigameRetry?() }
                )
                .frame(height: UIScreen.main.bounds.height * 0.4)
                .padding(.horizontal, 8)
                .padding(.bottom, max(bottomInset, 8))
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .alert("Conversation Action Error", isPresented: Binding(
            get: { messageActionError != nil },
            set: { if !$0 { messageActionError = nil } }
        )) {
            Button("OK", role: .cancel) { messageActionError = nil }
        } message: {
            Text(messageActionError ?? "Unknown error")
        }
        .onAppear {
            guard !hasLoggedFirstRender else { return }
            hasLoggedFirstRender = true
            os_signpost(.event, log: conversationViewSignpostLog, name: "ConversationFirstRender")
            appState.hydratePermissions(from: thread)
        }
        .onChange(of: thread) { _, newThread in
            appState.hydratePermissions(from: newThread)
        }
        .task(id: activeThreadKey) {
            await loadInitialTurnsIfNeeded()
        }
        .onChange(of: thread.initialTurnsLoaded) { _, _ in
            Task { await loadInitialTurnsIfNeeded() }
        }
        .task(id: UnsupportedHostHintProbe(
            serverId: activeThreadKey.serverId,
            health: appModel.snapshot?.serverSnapshot(for: activeThreadKey.serverId)?.health,
            isActive: scenePhase == .active
        )) {
            await refreshUnsupportedHostHint()
        }
    }
}

private extension AppThreadSnapshot {
    var serverId: String { key.serverId }
    var isSubagent: Bool {
        info.parentThreadId?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
            && ((info.agentNickname?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false)
                || (info.agentRole?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false))
    }
}

private struct UnsupportedHostHintProbe: Equatable {
    let serverId: String
    let health: AppServerHealth?
    let isActive: Bool
}

private struct MinigameLaunchButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "gamecontroller.fill")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.accent)
                .frame(width: 36, height: 36)
                .background(
                    Circle()
                        .fill(AgentBuddyTheme.surface.opacity(0.9))
                        .overlay(
                            Circle()
                                .stroke(AgentBuddyTheme.accent.opacity(0.3), lineWidth: 0.5)
                        )
                )
                .shadow(color: Color.black.opacity(0.15), radius: 4, x: 0, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Play a minigame while waiting")
    }
}

#if DEBUG
#Preview("Conversation") {
    AgentBuddyPreviewScene(appModel: AgentBuddyPreviewData.makeConversationAppModel(messages: AgentBuddyPreviewData.longConversation)) {
        ContentView()
    }
}
#endif
