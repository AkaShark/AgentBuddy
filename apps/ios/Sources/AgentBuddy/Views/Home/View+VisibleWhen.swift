import SwiftUI

/// Collapses a view to zero size + zero opacity when hidden, keeping
/// it in the tree (layout still runs). Used on zoom-gated card layers
/// so zoom transitions animate a frame-height interpolation rather
/// than materializing a new subtree.
///
/// When visible we use `maxHeight: nil` (natural sizing). Using
/// `.infinity` here causes every visible layer to compete for leftover
/// space inside a fixed-height container — at zoom 4 the card is sized
/// to a full screen page, so the layers spread apart and create gaps
/// between sections. With `nil`, layers stay tight against each other
/// and any leftover space falls below the last visible layer instead.
extension View {
    func visibleWhen(_ visible: Bool) -> some View {
        self
            .frame(maxHeight: visible ? nil : 0, alignment: .top)
            .opacity(visible ? 1 : 0)
            .clipped()
            .allowsHitTesting(visible)
    }
}
