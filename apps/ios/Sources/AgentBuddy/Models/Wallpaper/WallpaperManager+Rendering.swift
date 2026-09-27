import SwiftUI
import UIKit

extension WallpaperManager {
    // MARK: - Wallpaper Image Generation

    func generateWallpaper(themeSlug: String, themeManager: ThemeManager) -> UIImage? {
        if let cached = imageCache[themeSlug] { return cached }

        guard let entry = themeManager.themeIndex.first(where: { $0.slug == themeSlug }) else {
            return nil
        }

        let bgColor = UIColor(Color(hex: entry.backgroundHex))
        let accentColor = UIColor(Color(hex: entry.accentHex))
        let patternIndex = abs(themeSlug.hashValue) % PatternType.allCases.count
        let pattern = PatternType.allCases[patternIndex]

        let size = CGSize(width: 390, height: 844) // Standard phone size
        let renderer = UIGraphicsImageRenderer(size: size)

        let image = renderer.image { ctx in
            let rect = CGRect(origin: .zero, size: size)
            bgColor.setFill()
            ctx.fill(rect)

            let patternColor = accentColor.withAlphaComponent(0.10)
            patternColor.setStroke()
            patternColor.setFill()

            let context = ctx.cgContext
            context.setLineWidth(1.0)

            switch pattern {
            case .dotGrid:
                drawDotGrid(in: context, size: size, color: patternColor)
            case .diagonalLines:
                drawDiagonalLines(in: context, size: size, color: patternColor)
            case .concentricCircles:
                drawConcentricCircles(in: context, size: size, color: patternColor)
            case .hexagonalMesh:
                drawHexagonalMesh(in: context, size: size, color: patternColor)
            case .crossHatch:
                drawCrossHatch(in: context, size: size, color: patternColor)
            case .waveLines:
                drawWaveLines(in: context, size: size, color: patternColor)
            }
        }

        imageCache[themeSlug] = image
        return image
    }

    func generateThumbnail(for entry: ThemeIndexEntry) -> UIImage {
        let bgColor = UIColor(Color(hex: entry.backgroundHex))
        let accentColor = UIColor(Color(hex: entry.accentHex))
        let patternIndex = abs(entry.slug.hashValue) % PatternType.allCases.count
        let pattern = PatternType.allCases[patternIndex]

        let size = CGSize(width: 80, height: 120)
        let renderer = UIGraphicsImageRenderer(size: size)

        return renderer.image { ctx in
            let rect = CGRect(origin: .zero, size: size)
            bgColor.setFill()
            ctx.fill(rect)

            let patternColor = accentColor.withAlphaComponent(0.12)
            patternColor.setStroke()
            patternColor.setFill()

            let context = ctx.cgContext
            context.setLineWidth(0.5)

            switch pattern {
            case .dotGrid:
                drawDotGrid(in: context, size: size, color: patternColor)
            case .diagonalLines:
                drawDiagonalLines(in: context, size: size, color: patternColor)
            case .concentricCircles:
                drawConcentricCircles(in: context, size: size, color: patternColor)
            case .hexagonalMesh:
                drawHexagonalMesh(in: context, size: size, color: patternColor)
            case .crossHatch:
                drawCrossHatch(in: context, size: size, color: patternColor)
            case .waveLines:
                drawWaveLines(in: context, size: size, color: patternColor)
            }
        }
    }

    func wallpaperImage(for config: WallpaperConfig?, scope: WallpaperScope?, themeManager: ThemeManager) -> UIImage? {
        guard let config = config else { return nil }

        switch config.type {
        case .none:
            return nil
        case .theme:
            guard let slug = config.themeSlug else { return nil }
            return generateWallpaper(themeSlug: slug, themeManager: themeManager)
        case .customImage:
            return loadCustomImage(for: scope)
        case .solidColor:
            guard let hex = config.colorHex else { return nil }
            return generateSolidColor(hex: hex)
        case .customVideo, .videoUrl:
            return nil
        }
    }

