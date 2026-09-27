import SwiftUI
import UIKit

// MARK: - Scroll view

/// Vertical top+bottom vignette drawn over the scroll view during a
/// pinch. Fades in on `.began`, fades out on snap complete. Adds a
/// subtle "zooming in the center of the stack" feel — rows near the
/// vertical center of the screen stay bright, rows near the top/bottom
/// edges dim out.
final class PinchVignetteView: UIView {
    override class var layerClass: AnyClass { CAGradientLayer.self }
    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        let g = layer as! CAGradientLayer
        g.startPoint = CGPoint(x: 0.5, y: 0)
        g.endPoint = CGPoint(x: 0.5, y: 1)
        g.colors = [
            UIColor.black.withAlphaComponent(0.35).cgColor,
            UIColor.black.withAlphaComponent(0).cgColor,
            UIColor.black.withAlphaComponent(0).cgColor,
            UIColor.black.withAlphaComponent(0.35).cgColor,
        ]
        g.locations = [0, 0.35, 0.65, 1]
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

final class HomeSessionsScrollUIView: UIView {
    let scrollView = UIScrollView()
    let contentView = UIView()
    let pinchVignette = PinchVignetteView()
    let catFooterHostingController = UIHostingController(rootView: AnyView(EmptyView()))
    var containers: [ThreadKey: HomeRowContainer] = [:]
    var order: [ThreadKey] = []

    var zoomLevel: Int = 2
    var isPinching = false
    var continuousZoom: Double = 2.0
    var pinchStartZoom: Double = 2.0
    var pinchStartScale: CGFloat = 1.0
    var pinchAnchorIdx: Int = 0
    var pinchAnchorFraction: CGFloat = 0
    /// Last finger midpoint observed in `.changed`. By the time
    /// `.ended` fires, UIKit has typically already removed the touches
    /// — so we can't read the midpoint off the recognizer — but we
    /// need it for the drop anchor calculation.
    var lastPinchMidpoint: CGPoint = .zero
    /// Finger midpoint captured at `.began`. The anchor row is pinned
    /// to THIS screen position for the duration of the gesture, so
    /// incidental finger drift during the pinch doesn't drag the
    /// content around like a scroll.
    var pinchStartMidpoint: CGPoint = .zero

    var topInsetValue: CGFloat = 0
    var bottomInsetValue: CGFloat = 0
    var catFooterCountEligible = false
    var catFooterHostVisible = false
    var catFooterEntranceStarted = false
    private var widthUsed: CGFloat = 0
    var lastCommittedInteger: Int = 2
    /// Last-seen text scale. A change here invalidates every row's
    /// measured natural height because font sizes — and therefore
    /// intrinsic SwiftUI layout — shift with the user's text-size
    /// preference.
    var lastTextScale: CGFloat = 0

    var zoomCommit: ((Int) -> Void)?

    /// Surface the scroll view's safe-area top for row containers — they
    /// need it to keep the previous card's bottom from peeking into the
    /// dynamic-island zone during page-fit transitions.
    var scrollViewSafeAreaTop: CGFloat { scrollView.safeAreaInsets.top }

    // Used by row containers to know whether to short-circuit tap/swipe.
    var pinchActive: Bool { isPinching }
    // Used by rows to lock the vertical scroll while a swipe is latched.
    private(set) var activeSwipeRowCount: Int = 0 {
        didSet { updateScrollEnabled() }
    }

