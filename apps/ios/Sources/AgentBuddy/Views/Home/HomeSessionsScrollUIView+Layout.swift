import SwiftUI
import UIKit

// MARK: - Zoom height anchors

/// Fixed height anchors for zoom levels 1, 2, and 3 (fallback only —
/// the row's own `forceMeasureHostHeight` supersedes these when the row
/// is actually rendered at that zoom). Zoom 4 is always per-row
/// measured because its content height varies wildly with the assistant
/// response preview.
private enum ZoomHeights {
    static let z1: CGFloat = 28
    static let z2: CGFloat = 54
    static let z3: CGFloat = 110
    static let z4Minimum: CGFloat = 120
}

private let zoomSnapDuration: TimeInterval = 0.22

extension HomeSessionsScrollUIView {
    // MARK: - Layout

    func relayout(animated: Bool) {
        let width = bounds.width
        guard width > 0 else { return }

        let z = continuousZoom
        var y: CGFloat = 0
        var frames: [(HomeRowContainer, CGRect)] = []
        for key in order {
            guard let container = containers[key] else { continue }
            let h = rowHeight(for: container, at: z, width: width)
            frames.append((container, CGRect(x: 0, y: y, width: width, height: h)))
            y += h
        }
        let footerFrame: CGRect
        if shouldShowCatFooter {
            let h = catFooterHeight(width: width)
            footerFrame = CGRect(x: 0, y: y, width: width, height: h)
            y += h
        } else {
            footerFrame = .zero
        }
        let newContentSize = CGSize(width: width, height: y)

        if animated {
            UIView.animate(withDuration: zoomSnapDuration, delay: 0, options: [.curveEaseOut]) {
                for (container, frame) in frames { container.frame = frame }
                self.catFooterHostingController.view.frame = footerFrame
                self.contentView.frame = CGRect(origin: .zero, size: newContentSize)
                self.scrollView.contentSize = newContentSize
                self.updatePageBackgroundVisibility()
            } completion: { _ in
                self.updatePageBackgroundVisibility()
            }
        } else {
            for (container, frame) in frames { container.frame = frame }
            catFooterHostingController.view.frame = footerFrame
            contentView.frame = CGRect(origin: .zero, size: newContentSize)
            scrollView.contentSize = newContentSize
            updatePageBackgroundVisibility()
        }
    }

    func updatePageBackgroundVisibility() {
        let canShowPageBackground = zoomLevel == 4 && !isPinching
        let visibleRect = scrollView.convert(scrollView.bounds, to: contentView)
            .insetBy(dx: 0, dy: -1)
        for key in order {
            guard let container = containers[key] else { continue }
            let isVisible = canShowPageBackground && visibleRect.intersects(container.frame)
            container.setPageBackgroundVisible(isVisible)
        }
    }

    private var shouldShowCatFooter: Bool {
        catFooterCountEligible && zoomLevel == 1 && !isPinching
    }

    private func catFooterHeight(width: CGFloat) -> CGFloat {
        let videoWidth = min(max(0, width - 48), 340)
        return videoWidth * 9.0 / 16.0 + 32
    }

    func refreshCatFooterVisibility() {
        let visible = shouldShowCatFooter
        guard catFooterHostVisible != visible else { return }
        catFooterHostVisible = visible
        if visible {
            let playEntrance = !catFooterEntranceStarted
            catFooterEntranceStarted = true
            catFooterHostingController.rootView = AnyView(HomeCatFooterView(playEntrance: playEntrance))
        } else {
            catFooterHostingController.rootView = AnyView(EmptyView())
        }
        catFooterHostingController.view.isHidden = !visible
    }

    private func rowHeight(
        for container: HomeRowContainer,
        at zoom: Double,
        width: CGFloat
    ) -> CGFloat {
        // Four committed zoom levels: 1 SCAN, 2 GLANCE, 3 READ, 4 DEEP.
        // Continuous pinch interpolates linearly between adjacent anchors.
        let zc = max(1.0, min(4.0, zoom))
        // Zoom 4 (committed, not mid-pinch) is page-fit: every card is
        // exactly one visible-area tall so only one shows at a time and
        // the scroll view snaps to integer page boundaries — TikTok-style.
        // During a pinch, we still interpolate via the natural h4 so the
        // user can see content size grow continuously.
        if zc >= 4.0 && !isPinching {
            return pageFitHeight()
        }
        let h1 = heightAnchor(for: container, zoomInt: 1, width: width)
        let h2 = heightAnchor(for: container, zoomInt: 2, width: width)
        let h3 = heightAnchor(for: container, zoomInt: 3, width: width)
        let h4 = heightAnchor(for: container, zoomInt: 4, width: width)
        if zc <= 1.0 { return h1 }
        if zc <= 2.0 {
            let t = CGFloat(zc - 1.0)
            return h1 + t * (h2 - h1)
        }
        if zc <= 3.0 {
            let t = CGFloat(zc - 2.0)
            return h2 + t * (h3 - h2)
        }
        if zc >= 4.0 { return h4 }
        let t = CGFloat(zc - 3.0)
        return h3 + t * (h4 - h3)
    }

    /// Page-fit card height at zoom 4. Each card frame is exactly the
    /// full scroll-view bounds — that way card N's frame in scroll
    /// content runs `[N · boundsH, (N+1) · boundsH]` with no carved-out
    /// inset zones. The card's *internal* layout (`HomeRowContainer`)
    /// then offsets its host view by `topInsetValue` and stops it short
    /// of the bottom by `safeAreaInsets.top` so the chrome zones at the
    /// top of the *next* page (and the safe-area zone at the bottom of
    /// the *previous* page) draw empty container space rather than a
    /// neighbour's content.
    func pageFitHeight() -> CGFloat {
        max(120, bounds.height)
    }

    private func heightAnchor(
        for container: HomeRowContainer,
        zoomInt: Int,
        width: CGFloat
    ) -> CGFloat {
        if let measured = container.cachedNaturalHeight(atZoom: zoomInt, width: width) {
            return measured
        }
        if container.currentDisplayZoom == zoomInt {
            return container.forceMeasureHostHeight(width: width)
        }
        switch zoomInt {
        case 1: return ZoomHeights.z1
        case 2: return ZoomHeights.z2
        case 3: return ZoomHeights.z3
        default: return container.naturalHeightAtZoom4(width: width)
        }
    }
}
