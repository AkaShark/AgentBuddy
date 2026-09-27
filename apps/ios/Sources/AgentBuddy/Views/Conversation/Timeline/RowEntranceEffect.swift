import SwiftUI

struct RowEntranceModifier: ViewModifier {
    let isAssistantRow: Bool

    func body(content: Content) -> some View {
        if isAssistantRow {
            // The streaming markdown renderer scopes its own
            // `transaction { $0.animation = nil }` internally so that token
            // reveals don't replay on every snapshot. Leaving the row itself
            // unscoped lets sibling layout changes (e.g. a tool card collapse)
            // animate this row's position.
            content
        } else {
            content
                .transition(.asymmetric(
                    insertion: .rowEntranceReveal,
                    removal: .opacity
                ))
        }
    }
}

struct RowEntranceEffect: ViewModifier, Animatable {
    var progress: CGFloat
    var yOffset: CGFloat
    var minScale: CGFloat
    var maxBlur: CGFloat

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let clampedProgress = min(max(progress, 0), 1)
        let revealProgress = max(clampedProgress, 0.001)

        content
            .compositingGroup()
            .scaleEffect(
                x: 1,
                y: minScale + ((1 - minScale) * clampedProgress),
                anchor: .topLeading
            )
            .offset(y: yOffset * (1 - clampedProgress))
            .opacity(clampedProgress)
            .blur(radius: maxBlur * (1 - clampedProgress))
            .mask(alignment: .topLeading) {
                Rectangle()
                    .scaleEffect(x: 1, y: revealProgress, anchor: .topLeading)
            }
    }
}

extension AnyTransition {
    static var rowEntranceReveal: AnyTransition {
        .modifier(
            active: RowEntranceEffect(progress: 0, yOffset: 10, minScale: 0.965, maxBlur: 2.5),
            identity: RowEntranceEffect(progress: 1, yOffset: 0, minScale: 1, maxBlur: 0)
        )
    }

    static var sectionReveal: AnyTransition {
        .modifier(
            active: RowEntranceEffect(progress: 0, yOffset: 6, minScale: 0.985, maxBlur: 1.2),
            identity: RowEntranceEffect(progress: 1, yOffset: 0, minScale: 1, maxBlur: 0)
        )
    }
}
