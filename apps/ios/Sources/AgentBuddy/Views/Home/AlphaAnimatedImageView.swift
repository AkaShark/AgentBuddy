import ImageIO
import SwiftUI
import UIKit

struct AlphaAnimatedImageView: UIViewRepresentable {
    let fileURL: URL
    var repeatCount: Int = 0
    var onFinished: (() -> Void)?

    func makeUIView(context: Context) -> UIImageView {
        let imageView = UIImageView()
        imageView.backgroundColor = .clear
        imageView.isOpaque = false
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = false
        context.coordinator.configure(
            imageView,
            fileURL: fileURL,
            repeatCount: repeatCount,
            onFinished: onFinished
        )
        return imageView
    }

    func updateUIView(_ imageView: UIImageView, context: Context) {
        context.coordinator.configure(
            imageView,
            fileURL: fileURL,
            repeatCount: repeatCount,
            onFinished: onFinished
        )
    }

    static func dismantleUIView(_ imageView: UIImageView, coordinator: Coordinator) {
        coordinator.stop()
        imageView.image = nil
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator: NSObject, CAAnimationDelegate {
        private var configuredURL: URL?
        private var configuredRepeatCount: Int?
        private var onFinished: (() -> Void)?
        private weak var imageView: UIImageView?
        private var finishedFired = false

        // Frames are swapped via `CAKeyframeAnimation` on
        // `layer.contents` in `.discrete` mode. Core Animation runs
        // this on the render server with its own high-precision
        // clock, so frame transitions land exactly at the encoded
        // per-frame delays — no main-thread/display-link aliasing,
        // and no flat tempo from UIImageView's animationImages.
        private static let animationKey = "alphaFrames"

        func configure(
            _ imageView: UIImageView,
            fileURL: URL,
            repeatCount: Int,
            onFinished: (() -> Void)?
        ) {
            self.onFinished = onFinished
            self.imageView = imageView
            guard configuredURL != fileURL || configuredRepeatCount != repeatCount else { return }
            configuredURL = fileURL
            configuredRepeatCount = repeatCount
            finishedFired = false

            imageView.animationImages = nil
            imageView.stopAnimating()
            imageView.layer.removeAnimation(forKey: Coordinator.animationKey)

            let animation = AlphaAnimatedImageView.animation(from: fileURL)
            guard let first = animation.frames.first else {
                imageView.image = nil
                return
            }
            // Seeding `image` first sizes the layer (via UIImageView's
            // intrinsicContentSize/contentMode) and ensures
            // `layer.contents` has a sane fallback before/after the
            // animation runs.
            imageView.image = UIImage(cgImage: first)

            guard animation.frames.count > 1, animation.duration > 0 else {
                if repeatCount > 0, !finishedFired {
                    finishedFired = true
                    onFinished?()
                }
                return
            }

            let keyAnim = CAKeyframeAnimation(keyPath: "contents")
            keyAnim.values = animation.frames.map { $0 as Any }
            // Discrete mode wants one more keyTime than values:
            // values[i] is held over [keyTimes[i], keyTimes[i+1]).
            var keyTimes: [NSNumber] = [0.0]
            for end in animation.frameEndTimes {
                keyTimes.append(NSNumber(value: end / animation.duration))
            }
            keyAnim.keyTimes = keyTimes
            keyAnim.duration = animation.duration
            keyAnim.repeatCount = repeatCount > 0 ? Float(repeatCount) : .infinity
            keyAnim.calculationMode = .discrete
            keyAnim.fillMode = .forwards
            keyAnim.isRemovedOnCompletion = false
            keyAnim.delegate = self

            imageView.layer.add(keyAnim, forKey: Coordinator.animationKey)
        }

        func stop() {
            imageView?.layer.removeAnimation(forKey: Coordinator.animationKey)
        }

        func animationDidStop(_ anim: CAAnimation, finished: Bool) {
            guard finished, !finishedFired else { return }
            guard let repeats = configuredRepeatCount, repeats > 0 else { return }
            finishedFired = true
            onFinished?()
        }
    }

    private struct Animation {
        let frames: [CGImage]
        /// Cumulative end-time for each frame (frameEndTimes[i] is the
        /// timestamp at which frame i finishes / frame i+1 begins).
        let frameEndTimes: [TimeInterval]
        let duration: TimeInterval
    }

    /// Our iOS APNGs were authored at 10fps (100ms per frame), but the
    /// equivalent Android WebPs render at 15fps (67ms per frame) — so
    /// the same 165-frame entrance runs 16.5s on iOS vs 11.055s on
    /// Android. Force playback at the Android cadence by overriding
    /// the encoded delays. The source frames are uniform in both
    /// files, so a flat per-frame duration here is exact, not a
    /// resampling approximation.
    private static let playbackFrameDuration: TimeInterval = 1.0 / 15.0

    private static func animation(from url: URL) -> Animation {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else {
            return Animation(frames: [], frameEndTimes: [], duration: 0)
        }
        let count = CGImageSourceGetCount(source)
        var frames: [CGImage] = []
        var ends: [TimeInterval] = []
        frames.reserveCapacity(count)
        ends.reserveCapacity(count)
        var cumulative: TimeInterval = 0
        for index in 0..<count {
            guard let cgImage = CGImageSourceCreateImageAtIndex(source, index, nil) else { continue }
            frames.append(cgImage)
            cumulative += playbackFrameDuration
            ends.append(cumulative)
        }
        return Animation(
            frames: frames,
            frameEndTimes: ends,
            duration: max(cumulative, 0.1)
        )
    }

}