    private func generateSolidColor(hex: String) -> UIImage {
        let color = UIColor(Color(hex: hex))
        let size = CGSize(width: 1, height: 1)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { ctx in
            color.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
        }
    }

    // MARK: - Pattern Drawing

    private func drawDotGrid(in context: CGContext, size: CGSize, color: UIColor) {
        let spacing: CGFloat = 20
        let radius: CGFloat = 2
        color.setFill()
        var y: CGFloat = spacing / 2
        while y < size.height {
            var x: CGFloat = spacing / 2
            while x < size.width {
                context.fillEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
                x += spacing
            }
            y += spacing
        }
    }

    private func drawDiagonalLines(in context: CGContext, size: CGSize, color: UIColor) {
        let spacing: CGFloat = 16
        color.setStroke()
        context.setLineWidth(1.0)
        let total = size.width + size.height
        var offset: CGFloat = -size.height
        while offset < total {
            context.move(to: CGPoint(x: offset, y: 0))
            context.addLine(to: CGPoint(x: offset + size.height, y: size.height))
            offset += spacing
        }
        context.strokePath()
    }

    private func drawConcentricCircles(in context: CGContext, size: CGSize, color: UIColor) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let maxRadius = max(size.width, size.height)
        let spacing: CGFloat = 24
        color.setStroke()
        context.setLineWidth(0.8)
        var r: CGFloat = spacing
        while r < maxRadius {
            context.addEllipse(in: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2))
            r += spacing
        }
        context.strokePath()
    }

    private func drawHexagonalMesh(in context: CGContext, size: CGSize, color: UIColor) {
        let hexSize: CGFloat = 18
        let w = hexSize * 2
        let h = sqrt(3) * hexSize
        color.setStroke()
        context.setLineWidth(0.8)

        var row = 0
        var y: CGFloat = 0
        while y < size.height + h {
            let xOffset: CGFloat = (row % 2 == 0) ? 0 : w * 0.75
            var x: CGFloat = xOffset
            while x < size.width + w {
                drawHexagon(in: context, center: CGPoint(x: x, y: y), size: hexSize)
                x += w * 1.5
            }
            y += h / 2
            row += 1
        }
        context.strokePath()
    }

    private func drawHexagon(in context: CGContext, center: CGPoint, size: CGFloat) {
        for i in 0..<6 {
            let angle = CGFloat.pi / 3 * CGFloat(i) - CGFloat.pi / 6
            let point = CGPoint(x: center.x + size * cos(angle), y: center.y + size * sin(angle))
            if i == 0 {
                context.move(to: point)
            } else {
                context.addLine(to: point)
            }
        }
        context.closePath()
    }

    private func drawCrossHatch(in context: CGContext, size: CGSize, color: UIColor) {
        let spacing: CGFloat = 16
        color.setStroke()
        context.setLineWidth(0.8)

        // Forward diagonals
        let total = size.width + size.height
        var offset: CGFloat = -size.height
        while offset < total {
            context.move(to: CGPoint(x: offset, y: 0))
            context.addLine(to: CGPoint(x: offset + size.height, y: size.height))
            offset += spacing
        }
        // Backward diagonals
        offset = 0
        while offset < total {
            context.move(to: CGPoint(x: offset, y: 0))
            context.addLine(to: CGPoint(x: offset - size.height, y: size.height))
            offset += spacing
        }
        context.strokePath()
    }

    private func drawWaveLines(in context: CGContext, size: CGSize, color: UIColor) {
        let spacing: CGFloat = 20
        let amplitude: CGFloat = 6
        let wavelength: CGFloat = 40
        color.setStroke()
        context.setLineWidth(0.8)

        var y: CGFloat = spacing / 2
        while y < size.height {
            context.move(to: CGPoint(x: 0, y: y))
            var x: CGFloat = 0
            while x <= size.width {
                let dy = sin(x / wavelength * 2 * .pi) * amplitude
                context.addLine(to: CGPoint(x: x, y: y + dy))
                x += 2
            }
            y += spacing
        }
        context.strokePath()
    }
}
