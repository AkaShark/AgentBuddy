import UIKit

// MARK: - Selection overlay

/// Painted selection state: a highlight rectangle per row of the range,
/// plus circular drag handles at the start/end cell corners. Ghostty
/// doesn't paint our long-press selection itself; we draw it on top of
/// the Metal surface using cell metrics returned by the renderer.
final class TerminalSelectionOverlayView: UIView {
    private static let handleRadius: CGFloat = 7.0
    private static let handleHitRadius: CGFloat = 28.0
    private static let highlightInsetRatio: CGFloat = 0.06

    enum Handle: Equatable {
        case start
        case end
    }

    /// Range + metrics used to paint. Both must be set for anything to
    /// draw; clearing either erases the overlay.
    var range: TerminalCellRange? {
        didSet {
            guard oldValue != range else { return }
            setNeedsDisplay()
        }
    }

    var metrics: TerminalCellMetrics? {
        didSet { setNeedsDisplay() }
    }

    /// Called as the user drags a handle. The host view extends the
    /// selection range accordingly.
    var onHandleDrag: ((Handle, CGPoint, UIGestureRecognizer.State) -> Void)?

    private let handlePan = UIPanGestureRecognizer()
    private var activeHandle: Handle?
    private var contentScale: CGFloat = UIScreen.main.scale

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    func setContentScale(_ scale: CGFloat) {
        contentScale = max(scale, 1.0)
        setNeedsDisplay()
    }

