import AVKit
import CoreMedia
import CoreVideo
import Observation
import SwiftUI
import UIKit

/// Top-level controller for the streaming-assistant Picture-in-Picture
/// window. Owns the `AVPictureInPictureController` + sample-buffer host
/// view and drives the render loop. Singleton instance lives on `AppDelegate`.
///
/// Rendering pipeline:
///   PiPContentView (SwiftUI) → ImageRenderer.cgImage → CVPixelBuffer
///                            → CMSampleBuffer → AVSampleBufferDisplayLayer
///
/// Push cadence: 4 Hz. We always push a fresh frame — the view body reads
/// `AppModel.shared.snapshot` directly so each render reflects current state.
@MainActor
@Observable
final class StreamingPiPController: NSObject {
    static let shared = StreamingPiPController()

    /// Mirrored to the toolbar button so it can render `pip.fill` while open.
    private(set) var isActive: Bool = false

    /// Surfaces a non-fatal startup error to UI (e.g. unsupported device).
    private(set) var lastErrorMessage: String?

    /// When set, PiP renders this specific thread regardless of which thread
    /// is currently active in the app. `nil` falls back to the active thread.
    /// Cleared when PiP closes.
    private(set) var pinnedThreadKey: ThreadKey?

    /// PiP canvas width is fixed (in points); height grows with the card's
    /// content. PiP reads each sample buffer's format description to size
    /// the floating window, so changing dimensions per frame is how we
    /// resize.
    @ObservationIgnored let renderWidth: CGFloat = 360
    /// Coarse vertical snap so the floating window doesn't jitter on every
    /// token. PiP-side animation handles the transition between steps.
    @ObservationIgnored let heightStep: CGFloat = 24
    /// Max push cadence — actual frames pushed are gated by `isDirty`, so
    /// idle cost is unchanged. Streaming updates can hit this ceiling.
    @ObservationIgnored let pushIntervalSeconds: TimeInterval = 1.0 / 30.0
    /// Native device scale so text is rendered at Retina resolution.
    /// Pixel-buffer dimensions and cgImage dimensions are points * scale.
    @ObservationIgnored let renderScale: CGFloat = UIScreen.main.scale
    /// Pool/buffer dimensions in pixels (not points). When the SwiftUI
    /// content's point size changes we recreate the pool with the matching
    /// pixel dimensions and trigger a PiP aspect transition.
    @ObservationIgnored var currentRenderSize: CGSize = {
        let scale = UIScreen.main.scale
        return CGSize(width: 360 * scale, height: 160 * scale)
    }()
    /// When set, height is locked to this value (clamped to PiPContentView
    /// min/max) regardless of the card's intrinsic content height. Set by
    /// the PiP skip ⏪/⏩ controls and cleared on PiP close.
    @ObservationIgnored var userHeightOverride: CGFloat?
    /// Set true whenever something the card depends on changes. The render
    /// timer only pushes a frame when this is true (or the 1 Hz heartbeat
    /// is due), so an idle PiP drops to ~zero CPU.
    @ObservationIgnored var isDirty = true
    @ObservationIgnored var lastPushTime: Date = .distantPast
    /// Bumped on every `endSession` so an in-flight `withObservationTracking`
    /// callback can detect it's for a stopped session and stop re-subscribing.
    @ObservationIgnored var observationGeneration: Int = 0
    /// Heartbeat cadence — push a frame at least this often even with no
    /// data changes so the elapsed-turn timer chip keeps ticking.
    @ObservationIgnored let heartbeatSeconds: TimeInterval = 1.0

    @ObservationIgnored private let audioKeeper = SilentAudioKeeper()
    @ObservationIgnored var hostView: PiPHostView?
    @ObservationIgnored private var pipController: AVPictureInPictureController?
    @ObservationIgnored private var possibleObservation: NSKeyValueObservation?
    @ObservationIgnored var renderTimer: Timer?
    @ObservationIgnored var pixelBufferPool: CVPixelBufferPool?
    /// Set by `start()` when we want PiP to begin as soon as the controller's
    /// `isPictureInPicturePossible` flips true. Without this gate the first
    /// `startPictureInPicture()` after fresh setup is silently dropped because
    /// iOS hasn't acknowledged the new layer + audio session yet.
    @ObservationIgnored private var pendingStart = false

