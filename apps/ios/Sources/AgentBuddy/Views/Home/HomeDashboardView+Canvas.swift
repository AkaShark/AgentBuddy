import SwiftUI
import UIKit

extension HomeDashboardView {
    var canvas: some View {
        ZStack {
            // When search is open, replace the list entirely so we're not
            // fighting two scroll containers. When it's closed, the overlay
            // branch returns nothing and can't intercept scroll gestures.
            if isSearchExpanded {
                ZStack(alignment: .top) {
                    AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
                    ThreadSearchResultsView(
                        sessions: searchSessions,
                        pinnedThreadKeys: Set(pinnedThreadKeys),
                        query: searchQuery,
                        runtimeKinds: availableSearchRuntimeKinds,
                        selectedRuntimeKind: $selectedSearchRuntimeKind,
                        isLoading: isLoadingThreadListing && searchSessions.isEmpty,
                        onRefresh: refreshSearchThreads,
                        onAdd: { session in
                            onPinThread(session.key)
                            withAnimation(.spring(response: 0.42, dampingFraction: 0.82)) {
                                inputMode = .collapsed
                            }
                            searchQuery = ""
                            selectedSearchRuntimeKind = nil
                        },
                        onRemove: { session in
                            onUnpinThread(session.key)
                        },
                        contentInsets: EdgeInsets(top: 48, leading: 0, bottom: chrome == .full ? 140 : 80, trailing: 0)
                    )
                }
                .transition(.opacity)
            } else {
                sessionsList
            }
        }
        .overlay(alignment: .top) { topChrome }
        .overlay(alignment: .bottom) {
            switch chrome {
            case .full:
                bottomChrome
            case .sidebar:
                sidebarBottomChrome
            }
        }
        .overlay {
            if showOnboardingCoachmarks {
                emptyHomeFatCat
                    .transition(.opacity)
            }
        }
        .overlayPreferenceValue(CoachmarkAnchorKey.self) { anchors in
            if showOnboardingCoachmarks {
                OnboardingCoachmarksView(anchors: anchors)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: showOnboardingCoachmarks)
    }

    private func refreshSearchThreads() async {
        guard let onSearchThreads else { return }
        await MainActor.run { isLoadingThreadListing = true }
        await onSearchThreads(searchQuery, selectedSearchRuntimeKind, selectedMachineServerId, true)
        await MainActor.run { isLoadingThreadListing = false }
    }

    /// True whenever the visible session list is empty AND the user is in
    /// the default (collapsed) input mode — so the overlay doesn't fight the
    /// composer/search expansions, and disappears the moment a thread shows
    /// up in the current scope.
    private var showOnboardingCoachmarks: Bool {
        guard chrome == .full,
              inputMode == .collapsed,
              !isSearchExpanded else { return false }
        return visibleSessions.isEmpty
    }

    // Search results are rendered directly in `canvas` as an inline
    // replacement for the sessions list when `isSearchExpanded` is true.

    private var sessionsList: some View {
        // UIKit-backed scroll view owns pinch, pan, and row swipes
        // directly. Previously SwiftUI's `ScrollView` + `MagnifyGesture`
        // both consumed the same pan deltas, producing vertical jitter
        // during a pinch even with `.scrollDisabled(isPinching)`. The
        // UIKit host uses the Clear.app pattern: pinch anchored on the
        // finger midpoint in content coordinates + frame-only height
        // animation per row (SwiftUI does zero per-tick work during a
        // pinch).
        ZStack {
            if visibleSessions.isEmpty {
                ScrollView { emptyState.padding(.top, 48).padding(.bottom, 140) }
                    .scrollContentBackground(.hidden)
            } else {
                HomeSessionsScrollView(
                    sessions: visibleSessions,
                    pinnedThreadKeys: Set(pinnedThreadKeys),
                    hydratingKeys: hydratingKeys,
                    cancellingKeys: cancellingKeys,
                    openingKey: openingRecentSessionKey,
                    zoomLevel: $zoomLevel,
                    showCatFooter: chrome == .full,
                    topInset: 48,
                    bottomInset: chrome == .full ? 140 : 24,
                    callbacks: HomeSessionsScrollView.Callbacks(
                        onOpen: { session in
                            guard openingRecentSessionKey == nil else { return }
                            Task { await onOpenRecentSession(session) }
                        },
                        onReply: { session in replyTargetThread = session },
                        onHide: { key in onHideThread(key) },
                        onPin: { key in onPinThread(key) },
                        onUnpin: { key in onUnpinThread(key) },
                        onCancelTurn: { session in
                            cancellingKeys.insert(hydrationId(session.key))
                            Task { await onCancelThread?(session.key) }
                        },
                        onDelete: { session in deleteTargetThread = session },
                        onFork: { session in
                            Task { await onForkThread?(session) }
                        },
                        onShowPiP: { session in
                            StreamingPiPController.shared.start(for: session.key)
                        }
                    )
                )
                // Extend the scroll view edge-to-edge so content can
                // scroll under the semi-transparent top/bottom chrome.
                // The `topInset`/`bottomInset` we pass already carve
                // out safe resting space for the rows.
                .ignoresSafeArea()
            }
        }
    }

    /// The "no sessions yet" copy has been replaced by the coachmark
    /// overlay (mounted on `canvas` via `.overlayPreferenceValue`), which
    /// draws arrows from each label to the actual button positions. This
    /// branch just reserves vertical space for the scroll view.
    private var emptyState: some View {
        Color.clear.frame(height: 1)
    }

    /// Fat cat illustration shown on the empty home screen. Positioned in
    /// the middle vertical band — between the addServer label (y≈0.20) and
    /// the search/newThread labels (y≈0.62/0.70) — so it never collides
    /// with the coachmark arrows or labels. Plays the entrance APNG once
    /// then crossfades to the looping APNG, matching the cat footer.
    private var emptyHomeFatCat: some View {
        GeometryReader { proxy in
            let h = proxy.size.height
            let w = proxy.size.width
            let catWidth = min(max(180, w * 0.55), 260)
            let catHeight = catWidth * 202.0 / 360.0
            EmptyHomeFatCatView()
                .frame(width: catWidth, height: catHeight)
                .position(x: w / 2, y: h * 0.42)
        }
    }
}

private struct EmptyHomeFatCatView: View {
    @State private var showingLoop = false

    private let entranceURL = Bundle.main.url(forResource: "home_cat_entrance", withExtension: "png")
    private let loopURL = Bundle.main.url(forResource: "home_cat", withExtension: "png")

    var body: some View {
        if let imageURL = showingLoop ? loopURL : (entranceURL ?? loopURL) {
            AlphaAnimatedImageView(
                fileURL: imageURL,
                repeatCount: showingLoop ? 0 : 1,
                onFinished: showingLoop ? nil : { showingLoop = true }
            )
            .accessibilityHidden(true)
        }
    }
}