    private func configure() {
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = true
        handlePan.addTarget(self, action: #selector(handlePanGesture(_:)))
        addGestureRecognizer(handlePan)
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        // Only swallow touches that land on a handle; everything else
        // falls through to the underlying terminal so taps, long-presses,
        // and scroll pans still hit it.
        handle(at: point) != nil
    }

    /// Convert the overlay's points to viewport pixel space (matches the
    /// coords stored in `TerminalCellMetrics`).
    private func cellRectInPoints(row: UInt32, startCol: UInt32, endCol: UInt32) -> CGRect? {
        guard let metrics else { return nil }
        guard contentScale > 0 else { return nil }
        let cellWidthPt = CGFloat(metrics.cellWidthPx) / contentScale
        let cellHeightPt = CGFloat(metrics.cellHeightPx) / contentScale
        let firstCol = CGFloat(startCol)
        let lastColInclusive = CGFloat(endCol)
        let width = (lastColInclusive - firstCol + 1) * cellWidthPt
        let rect = CGRect(
            x: firstCol * cellWidthPt,
            y: CGFloat(row) * cellHeightPt,
            width: width,
            height: cellHeightPt
        )
        return rect.insetBy(dx: 0, dy: max(1, cellHeightPt * Self.highlightInsetRatio))
    }

    override func draw(_ rect: CGRect) {
        guard let range, let metrics, metrics.cols > 0 else { return }
        let normalized = normalizedRange(range)
        UIColor.systemBlue.withAlphaComponent(0.30).setFill()
        let lastCol = metrics.cols > 0 ? metrics.cols - 1 : 0
        for row in normalized.start.row...normalized.end.row {
            let firstCol = row == normalized.start.row ? normalized.start.col : 0
            let endCol = row == normalized.end.row ? normalized.end.col : lastCol
            guard endCol >= firstCol else { continue }
            if let cellRect = cellRectInPoints(row: row, startCol: firstCol, endCol: endCol) {
                UIBezierPath(roundedRect: cellRect, cornerRadius: 2).fill()
            }
        }
        drawHandles(for: normalized)
    }

    /// Union rect of all selection rows in the overlay's coordinate space.
    /// Used by the edit menu to anchor itself above the selected text.
    func selectionUnionRect() -> CGRect? {
        guard let range, let metrics, metrics.cols > 0 else { return nil }
        let normalized = normalizedRange(range)
        let lastCol = metrics.cols > 0 ? metrics.cols - 1 : 0
        var union: CGRect = .null
        for row in normalized.start.row...normalized.end.row {
            let firstCol = row == normalized.start.row ? normalized.start.col : 0
            let endCol = row == normalized.end.row ? normalized.end.col : lastCol
            guard endCol >= firstCol else { continue }
            if let cellRect = cellRectInPoints(row: row, startCol: firstCol, endCol: endCol) {
                union = union.union(cellRect)
            }
        }
        if union.isNull { return nil }
        return union.insetBy(dx: 0, dy: -6)
    }

    private func drawHandles(for range: TerminalCellRange) {
        guard let metrics, metrics.cellWidthPx > 0, metrics.cellHeightPx > 0 else { return }
        let radius = Self.handleRadius
        let centers = handleCenters(for: range, metrics: metrics)
        UIColor.systemBlue.setFill()
        UIBezierPath(
            ovalIn: CGRect(
                x: centers.start.x - radius,
                y: centers.start.y - radius,
                width: radius * 2,
                height: radius * 2
            )
        ).fill()
        UIBezierPath(
            ovalIn: CGRect(
                x: centers.end.x - radius,
                y: centers.end.y - radius,
                width: radius * 2,
                height: radius * 2
            )
        ).fill()
    }

    private func handleCenters(for range: TerminalCellRange, metrics: TerminalCellMetrics)
        -> (start: CGPoint, end: CGPoint)
    {
        let cellWidthPt = CGFloat(metrics.cellWidthPx) / contentScale
        let cellHeightPt = CGFloat(metrics.cellHeightPx) / contentScale
        let start = CGPoint(
            x: CGFloat(range.start.col) * cellWidthPt,
            y: CGFloat(range.start.row + 1) * cellHeightPt
        )
        let end = CGPoint(
            x: CGFloat(range.end.col + 1) * cellWidthPt,
            y: CGFloat(range.end.row + 1) * cellHeightPt
        )
        return (clampCenter(start), clampCenter(end))
    }

    private func clampCenter(_ point: CGPoint) -> CGPoint {
        let radius = Self.handleRadius
        let maxX = max(radius, bounds.width - radius)
        let maxY = max(radius, bounds.height - radius)
        return CGPoint(
            x: min(max(point.x, radius), maxX),
            y: min(max(point.y, radius), maxY)
        )
    }

    @objc private func handlePanGesture(_ gesture: UIPanGestureRecognizer) {
        let location = gesture.location(in: self)
        switch gesture.state {
        case .began:
            activeHandle = handle(at: location)
            if let activeHandle {
                onHandleDrag?(activeHandle, location, .began)
            }
        case .changed, .ended, .cancelled, .failed:
            guard let activeHandle else { return }
            onHandleDrag?(activeHandle, location, gesture.state)
            if gesture.state == .ended || gesture.state == .cancelled || gesture.state == .failed {
                self.activeHandle = nil
            }
        default:
            break
        }
    }

    private func handle(at point: CGPoint) -> Handle? {
        guard let range, let metrics else { return nil }
        let centers = handleCenters(for: normalizedRange(range), metrics: metrics)
        let dStart = hypot(point.x - centers.start.x, point.y - centers.start.y)
        let dEnd = hypot(point.x - centers.end.x, point.y - centers.end.y)
        let hit = Self.handleHitRadius
        switch (dStart <= hit, dEnd <= hit) {
        case (true, true):
            return dStart <= dEnd ? .start : .end
        case (true, false):
            return .start
        case (false, true):
            return .end
        default:
            return nil
        }
    }

    /// Sort start ≤ end so painting and handle placement are direction-
    /// agnostic. The caller is allowed to push an "inverted" range when
    /// the user drags right-to-left.
    private func normalizedRange(_ range: TerminalCellRange) -> TerminalCellRange {
        let startBeforeEnd =
            range.start.row < range.end.row ||
            (range.start.row == range.end.row && range.start.col <= range.end.col)
        if startBeforeEnd { return range }
        return TerminalCellRange(start: range.end, end: range.start, rectangle: range.rectangle)
    }
}
