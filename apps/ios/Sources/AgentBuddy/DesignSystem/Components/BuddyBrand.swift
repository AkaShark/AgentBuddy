import SwiftUI

// MARK: - Brand mark

/// Two opposing rounded brackets joined by a diagonal link on a 24 × 24 grid.
/// The same master geometry is used by the app icons and Android brand mark.
struct BuddyLinkShape: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / 24
        let origin = CGPoint(x: rect.midX - 12 * scale, y: rect.midY - 12 * scale)
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: origin.x + x * scale, y: origin.y + y * scale)
        }
        var path = Path()
        path.move(to: p(13, 5))
        path.addLine(to: p(9, 5))
        path.addCurve(to: p(5, 9), control1: p(6.79, 5), control2: p(5, 6.79))
        path.addLine(to: p(5, 13))
        path.move(to: p(11, 19))
        path.addLine(to: p(15, 19))
        path.addCurve(to: p(19, 15), control1: p(17.21, 19), control2: p(19, 17.21))
        path.addLine(to: p(19, 11))
        path.move(to: p(10, 14))
        path.addLine(to: p(14, 10))
        return path
    }
}

/// App identity tile, using the current theme's brand surface and ink.
struct BuddyBrandMark: View {
    var size: CGFloat = BuddySize.brandMark

    var body: some View {
        BuddyIconTile(
            content: .brandMark,
            fill: AgentBuddyTheme.brand,
            foreground: AgentBuddyTheme.onBrand,
            size: size
        )
    }
}

/// Localized wordmark with the app mark.
struct BuddyWordmark: View {
    var markSize: CGFloat = BuddySize.brandMark

    var body: some View {
        HStack(spacing: BuddySpacing.sm) {
            BuddyBrandMark(size: markSize)
            Text("AgentBuddy")
                .buddyText(.title)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}
