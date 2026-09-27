import SwiftUI
import UIKit

extension HomeSessionsScrollUIView {
    // MARK: - Update

    func apply(
        sessions: [HomeDashboardRecentSession],
        pinnedThreadKeys: Set<SavedThreadsStore.PinnedKey>,
        hydratingKeys: Set<String>,
        cancellingKeys: Set<String>,
        openingKey: ThreadKey?,
        zoomLevel: Int,
        showCatFooter: Bool,
        topInset: CGFloat,
        bottomInset: CGFloat,
        textScale: CGFloat,
        themeManager: ThemeManager,
        wallpaperManager: WallpaperManager,
        callbacks: HomeSessionsScrollView.Callbacks
    ) {
        let zoomChanged = self.zoomLevel != zoomLevel && !isPinching
        let enteredPageFit = zoomChanged && zoomLevel == 4
        self.zoomLevel = zoomLevel
        if !isPinching {
            self.continuousZoom = Double(zoomLevel)
        }
        self.lastCommittedInteger = zoomLevel
        // Zoom 4 = page-fit. Snappier deceleration so the snap-to-page
        // arrives quickly instead of drifting like a normal long scroll.
        scrollView.decelerationRate = (zoomLevel == 4) ? .fast : .normal
        self.topInsetValue = topInset
        self.bottomInsetValue = bottomInset
        self.catFooterCountEligible = showCatFooter && !sessions.isEmpty && sessions.count <= 10
        // At zoom 4 each card *frame* is the full scroll-view height,
        // and the card's internal layout puts the title below the top
        // chrome via fixed offsets in `HomeRowContainer.layoutSubviews`.
        // We zero out both content insets so the scroll view's natural
        // rest position has card 0's frame top at bounds.y == 0 — i.e.
        // truly the top edge of the phone (modulo safe-area auto-adjust).
        let effectiveTopInset: CGFloat = (zoomLevel == 4) ? 0 : topInset
        let effectiveBottomInset: CGFloat = (zoomLevel == 4) ? 0 : bottomInset
        scrollView.contentInset = UIEdgeInsets(top: effectiveTopInset, left: 0, bottom: effectiveBottomInset, right: 0)
        scrollView.verticalScrollIndicatorInsets = UIEdgeInsets(top: effectiveTopInset, left: 0, bottom: effectiveBottomInset, right: 0)
        refreshCatFooterVisibility()

        // Text scale change → blow out every row's height cache and
        // propagate the new scale into each hosted SwiftUI tree.
        let textScaleChanged = abs(lastTextScale - textScale) > 0.001
        if textScaleChanged {
            lastTextScale = textScale
            for container in containers.values {
                container.invalidateNaturalHeight()
            }
        }

        // Diff — remove obsolete rows.
        let newIds = sessions.map(\.key)
        let newSet = Set(newIds)
        for key in Array(containers.keys) where !newSet.contains(key) {
            if let c = containers.removeValue(forKey: key) {
                c.removeFromSuperview()
            }
        }
        // Add new rows.
        for session in sessions where containers[session.key] == nil {
            let container = HomeRowContainer(scrollHost: self)
            containers[session.key] = container
            contentView.addSubview(container)
        }
        self.order = newIds

        // Push data into each row. During a pinch, display at zoom=4 so
        // every content layer is present and UIKit frame-clipping can
        // reveal it progressively. When idle, render at the committed
        // integer zoom.
        let displayZoom = isPinching ? 4 : zoomLevel
        for session in sessions {
            guard let container = containers[session.key] else { continue }
            let hid = "\(session.key.serverId)/\(session.key.threadId)"
            let pinned = pinnedThreadKeys.contains(SavedThreadsStore.PinnedKey(threadKey: session.key))
            container.configure(
                session: session,
                isOpening: openingKey == session.key,
                isHydrating: hydratingKeys.contains(hid),
                isCancelling: cancellingKeys.contains(hid),
                pinned: pinned,
                displayZoom: displayZoom,
                textScale: textScale,
                themeManager: themeManager,
                wallpaperManager: wallpaperManager,
                callbacks: callbacks
            )
        }

        let layoutAnimated = zoomChanged || textScaleChanged
        relayout(animated: layoutAnimated)
        updatePageBackgroundVisibility()

        // Repair stuck pinch-blur state. iOS can finish our paused
        // `UIViewPropertyAnimator` during NavigationStack push/pop
        // (terminal → back), leaving a row's `UIVisualEffectView`
        // showing the full end-state blur. SwiftUI re-runs `apply()`
        // after the pop, so this is the earliest reliable point to
        // reset the animator on every visible row.
        if !isPinching {
            for container in containers.values {
                container.forceResetPinchBlurIfIdle()
            }
        }

        // Just landed on the page-fit zoom from a different one — bring
        // the scroll position to the nearest page boundary so the user
        // doesn't end up resting between two cards. Done after relayout
        // so we snap against the freshly-sized rows.
        if enteredPageFit {
            snapToNearestPage(animated: layoutAnimated)
        }
    }

    /// Move `contentOffset` to the closest page boundary. Used when
    /// zooming into the page-fit zoom (4) and after layout shifts that
    /// would otherwise leave the user between cards.
    func snapToNearestPage(animated: Bool) {
        let page = pageFitHeight()
        guard page > 0 else { return }
        let insetTop = scrollView.adjustedContentInset.top
        let pageOriginInScroll = -insetTop
        let relative = scrollView.contentOffset.y - pageOriginInScroll
        let nearestPage = (relative / page).rounded()
        let snapped = pageOriginInScroll + nearestPage * page
        let maxY = max(
            pageOriginInScroll,
            scrollView.contentSize.height
                - scrollView.bounds.height
                + scrollView.adjustedContentInset.bottom
        )
        let target = min(maxY, max(pageOriginInScroll, snapped))
        // Only animate if we're actually moving — otherwise the no-op
        // animation can introduce a one-frame offset glitch.
        if abs(target - scrollView.contentOffset.y) > 0.5 {
            scrollView.setContentOffset(CGPoint(x: 0, y: target), animated: animated)
        }
    }
}
