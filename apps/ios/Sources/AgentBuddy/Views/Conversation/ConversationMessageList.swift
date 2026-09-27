import SwiftUI

struct ConversationMessageList: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let items: [ConversationItem]
    let threadStatus: ConversationStatus
    let threadHasServerData: Bool
    let transcriptRenderDigest: Int
    let followScrollToken: Int
    let sendScrollToken: Int
    let activeThreadKey: ThreadKey
    let agentDirectoryVersion: UInt64
    var topInset: CGFloat = 0
    let olderTurnsCursor: String?
    let initialTurnsLoaded: Bool
    @Binding var textSizeStep: Int
    let resolveTargetLabel: (String) -> String?
    let onWidgetPrompt: (String) -> Void
    let onEditUserItem: (ConversationItem) -> Void
    let onForkFromUserItem: (ConversationItem) -> Void
    var onOpenConversation: ((ThreadKey) -> Void)? = nil
    let onLoadOlderTurns: (ThreadKey) -> Void
    @State var isNearBottom = true
    @State var autoFollowStreaming = true
    @State var userIsDraggingScroll = false
    @State var distanceFromBottom: CGFloat = 0
    @State private var waitingForDataExpired = false
    @State var pinchBaseStep: Int?
    @State var pinchAppliedDelta = 0
    @State var transcriptTurns: [TranscriptTurn] = []
    @State var transcriptBuildKey: Int?
    @State var renderedTurns: [TranscriptTurn] = []
    @State var renderedTurnsBuildKey: Int?
    @State var expandedTurnIDs: Set<String> = []
    @State var pendingAnimatedTurns: [TranscriptTurn]?
    @State var turnInsertionAnimationInFlight = false
    @State var followLayoutScrollScheduled = false
    @State var initialBottomScrollThreadScopeID: String?
    @State var programmaticBottomScrollSettling = false
    @State var programmaticBottomScrollGeneration = 0
    @AppStorage("collapseTurns") var collapseTurns = false
    private static let latestButtonShowDistance: CGFloat = 48
    static let nearBottomRestoreDistance: CGFloat = 12
    static let bottomScrollSettleDuration: TimeInterval = 0.3
    static let bottomAnchorID = "conversation-message-list-bottom"
    private static let scrollCoordinateSpaceName = "conversation-message-list-scroll"

    var expandedRecentTurnCount: Int {
        return collapseTurns ? 1 : .max
    }

    private var sourceTurns: [TranscriptTurn] {
        if transcriptTurns.isEmpty {
            return TranscriptTurn.build(
                from: items,
                threadStatus: threadStatus,
                expandedRecentTurnCount: expandedRecentTurnCount
            )
        }
        return transcriptTurns
    }

    private var lastTurnIsUserOnly: Bool {
        guard let lastTurn = sourceTurns.last else { return false }
        return lastTurn.items.allSatisfy { $0.isUserItem }
    }

    private var isStreamingLastTurn: Bool {
        if case .thinking = threadStatus { return true }
        return sourceTurns.last?.isLive == true
    }

    private var messageActionsDisabled: Bool {
        if case .thinking = threadStatus { return true }
        return false
    }

    private var isWaitingForData: Bool {
        items.isEmpty && threadHasServerData && !waitingForDataExpired
    }

    private var shouldShowScrollToBottom: Bool {
        !items.isEmpty && distanceFromBottom > Self.latestButtonShowDistance
    }

    var activeThreadScopeID: String {
        "\(activeThreadKey.serverId)::\(activeThreadKey.threadId)"
    }

    var isStreaming: Bool {
        if case .thinking = threadStatus { return true }
        return false
    }

    private var hasOlderTurns: Bool {
        if let cursor = olderTurnsCursor { return !cursor.isEmpty }
        return false
    }

    private var mergedRenderableTurns: [TranscriptTurn] {
        let turns = sourceTurns
        let buildKey = makeRenderedTurnsBuildKey(for: turns)
        if renderedTurnsBuildKey == buildKey { return renderedTurns }
        return TranscriptTurn.mergeConsecutiveExplorationTurnsForRendering(turns)
    }

    var body: some View {
        let turns = mergedRenderableTurns
        let lastTurnID = turns.last?.id
        ScrollViewReader { proxy in
            GeometryReader { viewport in
            ZStack(alignment: .bottomTrailing) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            if !initialTurnsLoaded && hasOlderTurns && !turns.isEmpty {
                                ConversationLoadingIndicator(label: "Loading earlier messages...")
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                            } else if hasOlderTurns {
                                Button {
                                    onLoadOlderTurns(activeThreadKey)
                                } label: {
                                    Text("Load earlier messages")
                                        .buddyText(.label, weight: .semibold)
                                        .foregroundStyle(AgentBuddyTheme.link)
                                        .frame(maxWidth: .infinity, minHeight: BuddySize.minHitTarget)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                            ForEach(turns) { turn in
                                let isLastTurn = turn.id == lastTurnID
                                ConversationTurnRow(
                                    turn: turn,
                                    isExpanded: isTurnExpanded(turn),
                                    canCollapse: turn.isCollapsedByDefault,
                                    isLastTurn: isLastTurn,
                                    viewportHeight: viewport.size.height,
                                    showTypingIndicator: isLastTurn && {
                                        if case .thinking = threadStatus { return true }
                                        return false
                                    }(),
                                    serverId: activeThreadKey.serverId,
                                    originThreadId: activeThreadKey.threadId,
                                    agentDirectoryVersion: agentDirectoryVersion,
                                    messageActionsDisabled: messageActionsDisabled,
                                    onToggleExpansion: {
                                        toggleTurnExpansion(turn)
                                    },
                                    onStreamingSnapshotRendered: {
                                        requestFollowScrollAfterLayout(proxy)
                                    },
                                    onLiveContentLayoutChanged: {
                                        requestFollowScrollAfterLayout(proxy)
                                    },
                                    resolveTargetLabel: resolveTargetLabel,
                                    onWidgetPrompt: onWidgetPrompt,
                                    onEditUserItem: onEditUserItem,
                                    onForkFromUserItem: onForkFromUserItem,
                                    onOpenConversation: onOpenConversation
                                )
                                .equatable()
                                .turnDebugOverlay(turnId: turn.id)
                            }
                        }
                        .frame(maxWidth: AgentBuddyPlatform.isRegularSurface(horizontalSizeClass: horizontalSizeClass) ? 760 : .infinity)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.horizontal, 16)
                        .padding(.top, topInset + 56)
                        .animation(.spring(response: 0.22, dampingFraction: 0.9), value: textSizeStep)

                        if isWaitingForData {
                            ConversationLoadingIndicator(label: "Loading conversation...")
                                .frame(maxWidth: .infinity)
                                .padding(.top, 40)
                        }

                        Color.clear
                            .frame(height: 1)
                            .id(Self.bottomAnchorID)
                            .padding(.horizontal, 16)
                    }
                    .frame(maxWidth: .infinity, minHeight: viewport.size.height, alignment: .top)
                }
                .id(activeThreadScopeID)
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
                .coordinateSpace(name: Self.scrollCoordinateSpaceName)
                .onScrollGeometryChange(for: CGFloat.self) { geometry in
                    max(0, geometry.contentSize.height - geometry.visibleRect.maxY)
                } action: { _, distance in
                    updateDistanceFromBottom(distance)
                }
                // Keep the chat initially bottom-aligned, but don't let keyboard-driven
                // viewport size changes force a fresh bottom jump with stale lazy heights.
                .defaultScrollAnchor(.bottom, for: .initialOffset)
                .simultaneousGesture(
                    MagnificationGesture(minimumScaleDelta: 0.03)
                        .onChanged { scale in handlePinchChanged(scale: scale) }
                        .onEnded { scale in finishPinch(scale: scale) }
                )
                .onScrollPhaseChange { _, newPhase in
                    switch newPhase {
                    case .tracking, .interacting:
                        programmaticBottomScrollSettling = false
                        userIsDraggingScroll = true
                        if isStreaming { autoFollowStreaming = false }
                    case .decelerating:
                        userIsDraggingScroll = true
                    default:
                        userIsDraggingScroll = false
                        if isNearBottom { autoFollowStreaming = true }
                    }
                }
                .onAppear {
                    autoFollowStreaming = true
                    syncTranscriptTurns()
                    requestInitialBottomScrollIfNeeded(proxy)
                }
                .onChange(of: activeThreadKey) {
                    autoFollowStreaming = true
                    isNearBottom = true
                    distanceFromBottom = 0
                    initialBottomScrollThreadScopeID = nil
                    waitingForDataExpired = false
                    syncTranscriptTurns(resetExpansion: true)
                    StreamingRendererCoordinator.shared.reset()
                    requestInitialBottomScrollIfNeeded(proxy)
                }
                .task(id: activeThreadKey) {
                    try? await Task.sleep(for: .seconds(1))
                    waitingForDataExpired = true
                }
                .onChange(of: items) { _, _ in
                    syncTranscriptTurns()
                    requestInitialBottomScrollIfNeeded(proxy)
                }
                .onChange(of: collapseTurns) {
                    syncTranscriptTurns(resetExpansion: true)
                }
                .onChange(of: followScrollToken) {
                    guard isStreaming, autoFollowStreaming, !userIsDraggingScroll else { return }
                    scrollToBottom(proxy)
                }
                .onChange(of: sendScrollToken) {
                    autoFollowStreaming = true
                    isNearBottom = true
                    distanceFromBottom = 0
                    scrollToBottom(proxy)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                        withAnimation(.interactiveSpring(response: 0.28, dampingFraction: 0.9)) {
                            scrollToBottom(proxy)
                        }
                    }
                }
                .onChange(of: threadStatus) { oldStatus, _ in
                    syncTranscriptTurns()
                    // When streaming ends, finish active renderers so they
                    // switch to static rendering (no re-animation on view rebuild).
                    let wasStreaming = { if case .thinking = oldStatus { return true }; return false }()
                    if wasStreaming && !isStreaming {
                        StreamingRendererCoordinator.shared.finishActive()
                    }
                    if wasStreaming && !isStreaming && autoFollowStreaming {
                        scrollToBottom(proxy)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                            scrollToBottom(proxy)
                        }
                    }
                }

                if shouldShowScrollToBottom {
                    ScrollToBottomIndicator {
                        autoFollowStreaming = true
                        isNearBottom = true
                        distanceFromBottom = 0
                        // Jump without animation first so LazyVStack realizes
                        // content near the bottom, then do an animated corrective
                        // scroll once layout has settled.  This avoids the
                        // overshoot caused by stale estimated heights.
                        scrollToBottom(proxy)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                            withAnimation(.interactiveSpring(response: 0.28, dampingFraction: 0.9)) {
                                scrollToBottom(proxy)
                            }
                        }
                    }
                    .padding(.trailing, 14)
                    .padding(.bottom, 10)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            }
        }
    }
}