    /// SwiftUI body we ask `ImageRenderer` to rasterise every tick. The body
    /// reads `AppModel.shared` so each rasterisation reflects fresh state.
    @ObservationIgnored lazy var renderer: ImageRenderer<PiPContentView> = {
        let r = ImageRenderer(content: PiPContentView())
        // Width is fixed; nil height lets ImageRenderer use the SwiftUI
        // content's intrinsic height (clamped by PiPContentView's frame).
        r.proposedSize = ProposedViewSize(width: renderWidth, height: nil)
        // Render at native device scale. The cgImage we get back has
        // pixel dimensions = proposedSize (points) × scale.
        r.scale = renderScale
        return r
    }()

    // MARK: - Lifecycle

    var isSupported: Bool { AVPictureInPictureController.isPictureInPictureSupported() }

    func toggle() {
        if isActive { stop() } else { start() }
    }

    /// Open PiP pinned to a specific thread (used by the home-card menu).
    /// If PiP is already open for the same key, no-op; if open for a
    /// different key, repin without closing.
    func start(for threadKey: ThreadKey) {
        pinnedThreadKey = threadKey
        // Make sure the next tick re-renders even if the snapshot itself
        // didn't change — the pinned thread did.
        isDirty = true
        if isActive { return }
        start()
    }

    func start() {
        guard !isActive else { return }
        guard isSupported else {
            lastErrorMessage = "PiP not supported on this device."
            LLog.warn("pip", "start: device does not support PiP")
            return
        }
        guard ensureSetup() else {
            lastErrorMessage = "Could not initialize PiP."
            return
        }

        // Reset the display layer if it's lingering in a failed/idle state
        // from the previous session — without flushing, enqueue is a no-op
        // and `isPictureInPicturePossible` never flips true on reopen.
        hostView?.displayLayer.flushAndRemoveImage()
        audioKeeper.activate()
        // Push at least one frame so the layer has content before start.
        // PiP refuses to start against an empty display layer.
        pushFrame()
        startRenderTimer()
        pendingStart = true
        startIfPossible()
    }

    func stop() {
        pendingStart = false
        // didStop delegate handles per-session cleanup (timer + audio).
        // Host view + controller + pool persist for the next session.
        pipController?.stopPictureInPicture()
    }

    /// Calls `startPictureInPicture()` if (a) we have a pending start and
    /// (b) the controller currently reports `isPictureInPicturePossible`.
    /// Otherwise we wait — the KVO observation in `ensureSetup()` will call
    /// back here once iOS finishes wiring up the new layer + audio session.
    private func startIfPossible() {
        guard pendingStart, let controller = pipController else { return }
        guard controller.isPictureInPicturePossible else { return }
        pendingStart = false
        controller.startPictureInPicture()
        LLog.info("pip", "startPictureInPicture invoked")
    }

    /// Per-session cleanup invoked from `pictureInPictureControllerDidStop`.
    /// Intentionally keeps `hostView`, `pipController`, and `pixelBufferPool`
    /// alive — recreating them across sessions races with iOS's PiP slide-out
    /// animation and is the source of the second-tap crash.
    private func endSession() {
        // Invalidate any in-flight observation callback so it doesn't
        // resubscribe after we've torn the session down.
        observationGeneration &+= 1
        renderTimer?.invalidate()
        renderTimer = nil
        audioKeeper.deactivate()
        isActive = false
        pinnedThreadKey = nil
        userHeightOverride = nil
        isDirty = false
        lastPushTime = .distantPast
    }

    // MARK: - One-time setup

