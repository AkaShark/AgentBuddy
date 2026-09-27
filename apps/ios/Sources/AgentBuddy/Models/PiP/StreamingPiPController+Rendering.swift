import AVKit
import CoreMedia
import CoreVideo
import Observation
import SwiftUI
import UIKit

extension StreamingPiPController {
    // MARK: - Render loop

    func startRenderTimer() {
        renderTimer?.invalidate()
        let timer = Timer(timeInterval: pushIntervalSeconds, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tickRender() }
        }
        RunLoop.main.add(timer, forMode: .common)
        renderTimer = timer
        // Begin observing AppModel; any snapshot change will mark dirty
        // so the next tick pushes a frame.
        isDirty = true
        observationGeneration &+= 1
        observeSnapshotChanges(generation: observationGeneration)
    }

    /// Timer callback. Pushes a frame only when (a) something the card
    /// reads has actually changed or (b) the 1 Hz heartbeat is due.
    private func tickRender() {
        let dueForHeartbeat =
            Date().timeIntervalSince(lastPushTime) >= heartbeatSeconds
        guard isDirty || dueForHeartbeat else { return }
        pushFrame()
    }

    /// Subscribes to `AppModel.shared.snapshot`. When the snapshot is
    /// replaced (which AppModel does once per debounced batch of bridge
    /// events), `onChange` fires, we set `isDirty`, and re-subscribe.
    private func observeSnapshotChanges(generation: Int) {
        withObservationTracking {
            _ = AppModel.shared.snapshot
        } onChange: { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, self.observationGeneration == generation else { return }
                self.isDirty = true
                self.observeSnapshotChanges(generation: generation)
            }
        }
    }

    func pushFrame() {
        guard let host = hostView else { return }
        // Recover from a failed layer state — happens after some PiP
        // transitions / app lifecycle events. Without this, enqueue is a
        // silent no-op and the window appears frozen.
        if host.displayLayer.status == .failed {
            LLog.warn(
                "pip",
                "display layer failed; flushing",
                fields: [
                    "error": host.displayLayer.error?.localizedDescription ?? "unknown"
                ]
            )
            host.displayLayer.flushAndRemoveImage()
        }
        // Skip when the layer's queue is full; rendering + buffer alloc
        // are the expensive part, so bail before paying that cost.
        guard host.displayLayer.isReadyForMoreMediaData else { return }
        // Reassign content each tick so ImageRenderer doesn't reuse a cached
        // render — particularly important on a fresh session where the
        // observed state changed but the renderer instance is the same.
        renderer.content = PiPContentView()

        let targetPixels: CGSize
        let finalImage: CGImage
        if isDirty {
            // Content may have changed height: measure (Phase 1) in points,
            // snap, then render at the snapped point-size (Phase 2) if
            // different. cgImage dimensions are points * renderScale.
            renderer.proposedSize = ProposedViewSize(width: renderWidth, height: nil)
            guard let measured = renderer.cgImage else { return }
            let measuredHeightPoints = CGFloat(measured.height) / renderScale
            let targetPoints = adaptedSize(forIntrinsicHeight: measuredHeightPoints)
            targetPixels = CGSize(
                width: targetPoints.width * renderScale,
                height: targetPoints.height * renderScale
            )
            if abs(measuredHeightPoints - targetPoints.height) < 0.5 {
                finalImage = measured
            } else {
                renderer.proposedSize = ProposedViewSize(width: renderWidth, height: targetPoints.height)
                guard let snapped = renderer.cgImage else { return }
                finalImage = snapped
            }
        } else {
            // Heartbeat-only push (idle PiP, just keeping the timer chip
            // alive). Reuse the cached pixel size and skip the measurement
            // render — one rasterisation instead of two.
            targetPixels = currentRenderSize
            let heightPoints = max(PiPContentView.minHeight, targetPixels.height / renderScale)
            renderer.proposedSize = ProposedViewSize(width: renderWidth, height: heightPoints)
            guard let img = renderer.cgImage else { return }
            finalImage = img
        }
        if targetPixels != currentRenderSize {
            // Format description is about to change. Without flushing,
            // the layer's queue may refuse to accept the new-format
            // buffer and `isReadyForMoreMediaData` stays false — which
            // is the "stops updating after I resize" symptom.
            host.displayLayer.flush()
            currentRenderSize = targetPixels
            pixelBufferPool = nil
        }
        ensurePixelBufferPool(size: targetPixels)
        guard let pixelBuffer = makePixelBuffer(from: finalImage) else { return }
        guard let sampleBuffer = makeSampleBuffer(from: pixelBuffer) else { return }
        host.displayLayer.enqueue(sampleBuffer)
        // Only clear dirty + advance heartbeat once the frame actually
        // made it into the layer's queue. Early-returns above leave the
        // dirty flag intact so the next tick retries.
        isDirty = false
        lastPushTime = Date()
    }

    /// Snaps the SwiftUI-measured height up to the next multiple of
    /// `heightStep` and clamps it to PiPContentView's min/max. When the
    /// user has tapped a skip control we honour that height verbatim
    /// (still clamped) instead of letting content drive growth.
    /// Returns the final pixel-buffer canvas size.
    private func adaptedSize(forIntrinsicHeight rawHeight: CGFloat) -> CGSize {
        let base = userHeightOverride ?? rawHeight
        let clamped = min(
            PiPContentView.maxHeight,
            max(PiPContentView.minHeight, base)
        )
        let snapped = (ceil(clamped / heightStep) * heightStep)
        return CGSize(width: renderWidth, height: snapped)
    }

    /// Skip-button handler — `delta > 0` grows, `delta < 0` shrinks. We
    /// quantise to two `heightStep`s per tap so the change is perceptible
    /// without making the user mash the button.
    func adjustHeight(byTaps delta: Int) {
        let bump = CGFloat(delta) * heightStep * 2
        // userHeightOverride is in points; currentRenderSize is pixels, so
        // convert when falling back to it.
        let currentPoints = userHeightOverride ?? (currentRenderSize.height / renderScale)
        let target = min(
            PiPContentView.maxHeight,
            max(PiPContentView.minHeight, currentPoints + bump)
        )
        userHeightOverride = target
        isDirty = true
        // Push immediately so the resize feels instant rather than waiting
        // for the next timer tick.
        pushFrame()
    }

    // MARK: - Sample-buffer pipeline

    func ensurePixelBufferPool(size: CGSize? = nil) {
        guard pixelBufferPool == nil else { return }
        let target = size ?? currentRenderSize
        let attrs: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: Int(target.width),
            kCVPixelBufferHeightKey as String: Int(target.height),
            kCVPixelBufferIOSurfacePropertiesKey as String: [:] as CFDictionary
        ]
        var pool: CVPixelBufferPool?
        let status = CVPixelBufferPoolCreate(
            kCFAllocatorDefault,
            nil,
            attrs as CFDictionary,
            &pool
        )
        if status == kCVReturnSuccess { pixelBufferPool = pool }
    }

    private func makePixelBuffer(from image: CGImage) -> CVPixelBuffer? {
        guard let pool = pixelBufferPool else { return nil }
        var buffer: CVPixelBuffer?
        let status = CVPixelBufferPoolCreatePixelBuffer(nil, pool, &buffer)
        guard status == kCVReturnSuccess, let pixelBuffer = buffer else { return nil }

        CVPixelBufferLockBaseAddress(pixelBuffer, [])
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, []) }

        guard let base = CVPixelBufferGetBaseAddress(pixelBuffer) else { return nil }
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGImageAlphaInfo.premultipliedFirst.rawValue
            | CGBitmapInfo.byteOrder32Little.rawValue
        guard let ctx = CGContext(
            data: base,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: bitmapInfo
        ) else { return nil }

        ctx.setFillColor(UIColor.black.cgColor)
        ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return pixelBuffer
    }

    private func makeSampleBuffer(from pixelBuffer: CVPixelBuffer) -> CMSampleBuffer? {
        var formatDescription: CMFormatDescription?
        CMVideoFormatDescriptionCreateForImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescriptionOut: &formatDescription
        )
        guard let formatDescription else { return nil }

        let pts = CMClockGetTime(CMClockGetHostTimeClock())
        var timing = CMSampleTimingInfo(
            duration: CMTime(value: 1, timescale: 4),
            presentationTimeStamp: pts,
            decodeTimeStamp: .invalid
        )

        var sample: CMSampleBuffer?
        let status = CMSampleBufferCreateReadyWithImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescription: formatDescription,
            sampleTiming: &timing,
            sampleBufferOut: &sample
        )
        guard status == noErr, let sb = sample else { return nil }

        if let attachments = CMSampleBufferGetSampleAttachmentsArray(sb, createIfNecessary: true) {
            let dict = unsafeBitCast(
                CFArrayGetValueAtIndex(attachments, 0),
                to: CFMutableDictionary.self
            )
            CFDictionarySetValue(
                dict,
                Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(),
                Unmanaged.passUnretained(kCFBooleanTrue).toOpaque()
            )
        }
        return sb
    }
}
