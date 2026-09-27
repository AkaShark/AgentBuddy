import SwiftUI
import UIKit

// MARK: - Row container

final class HomeRowContainer: UIView {
    let hostingController: UIHostingController<AnyView>
    let backgroundHostingController: UIHostingController<AnyView>
    /// Clipping window for the hosted SwiftUI view. At zoom 4 the
    /// SwiftUI content can be naturally taller than the available
    /// screen-fit space (long response previews). The hosting view's
    /// own `.clipsToBounds` doesn't help, because UIHostingController
    /// sizes its `view` to the SwiftUI body's intrinsic height — so
    /// the view itself grows. Putting it inside this fixed-height
    /// clipping container is what actually keeps the title pinned at
    /// the top while the bottom of the content gets cut off.
    private let hostClip = UIView()
    let actionsBackground = UIView()
    let pinchHighlight = UIView()
    let pinchBlur = UIVisualEffectView(effect: nil)
    /// Paused animator that scrubs the blur effect's intensity via
    /// `fractionComplete`. Direct alpha on a `UIVisualEffectView`
    /// gives a crossfade rather than a progressive blur — scrubbing
    /// an animator's fractionComplete is the canonical way to
    /// interpolate blur radius on iOS.
    var pinchBlurAnimator: UIViewPropertyAnimator?
    private func makePinchBlurAnimator() -> UIViewPropertyAnimator {
        let animator = UIViewPropertyAnimator(duration: 1, curve: .linear)
        animator.addAnimations { [weak self] in
            self?.pinchBlur.effect = UIBlurEffect(style: .systemThinMaterialDark)
        }
        animator.pausesOnCompletion = true
        // IMPORTANT: the animator must be `.active` (running or paused)
        // for `fractionComplete` scrubbing to take effect. Right after
        // construction the animator is `.inactive` and scrubs silently
        // do nothing — so we kick it to running, immediately pause,
        // and seed the progress at 0.
        animator.startAnimation()
        animator.pauseAnimation()
        animator.fractionComplete = 0
        return animator
    }
    let leadingIconView = UIImageView()
    let trailingIconView = UIImageView()

    var session: HomeDashboardRecentSession?
    var isOpening = false
    var isHydrating = false
    var isCancelling = false
    var pinned = false
    private(set) var currentDisplayZoom: Int = 2
    var displayZoom: Int {
        get { currentDisplayZoom }
        set { currentDisplayZoom = newValue }
    }
    var callbacks: HomeSessionsScrollView.Callbacks?
    var cachedNaturalHeight: CGFloat?
    var cachedMeasureWidth: CGFloat = 0
    var textScale: CGFloat = 1.0
    var themeManager: ThemeManager?
    var wallpaperManager: WallpaperManager?
    var pageBackgroundVisible = false
    var fadeLink: CADisplayLink?
    /// Highest `setPinchBlurProgress` value observed during the current
    /// pinch. When progress dips below this, we're on the way back and
    /// the blur uses the inverse (ease-in) curve so it drops faster at
    /// first — symmetric feel with the slow ramp-up on the way in.
    private var peakBlurProgress: CGFloat = 0
    /// Natural hostingView height per displayZoom, keyed by (zoom,width).
    /// Invalidated when session data or displayZoom changes.
    var hostHeightByZoom: [Int: CGFloat] = [:]
    var hostHeightCachedWidth: CGFloat = 0

    var offsetX: CGFloat = 0
    var activated: Bool = false
    var pastThreshold: Bool = false
    var swipeStartPoint: CGPoint = .zero
    var swipeTracking: Bool = false

    weak var scrollHost: HomeSessionsScrollUIView?

