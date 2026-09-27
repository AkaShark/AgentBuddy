import SwiftUI
import UIKit

extension HomeRowContainer {
    // MARK: - Swipe

    private static let fullSwipeThreshold: CGFloat = 120
    private static let activationDistance: CGFloat = 24
    private static let horizontalDominance: CGFloat = 2.0

    @objc func handleSwipe(_ g: UILongPressGestureRecognizer) {
        guard let session, let callbacks else { return }

        // If a second finger lands (pinch or two-finger scroll elsewhere),
        // bail immediately — reset offset and stop tracking.
        if g.numberOfTouches > 1 || scrollHost?.pinchActive == true {
            if swipeTracking {
                swipeTracking = false
                reset(animated: true)
            }
            return
        }

        let point = g.location(in: self)
        switch g.state {
        case .began:
            swipeStartPoint = point
            swipeTracking = true
        case .changed:
            guard swipeTracking else { return }
            let w = point.x - swipeStartPoint.x
            let h = point.y - swipeStartPoint.y
            if !activated {
                let horizontalDominant = abs(w) > abs(h) * Self.horizontalDominance
                let pastActivation = abs(w) >= Self.activationDistance
                if horizontalDominant && pastActivation {
                    activated = true
                    scrollHost?.noteRowSwipeChanged(activated: true)
                    let gen = UIImpactFeedbackGenerator(style: .light)
                    gen.impactOccurred(intensity: 0.5)
                } else {
                    return
                }
            }
            offsetX = w
            updateActionsVisuals()
            let nowPast = abs(w) >= Self.fullSwipeThreshold
            if nowPast != pastThreshold {
                pastThreshold = nowPast
                let gen = UIImpactFeedbackGenerator(style: .medium)
                gen.impactOccurred(intensity: 0.7)
            }
            setNeedsLayout()
            layoutIfNeeded()
        case .ended, .cancelled, .failed:
            guard swipeTracking else { return }
            swipeTracking = false
            let w = point.x - swipeStartPoint.x
            let shouldFire = activated && scrollHost?.pinchActive != true
            if activated {
                scrollHost?.noteRowSwipeChanged(activated: false)
            }
            activated = false
            pastThreshold = false
            if shouldFire && w > Self.fullSwipeThreshold {
                callbacks.onReply(session)
                let gen = UIImpactFeedbackGenerator(style: .heavy)
                gen.impactOccurred(intensity: 0.9)
            } else if shouldFire && w < -Self.fullSwipeThreshold {
                callbacks.onHide(session.key)
                let gen = UIImpactFeedbackGenerator(style: .heavy)
                gen.impactOccurred(intensity: 0.9)
            }
            reset(animated: true)
        default:
            break
        }
    }

    private func reset(animated: Bool) {
        if animated {
            UIView.animate(
                withDuration: 0.35, delay: 0,
                usingSpringWithDamping: 0.82, initialSpringVelocity: 0,
                options: [.curveEaseOut]
            ) {
                self.offsetX = 0
                self.updateActionsVisuals()
                self.setNeedsLayout()
                self.layoutIfNeeded()
            }
        } else {
            offsetX = 0
            updateActionsVisuals()
            setNeedsLayout()
        }
    }

    private func updateActionsVisuals() {
        let progress = min(1, abs(offsetX) / Self.fullSwipeThreshold)
        let tintAlpha = progress * 0.55
        let iconAlpha = progress
        let iconScale: CGFloat = 0.7 + 0.3 * progress

        if offsetX > 0 {
            actionsBackground.backgroundColor = UIColor(AgentBuddyTheme.accent)
            actionsBackground.alpha = tintAlpha
            leadingIconView.alpha = iconAlpha
            leadingIconView.transform = CGAffineTransform(scaleX: iconScale, y: iconScale)
            trailingIconView.alpha = 0
        } else if offsetX < 0 {
            actionsBackground.backgroundColor = UIColor(AgentBuddyTheme.danger)
            actionsBackground.alpha = tintAlpha
            trailingIconView.alpha = iconAlpha
            trailingIconView.transform = CGAffineTransform(scaleX: iconScale, y: iconScale)
            leadingIconView.alpha = 0
        } else {
            actionsBackground.alpha = 0
            leadingIconView.alpha = 0
            trailingIconView.alpha = 0
        }
    }
}

extension HomeRowContainer: UIGestureRecognizerDelegate {
    /// Run simultaneously with the enclosing scroll view's pan — our
    /// long-press recognizer observes touches without claiming direction,
    /// so scrolling continues to work until we latch onto a horizontal
    /// commitment in `handleSwipe`.
    func gestureRecognizer(
        _ g: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        true
    }
}
