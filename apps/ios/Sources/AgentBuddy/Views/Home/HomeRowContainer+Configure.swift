import SwiftUI
import UIKit

extension HomeRowContainer {
    // MARK: - Configure

    func configure(
        session: HomeDashboardRecentSession,
        isOpening: Bool,
        isHydrating: Bool,
        isCancelling: Bool,
        pinned: Bool,
        displayZoom: Int,
        textScale: CGFloat,
        themeManager: ThemeManager,
        wallpaperManager: WallpaperManager,
        callbacks: HomeSessionsScrollView.Callbacks
    ) {
        let sessionChanged = self.session != session
        let stateChanged = self.isOpening != isOpening ||
            self.isHydrating != isHydrating ||
            self.isCancelling != isCancelling ||
            self.pinned != pinned
        let zoomChanged = self.displayZoom != displayZoom
        let textScaleChanged = abs(self.textScale - textScale) > 0.001
        let environmentChanged = self.themeManager !== themeManager ||
            self.wallpaperManager !== wallpaperManager
        self.session = session
        self.isOpening = isOpening
        self.isHydrating = isHydrating
        self.isCancelling = isCancelling
        self.pinned = pinned
        self.displayZoom = displayZoom
        self.textScale = textScale
        self.themeManager = themeManager
        self.wallpaperManager = wallpaperManager
        self.callbacks = callbacks

        if sessionChanged || textScaleChanged {
            cachedNaturalHeight = nil
            hostHeightByZoom.removeAll(keepingCapacity: true)
        }
        if sessionChanged || stateChanged || zoomChanged || textScaleChanged {
            refreshRootView()
            setNeedsLayout()
        }
        if sessionChanged || zoomChanged || environmentChanged {
            refreshPageBackgroundView()
        }
    }

    func setDisplayZoom(_ z: Int) {
        guard displayZoom != z else { return }
        displayZoom = z
        refreshRootView()
        refreshPageBackgroundView()
        // Re-measure host height for the new displayZoom (cached per zoom).
        setNeedsLayout()
        layoutIfNeeded()
    }

    func invalidateNaturalHeight() {
        cachedNaturalHeight = nil
        hostHeightByZoom.removeAll(keepingCapacity: true)
    }

    /// Report a cached natural height for a given zoom level, if one
    /// has been measured at the current width. `layoutSubviews` pops a
    /// measurement for whatever the current `displayZoom` is, so rows
    /// naturally populate this cache as the user browses at different
    /// committed zooms.
    func cachedNaturalHeight(atZoom zoom: Int, width: CGFloat) -> CGFloat? {
        guard hostHeightCachedWidth == width else { return nil }
        return hostHeightByZoom[zoom]
    }

    private func refreshRootView() {
        guard let session, let callbacks else { return }
        let sessionSnapshot = session
        let openTap: () -> Void = { [weak self] in
            guard let self, self.scrollHost?.pinchActive != true else { return }
            callbacks.onOpen(sessionSnapshot)
        }
        let content = HomeSessionRowContent(
            session: session,
            isOpening: isOpening,
            isHydrating: isHydrating,
            isCancelling: isCancelling,
            zoomLevel: displayZoom,
            pinned: pinned,
            onTap: openTap,
            onReply: { callbacks.onReply(sessionSnapshot) },
            onHide: { callbacks.onHide(sessionSnapshot.key) },
            onPin: { callbacks.onPin(sessionSnapshot.key) },
            onUnpin: { callbacks.onUnpin(sessionSnapshot.key) },
            onCancelTurn: { callbacks.onCancelTurn(sessionSnapshot) },
            onDelete: { callbacks.onDelete(sessionSnapshot) },
            onFork: { callbacks.onFork(sessionSnapshot) },
            onShowPiP: { callbacks.onShowPiP(sessionSnapshot) }
        )
        .environment(\.textScale, textScale)
        hostingController.rootView = AnyView(content)
    }

    func setPageBackgroundVisible(_ visible: Bool) {
        guard pageBackgroundVisible != visible else { return }
        pageBackgroundVisible = visible
        refreshPageBackgroundView()
    }

    private func refreshPageBackgroundView() {
        guard displayZoom == 4,
              pageBackgroundVisible,
              let session,
              let themeManager,
              let wallpaperManager else {
            backgroundHostingController.rootView = AnyView(EmptyView())
            backgroundHostingController.view.isHidden = true
            return
        }

        let background = ChatWallpaperBackground(threadKey: session.key)
            .environment(themeManager)
            .environment(wallpaperManager)
        backgroundHostingController.rootView = AnyView(background)
        backgroundHostingController.view.isHidden = false
    }

    // MARK: - Measurement

    /// Natural container height at zoom 4 — equals the hosted SwiftUI
    /// view's intrinsic height at displayZoom=4. Only reliable when
    /// the row is currently rendering at displayZoom=4 (set at pinch
    /// begin and at committed z=4).
    func naturalHeightAtZoom4(width: CGFloat) -> CGFloat {
        if let cached = cachedNaturalHeight, abs(cachedMeasureWidth - width) < 0.5 {
            return cached
        }
        guard session != nil else { return 400 }
        guard displayZoom == 4 else {
            return 400
        }
        // Invalidate any existing measurement for this zoom, then
        // remeasure with the current width. `measureHostHeight` caches
        // into `hostHeightByZoom` and `cachedNaturalHeight`.
        hostHeightByZoom.removeValue(forKey: 4)
        return measureHostHeight(width: width)
    }
}
