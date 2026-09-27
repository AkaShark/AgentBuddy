import UIKit

struct TerminalGridSize: Equatable {
    let cols: UInt16
    let rows: UInt16

    init(cols: UInt16, rows: UInt16) {
        self.cols = cols
        self.rows = rows
    }

    init(metrics: AgentBuddyGhosttySurfaceMetrics) {
        cols = UInt16(max(20, min(240, Int(metrics.columns))))
        rows = UInt16(max(4, min(120, Int(metrics.rows))))
    }

    static func metricsAreCurrent(
        _ metrics: AgentBuddyGhosttySurfaceMetrics,
        for size: CGSize,
        contentScale: CGFloat
    ) -> Bool {
        guard metrics.cellWidthPx > 0, metrics.cellHeightPx > 0 else { return false }
        let expectedWidth = Int(round(max(0, size.width * contentScale)))
        let expectedHeight = Int(round(max(0, size.height * contentScale)))
        let actualWidth = Int(metrics.widthPx)
        let actualHeight = Int(metrics.heightPx)
        return abs(actualWidth - expectedWidth) <= 2
            && abs(actualHeight - expectedHeight) <= 2
    }

    /// Derive cols/rows from the live cell metrics Ghostty reports. Pixel
    /// values come from `ghostty_surface_size`, view bounds come from
    /// SwiftUI; divide the latter (in pixels) by the former to get a
    /// grid that lines up exactly with what Ghostty paints.
    init(size: CGSize, contentScale: CGFloat, cellWidthPx: CGFloat, cellHeightPx: CGFloat) {
        let widthPx = max(0, size.width * contentScale)
        let heightPx = max(0, size.height * contentScale)
        let computedCols = Int(floor(widthPx / max(cellWidthPx, 1)))
        let computedRows = Int(floor(heightPx / max(cellHeightPx, 1)))
        cols = UInt16(max(20, min(240, computedCols)))
        rows = UInt16(max(4, min(120, computedRows)))
    }

    /// First-frame fallback used before the renderer has produced metrics.
    /// Estimate cell dimensions from the chosen font size so the initial
    /// PTY grid is in the right ballpark across the 10–24 pt range.
    init(estimatedFor size: CGSize, fontSize: Double) {
        let cellWidth = max(6.0, fontSize * 0.6)
        let cellHeight = max(12.0, fontSize * 1.31)
        let contentWidth = max(0, size.width)
        let contentHeight = max(0, size.height)
        let computedCols = Int(contentWidth / cellWidth)
        let computedRows = Int(contentHeight / cellHeight)
        cols = UInt16(max(20, min(240, computedCols)))
        rows = UInt16(max(4, min(120, computedRows)))
    }
}