    private lazy var pinchRecognizer: UIPinchGestureRecognizer = {
        let g = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
        g.delegate = self
        return g
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        addSubview(scrollView)
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
        scrollView.addSubview(contentView)
        scrollView.showsVerticalScrollIndicator = true
        scrollView.alwaysBounceVertical = true
        scrollView.backgroundColor = .clear
        scrollView.keyboardDismissMode = .interactive
        // `.always` so the scroll view's adjustedContentInset stacks
        // our configured `contentInset` on top of the safe-area insets
        // — lets the outer `.ignoresSafeArea()` SwiftUI modifier push
        // the scroll view edge-to-edge without the top row sliding
        // under the dynamic island / status bar.
        scrollView.contentInsetAdjustmentBehavior = .always
        scrollView.delegate = self
        scrollView.addGestureRecognizer(pinchRecognizer)
        catFooterHostingController.view.backgroundColor = .clear
        catFooterHostingController.view.isHidden = true
        contentView.addSubview(catFooterHostingController.view)
        // Let pinch and scroll pan arbitrate naturally. Pinch requires 2
        // touches to begin; `numberOfTouchesRequired = 2` on pinch + our
        // pinchActive check (which disables `scrollView.isScrollEnabled`
        // during a pinch) prevents them from fighting. Using
        // `panGestureRecognizer.require(toFail: pinchRecognizer)` left
        // 1-finger scrolls blocked until the pinch recognizer formally
        // failed — visible as dead touches on the row content area.

        // Vignette sits above the scroll view, edge-to-edge, non-
        // interactive. Fades in during pinch.
        pinchVignette.alpha = 0
        addSubview(pinchVignette)
        pinchVignette.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            pinchVignette.topAnchor.constraint(equalTo: topAnchor),
            pinchVignette.leadingAnchor.constraint(equalTo: leadingAnchor),
            pinchVignette.trailingAnchor.constraint(equalTo: trailingAnchor),
            pinchVignette.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(homeBecameActiveNotification),
            name: HomeSessionsScrollUIView.homeBecameActive,
            object: nil
        )
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    /// Posted by `ContentView` when the conversation is dismissed and the
    /// home screen becomes the active route again. A NavigationStack pop
    /// keeps our hosting tree in the window (so no `didMoveToWindow`), and
    /// SwiftUI does not reliably re-run `updateUIView`/`apply` on pop — so
    /// the UIHostingController-backed rows stay blank (only the background
    /// shows through) until the user nudges the scroll. This notification is
    /// a reliable "home is visible again" hook to force that redraw.
    static let homeBecameActive = Notification.Name("agentBuddyHomeBecameActive")

    @objc private func homeBecameActiveNotification() {
        kickRedraw()
    }

    /// Re-lay-out and force the scroll view to repaint its hosted rows.
    /// Mirrors what a manual scroll does (the user-observed workaround):
    /// nudge `contentOffset` so UIKit gives the scroll view a fresh display
    /// pass, which repaints the rows that came back blank after a pop.
    private func kickRedraw() {
        guard window != nil else { return }
        invalidateMeasurements()
        relayout(animated: false)
        if !isPinching {
            for container in containers.values {
                container.forceResetPinchBlurIfIdle()
                container.setNeedsLayout()
                container.layoutIfNeeded()
            }
        }
        // Nudge the content offset by 1pt and revert it on the NEXT runloop.
        // Done in one runloop, UIKit coalesces the two sets into a net-zero
        // move and skips the display pass; split across two runloops it
        // produces two real scroll/display passes (≈16ms apart, imperceptible)
        // — exactly what the user's manual scroll does to repaint the rows.
        DispatchQueue.main.async { [weak self] in
            guard let self, self.window != nil else { return }
            let offset = self.scrollView.contentOffset
            self.scrollView.setContentOffset(CGPoint(x: offset.x, y: offset.y + 1), animated: false)
            DispatchQueue.main.async { [weak self] in
                guard let self, self.window != nil else { return }
                self.scrollView.setContentOffset(offset, animated: false)
            }
        }
        // The blur can re-snap a beat later as the pop transition's own
        // animations finish, so sweep the per-row reset again on a few short
        // delays to catch a late snap on whichever rows are on screen.
        for delay in [0.08, 0.25, 0.5] {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                guard let self, self.window != nil, !self.isPinching else { return }
                for container in self.containers.values { container.forceResetPinchBlurIfIdle() }
            }
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if abs(bounds.width - widthUsed) > 0.5 {
            widthUsed = bounds.width
            invalidateMeasurements()
            relayout(animated: false)
        }
    }

    func noteRowSwipeChanged(activated: Bool) {
        if activated { activeSwipeRowCount += 1 }
        else { activeSwipeRowCount = max(0, activeSwipeRowCount - 1) }
    }

    func updateScrollEnabled() {
        let enabled = !(isPinching || activeSwipeRowCount > 0)
        scrollView.isScrollEnabled = enabled
        // Also toggle the pan recognizer directly — `isScrollEnabled`
        // is the public knob but the pan can occasionally still fire
        // in-flight events. Disabling the recognizer cancels any
        // pending pan gesture immediately so `contentOffset` stays
        // under our pinch-anchor control.
        scrollView.panGestureRecognizer.isEnabled = enabled
    }

    private func invalidateMeasurements() {
        for container in containers.values {
            container.invalidateNaturalHeight()
        }
    }
}