    /// Lazily builds the persistent host view + controller + pool. Returns
    /// `false` only if there is no key window to host the layer.
    private func ensureSetup() -> Bool {
        if pipController != nil { return true }
        guard let window = keyWindow() else {
            LLog.warn("pip", "ensureSetup: key window unavailable")
            return false
        }
        let host = PiPHostView(frame: CGRect(x: -1, y: -1, width: 1, height: 1))
        window.addSubview(host)
        hostView = host
        ensurePixelBufferPool()

        let source = AVPictureInPictureController.ContentSource(
            sampleBufferDisplayLayer: host.displayLayer,
            playbackDelegate: self
        )
        let controller = AVPictureInPictureController(contentSource: source)
        controller.delegate = self
        // requiresLinearPlayback = false exposes the chevron skip buttons in
        // PiP's control bar. We repurpose them as shrink/grow controls via
        // the `skipByInterval` delegate callback.
        controller.requiresLinearPlayback = false
        pipController = controller
        // KVO so `start()` can defer until iOS reports readiness.
        possibleObservation = controller.observe(
            \.isPictureInPicturePossible,
            options: [.new]
        ) { [weak self] _, change in
            guard change.newValue == true else { return }
            Task { @MainActor [weak self] in self?.startIfPossible() }
        }
        return true
    }

    private func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
    }
}

// MARK: - AVPictureInPictureControllerDelegate

extension StreamingPiPController: AVPictureInPictureControllerDelegate {
    nonisolated func pictureInPictureControllerDidStartPictureInPicture(
        _ controller: AVPictureInPictureController
    ) {
        Task { @MainActor in
            self.isActive = true
            LLog.info("pip", "didStartPictureInPicture")
        }
    }

    nonisolated func pictureInPictureControllerDidStopPictureInPicture(
        _ controller: AVPictureInPictureController
    ) {
        Task { @MainActor in
            LLog.info("pip", "didStopPictureInPicture")
            self.endSession()
        }
    }

    nonisolated func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        failedToStartPictureInPictureWithError error: any Error
    ) {
        Task { @MainActor in
            LLog.warn("pip", "failedToStartPictureInPicture", fields: ["error": "\(error)"])
            self.lastErrorMessage = error.localizedDescription
            self.endSession()
        }
    }
}

// MARK: - AVPictureInPictureSampleBufferPlaybackDelegate

extension StreamingPiPController: AVPictureInPictureSampleBufferPlaybackDelegate {
    nonisolated func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        setPlaying playing: Bool
    ) {}

    nonisolated func pictureInPictureControllerTimeRangeForPlayback(
        _ controller: AVPictureInPictureController
    ) -> CMTimeRange {
        // iOS disables the skip chevrons when the playback range is
        // infinite (it treats the source as a live stream). We don't
        // actually have media to scrub; we just need iOS to think there
        // are seekable bounds with the playhead in the middle so both
        // chevrons enable — we repurpose them as shrink/grow controls
        // in `skipByInterval`.
        //
        // The window must follow the actual playhead: our PTSs come from
        // `CMClockGetHostTimeClock`, so anchor the window on that same
        // clock. A static [-1800, +1800] would put the (huge, host-time)
        // playhead past the end, which is why forward-skip was disabled.
        let now = CMClockGetTime(CMClockGetHostTimeClock())
        let halfWindow = CMTime(seconds: 1800, preferredTimescale: 1)
        return CMTimeRange(
            start: now - halfWindow,
            duration: halfWindow + halfWindow
        )
    }

    nonisolated func pictureInPictureControllerIsPlaybackPaused(
        _ controller: AVPictureInPictureController
    ) -> Bool {
        false
    }

    nonisolated func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        didTransitionToRenderSize newRenderSize: CMVideoDimensions
    ) {}

    nonisolated func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        skipByInterval skipInterval: CMTime,
        completion completionHandler: @escaping () -> Void
    ) {
        // Fire the completion synchronously so iOS doesn't hold the PiP
        // control bar in a "skipping…" state while we do our resize work.
        completionHandler()
        // We don't care about the interval magnitude (iOS picks it for
        // hypothetical video content); just the sign — grow vs shrink.
        let delta = skipInterval.seconds >= 0 ? 1 : -1
        Task { @MainActor in
            self.adjustHeight(byTaps: delta)
        }
    }
}
