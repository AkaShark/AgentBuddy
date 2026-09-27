import SwiftUI
import UIKit

/// CADisplayLink target shim — it only holds a closure to call on
/// each tick. `CADisplayLink` needs an ObjC @objc selector target,
/// which a generic closure-friendly helper provides cleanly.
private final class PinchBlurFadeTarget {
    let handler: () -> Void
    init(_ handler: @escaping () -> Void) { self.handler = handler }
    @objc func tick() { handler() }
}

extension HomeRowContainer {
    /// Drive blur intensity via the paused animator's fractionComplete.
    /// Non-anchor rows track pinch progress; anchor row stays at 0.
    ///
    /// Shape:
    ///   * pow-curve eases the start (progress^1.8) so slight pinches
    ///     don't slam straight into heavy blur.
    ///   * multiplied by `pinchBlurCeiling` so even at full zoom the
    ///     blur tops out below the animator's max — keeps siblings
    ///     legible as silhouettes instead of milky squares.
    private static let pinchBlurCeiling: CGFloat = 0.5
    private static let pinchBlurExponent: CGFloat = 2.8
    func setPinchBlurProgress(_ progress: CGFloat) {
        // Mac modes (Catalyst + iOS-on-Mac) don't install the
        // pinch-blur view (see init).
        if AgentBuddyPlatform.rendersAsMacApp { return }
        guard !UIAccessibility.isReduceTransparencyEnabled else {
            pinchBlur.removeFromSuperview()
            pinchBlur.effect = nil
            tearDownPinchBlurAnimator()
            return
        }
        updatePinchBlurAvailability()
        guard let pinchBlurAnimator else { return }
        let p = max(0, min(1, progress))
        // Symmetric ease-out curve: blur tracks zoom progress both
        // directions so a pinch-in that had slowly-building blur will
        // slowly release it on the way back. No peak tracking — it
        // introduced a fast-drop curve on collapse that felt like the
        // blur abruptly vanished.
        let eased = pow(p, Self.pinchBlurExponent) * Self.pinchBlurCeiling
        pinchBlurAnimator.fractionComplete = max(0, min(0.999, eased))
    }

    /// No-op kept for the scroll host's `.began` call site; the
    /// direction-aware curve was replaced with a symmetric one.
    func resetPinchBlurPeak() {}

    /// Force the pinch blur back to a clean, nil-effect state when no
    /// pinch is in progress. Called from the scroll host's `apply()`
    /// after every SwiftUI update — covers the case where iOS finishes
    /// our paused `UIViewPropertyAnimator` out from under us during a
    /// NavigationStack push/pop (e.g. into the terminal screen and
    /// back), which otherwise snaps the blur to its end state and
    /// leaves a milky band over the row.
    func forceResetPinchBlurIfIdle() {
        if AgentBuddyPlatform.rendersAsMacApp { return }
        if UIAccessibility.isReduceTransparencyEnabled { return }
        // A live fade-out is still scrubbing the animator's
        // fractionComplete back to 0 — don't yank the animator out
        // from under it.
        if fadeLink != nil { return }
        // If the scroll host says a pinch is active, the animator is
        // being scrubbed in real time. Leave it alone.
        if scrollHost?.pinchActive == true { return }
        tearDownPinchBlurAnimator()
        pinchBlur.effect = nil
        // [baozi-fork] Yank the effect view out of the hierarchy entirely so a
        // stuck/half-snapped material can't keep painting a grey band over the
        // row. `updatePinchBlurAvailability` re-inserts it when the next pinch
        // begins.
        pinchBlur.removeFromSuperview()
        // [baozi-fork] Do NOT rebuild the paused animator here. A freshly
        // built UIViewPropertyAnimator for a UIVisualEffectView momentarily
        // renders its full end-state blur until a layout pass scrubs it to
        // fractionComplete=0. A fullScreenCover return (terminal → back) gives
        // the row that pass for free, but a NavigationStack pop (conversation
        // → home) does not, so rebuilding left a milky grey band over the whole
        // list until the user nudged the scroll. Leave the animator nil; the
        // pinch drive path (`updatePinchBlurAvailability`) rebuilds it lazily
        // when the next pinch starts.
        pinchBlurAnimator = nil
    }

    /// Smoothly wind the blur back to zero on pinch release. Uses
    /// a CADisplayLink-driven tween because UIViewPropertyAnimator's
    /// `fractionComplete` can't be animated with `UIView.animate`.
    func fadeOutPinchBlur(duration: TimeInterval = 0.25) {
        if AgentBuddyPlatform.rendersAsMacApp { return }
        guard !UIAccessibility.isReduceTransparencyEnabled,
              let pinchBlurAnimator else {
            fadeLink?.invalidate()
            fadeLink = nil
            pinchBlur.removeFromSuperview()
            pinchBlur.effect = nil
            return
        }
        fadeLink?.invalidate()
        let start = CFAbsoluteTimeGetCurrent()
        let from = pinchBlurAnimator.fractionComplete
        let link = CADisplayLink(target: PinchBlurFadeTarget { [weak self] in
            guard let self else { return }
            let t = min(1, (CFAbsoluteTimeGetCurrent() - start) / duration)
            let eased = 1 - (1 - t) * (1 - t)  // ease-out quad
            let value = from * (1 - CGFloat(eased))
            self.pinchBlurAnimator?.fractionComplete = max(0, value)
            if t >= 1 {
                self.fadeLink?.invalidate()
                self.fadeLink = nil
            }
        }, selector: #selector(PinchBlurFadeTarget.tick))
        link.add(to: .main, forMode: .common)
        fadeLink = link
    }

    /// Set the highlight opacity directly (0–1). Used during a live
    /// pinch so the tint fades in sync with zoom progress — strong at
    /// pinch start, invisible at full open, reversing on collapse.
    func setPinchHighlightAlpha(_ alpha: CGFloat, animated: Bool = false) {
        let clamped = max(0, min(1, alpha))
        if animated {
            UIView.animate(
                withDuration: clamped > pinchHighlight.alpha ? 0.12 : 0.22, delay: 0,
                options: [.curveEaseOut, .beginFromCurrentState, .allowUserInteraction]
            ) {
                self.pinchHighlight.alpha = clamped
            }
        } else {
            pinchHighlight.alpha = clamped
        }
    }
}
