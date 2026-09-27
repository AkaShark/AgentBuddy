import SwiftUI

// MARK: - Brand mark

/// The Lucide "cat" outline (ISC licence, see `artifacts/design/agentbuddy-ui-v1/spec/LUCIDE-LICENSE.txt`),
/// drawn on its 24×24 master grid and scaled to fit. Stroke it with round caps
/// and joins at ~1.8/24 of the size.
struct BuddyCatShape: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / 24
        let origin = CGPoint(
            x: rect.midX - 12 * scale,
            y: rect.midY - 12 * scale
        )
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: origin.x + x * scale, y: origin.y + y * scale)
        }
        var path = Path()
        // Head outline (the source arc is converted to cubic curves).
        path.move(to: p(12, 5))
        path.addCurve(to: p(14, 5.26), control1: p(12.67, 5), control2: p(13.35, 5.09))
        path.addCurve(to: p(20.42, 3), control1: p(15.78, 3.26), control2: p(19.03, 2.42))
        path.addCurve(to: p(20, 10), control1: p(21.82, 3.58), control2: p(20, 10))
        path.addCurve(to: p(21, 13.44), control1: p(20.57, 11.07), control2: p(21, 12.24))
        path.addCurve(to: p(12, 21), control1: p(21, 17.9), control2: p(16.97, 21))
        path.addCurve(to: p(3, 13.44), control1: p(7.03, 21), control2: p(3, 18))
        path.addCurve(to: p(4, 10), control1: p(3, 12.19), control2: p(3.5, 11.04))
        path.addCurve(to: p(3.5, 3), control1: p(4, 10), control2: p(2.11, 3.58))
        path.addCurve(to: p(10, 5.23), control1: p(4.89, 2.42), control2: p(8.22, 3.23))
        path.addCurve(to: p(12, 5), control1: p(10.656, 5.079), control2: p(11.327, 5.002))
        path.closeSubpath()
        // Eyes.
        path.move(to: p(8, 14))
        path.addLine(to: p(8, 14.5))
        path.move(to: p(16, 14))
        path.addLine(to: p(16, 14.5))
        // Nose.
        path.move(to: p(11.25, 16.25))
        path.addLine(to: p(12.75, 16.25))
        path.addLine(to: p(12, 17))
        path.addLine(to: p(11.25, 16.25))
        path.closeSubpath()
        return path
    }
}

/// App mark: cat outline on an `action` tile (deep green in light mode,
/// mint in dark mode), as in the home header.
struct BuddyBrandMark: View {
    var size: CGFloat = 32

    var body: some View {
        BuddyIconTile(
            content: .brandMark,
            fill: AgentBuddyTheme.action,
            foreground: AgentBuddyTheme.onAction,
            size: size
        )
    }
}

/// "搭子" wordmark with the app mark.
struct BuddyWordmark: View {
    var markSize: CGFloat = 32

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