    private lazy var swipeRecognizer: UILongPressGestureRecognizer = {
        let g = UILongPressGestureRecognizer(target: self, action: #selector(handleSwipe(_:)))
        g.minimumPressDuration = 0
        g.allowableMovement = .greatestFiniteMagnitude
        g.cancelsTouchesInView = false
        g.delegate = self
        return g
    }()

    init(scrollHost: HomeSessionsScrollUIView) {
        self.scrollHost = scrollHost
        self.hostingController = UIHostingController(rootView: AnyView(EmptyView()))
        self.backgroundHostingController = UIHostingController(rootView: AnyView(EmptyView()))
        super.init(frame: .zero)
        clipsToBounds = true
        backgroundColor = .clear

        backgroundHostingController.view.backgroundColor = .clear
        backgroundHostingController.view.isUserInteractionEnabled = false
        backgroundHostingController.view.isHidden = true
        addSubview(backgroundHostingController.view)

        // Actions background — tinted view that fills the row behind the
        // content, crossfading between leading (reply) / trailing (hide).
        actionsBackground.backgroundColor = .clear
        actionsBackground.alpha = 0
        addSubview(actionsBackground)

        leadingIconView.image = UIImage(systemName: "arrowshape.turn.up.left.fill")
        leadingIconView.tintColor = .white
        leadingIconView.contentMode = .center
        leadingIconView.alpha = 0
        actionsBackground.addSubview(leadingIconView)

        trailingIconView.image = UIImage(systemName: "eye.slash.fill")
        trailingIconView.tintColor = .white
        trailingIconView.contentMode = .center
        trailingIconView.alpha = 0
        actionsBackground.addSubview(trailingIconView)

        hostingController.view.backgroundColor = .clear
        hostClip.clipsToBounds = true
        hostClip.backgroundColor = .clear
        addSubview(hostClip)
        hostClip.addSubview(hostingController.view)

        // Pinch blur — non-anchor rows have their visual effect
        // interpolated via `pinchBlurAnimator.fractionComplete` during
        // pinch. Starts with `effect = nil` (no blur) and scrubs up
        // to a thin material blur as zoom progresses.
        //
        // Skipped whenever we render as a Mac app (Catalyst OR iOS-on-Mac):
        // UIBlurEffect bridges to NSVisualEffectView and does not honor a
        // paused animator at fractionComplete=0 — it renders the full
        // material instead of nothing, so the blur sits over every row
        // obscuring all content. No pinch gesture in Mac modes anyway,
        // so the whole pipeline is unused there.
        //
        // iOS Reduce Transparency has the same practical failure mode:
        // system material can collapse to an opaque fallback over the
        // hosted SwiftUI row. In that accessibility mode, the contrast-
        // safe behavior is no blur overlay at all.
        pinchBlur.isUserInteractionEnabled = false
        pinchBlur.alpha = 1
        if !AgentBuddyPlatform.rendersAsMacApp {
            updatePinchBlurAvailability()
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(reduceTransparencyDidChange),
                name: UIAccessibility.reduceTransparencyStatusDidChangeNotification,
                object: nil
            )
        }

        // Pinch highlight — subtle accent tint over the anchor row
        // while a pinch is live. Fades in on `.began`, tracks the
        // inverse of zoom progress during `.changed` (so it quietly
        // disappears as the row opens), and fades out on release.
        pinchHighlight.backgroundColor = UIColor(AgentBuddyTheme.accent).withAlphaComponent(0.14)
        pinchHighlight.layer.cornerRadius = 6
        pinchHighlight.isUserInteractionEnabled = false
        pinchHighlight.alpha = 0
        addSubview(pinchHighlight)

        addGestureRecognizer(swipeRecognizer)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    deinit {
        // UIKit raises NSInternalInconsistencyException if a
        // UIViewPropertyAnimator is released while still in `.active`
        // (running or paused). We hold it paused-active for
        // `fractionComplete` scrubbing, so terminate it explicitly here.
        fadeLink?.invalidate()
        if !AgentBuddyPlatform.rendersAsMacApp {
            NotificationCenter.default.removeObserver(
                self,
                name: UIAccessibility.reduceTransparencyStatusDidChangeNotification,
                object: nil
            )
            tearDownPinchBlurAnimator()
        }
    }

    @objc private func reduceTransparencyDidChange() {
        updatePinchBlurAvailability()
        setNeedsLayout()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        // `pinchBlur`'s intensity is driven by a paused
        // `UIViewPropertyAnimator` whose `fractionComplete` is scrubbed
        // between `effect = nil` and `systemThinMaterialDark`. When the
        // hosting NavigationStack pushes a new screen (e.g. terminal)
        // and pops back, iOS can finish/invalidate paused property
        // animators, leaving the blur snapped to its full effect — a
        // milky band sits over the affected rows. When we re-attach to
        // a window with no pinch in progress, reset the animator from
        // scratch so it scrubs cleanly back to nil.
        guard window != nil,
              !AgentBuddyPlatform.rendersAsMacApp,
              !UIAccessibility.isReduceTransparencyEnabled,
              scrollHost?.pinchActive != true
        else { return }
        fadeLink?.invalidate()
        fadeLink = nil
        tearDownPinchBlurAnimator()
        pinchBlur.effect = nil
        // [baozi-fork] Was `pinchBlurAnimator = makePinchBlurAnimator()`, which
        // left a paused animator sitting idle between pinches — iOS finishes it
        // out from under us during a NavigationStack push/pop and snaps the blur
        // to full, leaving a grey band over the row. Keep idle rows blur-free;
        // the pinch drive path rebuilds the animator + re-inserts the view.
        pinchBlur.removeFromSuperview()
        pinchBlurAnimator = nil
    }

    func updatePinchBlurAvailability() {
        if UIAccessibility.isReduceTransparencyEnabled {
            pinchBlur.removeFromSuperview()
            pinchBlur.effect = nil
            tearDownPinchBlurAnimator()
            return
        }

        if pinchBlur.superview == nil {
            if pinchHighlight.superview === self {
                insertSubview(pinchBlur, belowSubview: pinchHighlight)
            } else {
                insertSubview(pinchBlur, aboveSubview: hostClip)
            }
        }

        if pinchBlurAnimator == nil {
            pinchBlurAnimator = makePinchBlurAnimator()
        }
    }

    func tearDownPinchBlurAnimator() {
        guard let animator = pinchBlurAnimator else { return }
        animator.stopAnimation(false)
        animator.finishAnimation(at: .current)
        pinchBlurAnimator = nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        backgroundHostingController.view.frame = bounds
        actionsBackground.frame = bounds
        pinchBlur.frame = bounds
        pinchHighlight.frame = bounds.insetBy(dx: 4, dy: 2)

        let iconSize: CGFloat = 22
        leadingIconView.frame = CGRect(
            x: 24, y: (bounds.height - iconSize) / 2,
            width: iconSize, height: iconSize
        )
        trailingIconView.frame = CGRect(
            x: bounds.width - 24 - iconSize, y: (bounds.height - iconSize) / 2,
            width: iconSize, height: iconSize
        )

        // Two-layer layout:
        //   * `hostClip` is the visible window — at zoom 4 it's the
        //     screen-fit rectangle (offset by the chrome height at top
        //     and stopped short by the safe-area top at the bottom);
        //     at lower zooms it equals the SwiftUI natural height.
        //   * `hostingController.view` sits at (0, 0) inside `hostClip`
        //     and is sized to the SwiftUI content's natural height.
        //     When natural > clipHeight (long response preview at z4),
        //     the hosting view extends below the clip's bottom and is
        //     cut off there. The title at (0, 0) of the hosting view
        //     stays pinned to the top of the clip — never pushed up.
        let width = bounds.width
        guard width > 0 else { return }
        if hostHeightCachedWidth != width {
            hostHeightByZoom.removeAll(keepingCapacity: true)
            hostHeightCachedWidth = width
        }
        let pageFit = displayZoom == 4 && (scrollHost?.isPinching == false)
        let topPad: CGFloat = pageFit ? (scrollHost?.topInsetValue ?? 0) : 0
        let safeAreaTop: CGFloat = pageFit ? (scrollHost?.scrollViewSafeAreaTop ?? 0) : 0
        let naturalHeight = hostHeightByZoom[displayZoom] ?? measureHostHeight(width: width)
        let clipHeight: CGFloat = if pageFit {
            max(0, bounds.height - topPad - safeAreaTop)
        } else {
            naturalHeight
        }
        hostClip.frame = CGRect(
            x: offsetX, y: topPad, width: width, height: clipHeight
        )
        hostingController.view.frame = CGRect(
            x: 0, y: 0, width: width, height: naturalHeight
        )
    }

    /// Public wrapper — called by the scroll host when it needs a
    /// measurement on demand (e.g., the first `relayout` after a row
    /// is configured, before any implicit layoutSubviews pass).
    @discardableResult
    func forceMeasureHostHeight(width: CGFloat) -> CGFloat {
        measureHostHeight(width: width)
    }

    /// Measure the hosted SwiftUI view's natural height at the current
    /// `displayZoom`. Caches the result so repeated layouts during a
    /// pinch don't re-measure. Must be called only after `rootView` has
    /// been set via `refreshRootView`.
    @discardableResult
    func measureHostHeight(width: CGFloat) -> CGFloat {
        // Give the host a tall sizing frame so sizeThatFits reports the
        // true intrinsic, not a compressed version.
        hostingController.view.frame = CGRect(x: offsetX, y: 0, width: width, height: 10_000)
        hostingController.view.setNeedsLayout()
        hostingController.view.layoutIfNeeded()
        let size = hostingController.sizeThatFits(
            in: CGSize(width: width, height: .greatestFiniteMagnitude)
        )
        let h = max(12, size.height)
        hostHeightByZoom[displayZoom] = h
        if displayZoom == 4 {
            cachedNaturalHeight = h
            cachedMeasureWidth = width
        }
        return h
    }
}