/// "Latest" jump pill: surface capsule with a hairline border at compact-pill
/// height inside a 44pt hit area. No looping motion.
private struct ScrollToBottomIndicator: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: BuddySpacing.xs) {
                Image(systemName: "arrow.down")
                    .font(.system(size: 17, weight: .semibold))
                    .accessibilityHidden(true)
                Text("Latest")
                    .buddyText(.label, weight: .semibold)
            }
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .padding(.horizontal, BuddySpacing.md)
            .frame(minHeight: BuddySize.compactPill)
            .background(AgentBuddyTheme.surface, in: Capsule())
            .overlay {
                Capsule().strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
            }
            .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, x: 0, y: 8)
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
    }
}

/// Loading label in textSecondary. A soft shimmer runs only when Reduce
/// Motion is off.
private struct ConversationLoadingIndicator: View {
    let label: LocalizedStringKey
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shimmerOffset: CGFloat = -1

    var body: some View {
        if reduceMotion {
            text
                .foregroundStyle(AgentBuddyTheme.textSecondary)
        } else {
            text
                .foregroundStyle(
                    LinearGradient(
                        colors: [
                            AgentBuddyTheme.textSecondary.opacity(0.55),
                            AgentBuddyTheme.textSecondary,
                            AgentBuddyTheme.textSecondary.opacity(0.55),
                        ],
                        startPoint: UnitPoint(x: shimmerOffset - 0.3, y: 0.5),
                        endPoint: UnitPoint(x: shimmerOffset + 0.3, y: 0.5)
                    )
                )
                .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false), value: shimmerOffset)
                .onAppear {
                    shimmerOffset = 2
                }
        }
    }

    private var text: some View {
        Text(label)
            .buddyText(.label)
    }
}
