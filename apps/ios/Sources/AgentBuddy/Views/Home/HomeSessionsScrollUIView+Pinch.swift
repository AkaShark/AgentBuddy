import SwiftUI
import UIKit

/// Zoom levels per "octave" of pinch (doubling/halving the finger
/// distance). Symmetric around scale=1, unlike `(scale - 1) / k` which
/// treats pinch-out (close) much less sensitively than pinch-in (open).
/// `log2(scale) * zoomLevelsPerOctave` gives: scale=2 → +1.4 levels,
/// scale=0.5 → -1.4 levels.
private let zoomLevelsPerOctave: Double = 1.4

extension HomeSessionsScrollUIView {
    // MARK: - Pinch

    @objc func handlePinch(_ g: UIPinchGestureRecognizer) {
        switch g.state {
        case .began:
            beginPinch(g)
        case .changed:
            updatePinch(g)
        case .ended, .cancelled, .failed:
            endPinch(g)
        default:
            break
        }
    }

    private func beginPinch(_ g: UIPinchGestureRecognizer) {
        // Cancel any in-flight snap animation from a previous pinch so the
        // new pinch starts from a clean state.
        layer.removeAllAnimations()
        for key in order {
            containers[key]?.layer.removeAllAnimations()
        }
        pinchVignette.layer.removeAllAnimations()

        // Promote every row to displayZoom=4 FIRST so the full content tree
        // is rendered. UIKit frame animation reveals it progressively.
        // Also reset the per-row blur-progress peak so the ease-out curve
        // starts from zero for this new gesture.
        for key in order {
            containers[key]?.setDisplayZoom(4)
            containers[key]?.resetPinchBlurPeak()
        }

        isPinching = true
        refreshCatFooterVisibility()
        updateScrollEnabled()
        pinchStartZoom = continuousZoom
        pinchStartScale = g.scale

        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut, .beginFromCurrentState, .allowUserInteraction]) {
            self.pinchVignette.alpha = 1
        }

        // Anchor: the row containing the midpoint between the two
        // fingers. Capture the exact fractional position of the finger
        // within the row — this is the "tracking" anchor used at the
        // start of the gesture. As zoom climbs toward 4, the anchor
        // migrates from (rowIdx, frac) under the fingers to (rowIdx, 0)
        // at the top of the visible area, so the row naturally "opens
        // up" and its title lands at the top at max zoom.
        let anchorPoint = midpoint(of: g, in: self)
        pinchStartMidpoint = anchorPoint
        lastPinchMidpoint = anchorPoint
        let anchorContentY = scrollView.contentOffset.y + anchorPoint.y
        if let (idx, frac) = locateAnchor(atContentY: anchorContentY) {
            pinchAnchorIdx = idx
            pinchAnchorFraction = frac
        } else {
            pinchAnchorIdx = 0
            pinchAnchorFraction = 0
        }

        // Highlight the anchor row so the user sees which one the pinch
        // is operating on the instant their two fingers land. The
        // subsequent `updatePinch` calls drive the alpha down toward
        // zero as zoom progress increases, so the highlight "uses up"
        // as the row opens.
        if pinchAnchorIdx >= 0, pinchAnchorIdx < order.count {
            let key = order[pinchAnchorIdx]
            containers[key]?.setPinchHighlightAlpha(1, animated: true)
        }
    }

    private func updatePinch(_ g: UIPinchGestureRecognizer) {
        // Log-based pinch: delta in zoom levels = log2(current / start)
        // × sensitivity. Symmetric: halving the finger distance (scale
        // → 0.5) subtracts the same number of levels that doubling it
        // adds.
        let scaleRatio = max(0.05, Double(g.scale / pinchStartScale))
        let delta = log2(scaleRatio) * zoomLevelsPerOctave
        let zc = max(1.0, min(4.0, pinchStartZoom + delta))
        continuousZoom = zc

        relayout(animated: false)

        // Anchor stays pinned to the pinch-start finger midpoint —
        // not the current midpoint — so incidental finger drift
        // during the pinch doesn't shift the content around like a
        // scroll. The row expands and contracts in place under the
        // starting position of the gesture.
        if g.numberOfTouches >= 2 {
            lastPinchMidpoint = midpoint(of: g, in: self)
            if let newAnchorY = contentYForAnchor(
                idx: pinchAnchorIdx, fraction: pinchAnchorFraction
            ) {
                let raw = newAnchorY - pinchStartMidpoint.y
                scrollView.contentOffset = CGPoint(
                    x: scrollView.contentOffset.x,
                    y: raw
                )
            }
        }

        // Haptic tick on crossing the snap midpoints (1.5 and 3.0).
        let newInteger = snapZoom(zc)
        if newInteger != lastCommittedInteger {
            lastCommittedInteger = newInteger
            let gen = UIImpactFeedbackGenerator(style: .light)
            gen.impactOccurred(intensity: 0.5)
        }

        // Fade the anchor highlight inversely with zoom progress:
        // full alpha at pinchStartZoom, zero at z=4, reversing if the
        // user pinches back toward the start. Siblings get a blur
        // overlay that ramps up in the opposite direction so they
        // recede behind the opening row.
        let denom = max(0.001, 4.0 - pinchStartZoom)
        let progress = CGFloat(max(0, min(1, (zc - pinchStartZoom) / denom)))
        for (i, key) in order.enumerated() {
            guard let container = containers[key] else { continue }
            if i == pinchAnchorIdx {
                container.setPinchHighlightAlpha(1 - progress)
                container.setPinchBlurProgress(0)
            } else {
                container.setPinchHighlightAlpha(0)
                container.setPinchBlurProgress(progress)
            }
        }
    }

    /// Snap a continuous zoom into the three committed levels: {1, 2, 4}.
    /// Thresholds are the midpoints between levels.
    private func snapZoom(_ zc: Double) -> Int {
        if zc < 1.5 { return 1 }
        if zc < 3.0 { return 2 }
        return 4
    }

    private func endPinch(_ g: UIPinchGestureRecognizer) {
        let snapped = snapZoom(continuousZoom)
        let changed = snapped != zoomLevel
        zoomLevel = snapped
        continuousZoom = Double(snapped)

        // Finger midpoint for the drop anchor. UIKit usually removes
        // the touches before `.ended` fires, so we use the last
        // midpoint observed in `.changed` — otherwise the snap would
        // relayout row heights without compensating contentOffset and
        // the anchor row would visibly drift away on release.
        let dropFinger = lastPinchMidpoint

        // Spring-animate frames + contentSize + contentOffset together so
        // the snap feels like a single elastic motion instead of a linear
        // ease-out. SwiftUI stays at displayZoom=4 during the animation
        // so the content we're collapsing *to* is still fully rendered.
        UIView.animate(
            withDuration: 0.38, delay: 0,
            usingSpringWithDamping: 0.82,
            initialSpringVelocity: 0.3,
            options: [.curveEaseOut, .beginFromCurrentState, .allowUserInteraction]
        ) {
            self.relayout(animated: false)
            if let newAnchorY = self.contentYForAnchor(
                idx: self.pinchAnchorIdx,
                fraction: self.pinchAnchorFraction
               ) {
                var raw = newAnchorY - dropFinger.y
                if let rowTopY = self.contentYForAnchor(idx: self.pinchAnchorIdx, fraction: 0) {
                    let maxOffsetForRowTopAtViewTop = rowTopY - self.scrollView.adjustedContentInset.top
                    raw = min(raw, maxOffsetForRowTopAtViewTop)
                }
                let maxY = max(-self.scrollView.adjustedContentInset.top,
                               self.scrollView.contentSize.height - self.scrollView.bounds.height + self.scrollView.adjustedContentInset.bottom)
                let minY = -self.scrollView.adjustedContentInset.top
                self.scrollView.contentOffset = CGPoint(
                    x: self.scrollView.contentOffset.x,
                    y: min(max(raw, minY), maxY)
                )
            }
        } completion: { _ in
            self.isPinching = false
            self.refreshCatFooterVisibility()
            self.updateScrollEnabled()
            // Reset displayZoom to the committed integer so each row
            // goes back to its gated-content rendering.
            for key in self.order {
                self.containers[key]?.setDisplayZoom(snapped)
            }
            // One more layout pass — the displayZoom=4 layouts may have
            // left the rows with slightly taller natural sizes than
            // needed at the snapped zoom.
            self.relayout(animated: false)
            self.updatePageBackgroundVisibility()
        }

        // Vignette + anchor highlight fade out together — slightly
        // faster than the snap so they're gone by the time the rows
        // settle.
        UIView.animate(withDuration: 0.25, delay: 0, options: [.curveEaseOut, .beginFromCurrentState]) {
            self.pinchVignette.alpha = 0
        }
        for (i, key) in order.enumerated() {
            guard let container = containers[key] else { continue }
            if i == pinchAnchorIdx {
                container.setPinchHighlightAlpha(0, animated: true)
            } else {
                container.fadeOutPinchBlur()
            }
        }

        if changed {
            zoomCommit?(snapped)
            let gen = UIImpactFeedbackGenerator(style: .medium)
            gen.impactOccurred()
        }
    }

    // MARK: - Anchor helpers

    private func midpoint(of g: UIPinchGestureRecognizer, in view: UIView) -> CGPoint {
        if g.numberOfTouches >= 2 {
            let p0 = g.location(ofTouch: 0, in: view)
            let p1 = g.location(ofTouch: 1, in: view)
            return CGPoint(x: (p0.x + p1.x) / 2, y: (p0.y + p1.y) / 2)
        }
        return g.location(in: view)
    }

    private func locateAnchor(atContentY y: CGFloat) -> (Int, CGFloat)? {
        var cy: CGFloat = 0
        var lastValidIdx: Int? = nil
        for (i, key) in order.enumerated() {
            guard let container = containers[key] else { continue }
            let h = container.frame.height
            if h <= 0 { continue }
            if y >= cy && y <= cy + h {
                let frac = max(0, min(1, (y - cy) / h))
                return (i, frac)
            }
            cy += h
            lastValidIdx = i
        }
        // Past the last row → clamp to the LAST row (not the first).
        // Keeps the anchor on the row the user meant to pinch when
        // their midpoint lands in the empty space below the content.
        if let lastValidIdx, y > 0 {
            return (lastValidIdx, 1.0)
        }
        return nil
    }

    private func contentYForAnchor(idx: Int, fraction: CGFloat) -> CGFloat? {
        guard idx >= 0, idx < order.count else { return nil }
        var cy: CGFloat = 0
        for (i, key) in order.enumerated() {
            guard let container = containers[key] else { continue }
            let h = container.frame.height
            if i == idx { return cy + fraction * h }
            cy += h
        }
        return nil
    }
}
