import SwiftUI
import UIKit

// MARK: - SwiftUI entry point

/// UIKit-backed scroll view for the home dashboard's session list. Owns
/// pinch-to-zoom, horizontal row swipes, and vertical scroll directly so
/// gestures never fight each other (the SwiftUI `MagnifyGesture` +
/// `ScrollView` combo jittered because both consumed the same pan
/// deltas). Row content stays SwiftUI — each row hosts
/// `HomeSessionRowContent` inside a `UIHostingController`. UIKit animates
/// only the row's *container height* during a pinch, so SwiftUI does no
/// per-tick work.
struct HomeSessionsScrollView: UIViewRepresentable {
    struct Callbacks {
        var onOpen: (HomeDashboardRecentSession) -> Void
        var onReply: (HomeDashboardRecentSession) -> Void
        var onHide: (ThreadKey) -> Void
        var onPin: (ThreadKey) -> Void
        var onUnpin: (ThreadKey) -> Void
        var onCancelTurn: (HomeDashboardRecentSession) -> Void
        var onDelete: (HomeDashboardRecentSession) -> Void
        var onFork: (HomeDashboardRecentSession) -> Void
        var onShowPiP: (HomeDashboardRecentSession) -> Void
    }

    let sessions: [HomeDashboardRecentSession]
    let pinnedThreadKeys: Set<SavedThreadsStore.PinnedKey>
    let hydratingKeys: Set<String>
    let cancellingKeys: Set<String>
    let openingKey: ThreadKey?
    @Binding var zoomLevel: Int
    let showCatFooter: Bool
    let topInset: CGFloat
    let bottomInset: CGFloat
    let callbacks: Callbacks
    /// App's text scale from `@Environment(\.textScale)`. Piped in so
    /// row height measurements (which depend on rendered font sizes)
    /// can be invalidated when the user changes their text size in
    /// Appearance settings. Pass this in from the caller with
    /// `@Environment(\.textScale) private var textScale`.
    @Environment(\.textScale) private var textScale
    @Environment(ThemeManager.self) private var themeManager
    @Environment(WallpaperManager.self) private var wallpaperManager

    func makeUIView(context: Context) -> HomeSessionsScrollUIView {
        HomeSessionsScrollUIView()
    }

    func updateUIView(_ view: HomeSessionsScrollUIView, context: Context) {
        view.zoomCommit = { newZoom in
            if zoomLevel != newZoom { zoomLevel = newZoom }
        }
        // Propagate the SwiftUI `\.textScale` environment through the
        // hosting boundary. Without this, changing text size in settings
        // alters the rendered SwiftUI layout but the hosted controllers
        // inside each row wouldn't inherit the new value (UIHostingController
        // does not forward parent environment into its own tree).
        view.apply(
            sessions: sessions,
            pinnedThreadKeys: pinnedThreadKeys,
            hydratingKeys: hydratingKeys,
            cancellingKeys: cancellingKeys,
            openingKey: openingKey,
            zoomLevel: zoomLevel,
            showCatFooter: showCatFooter,
            topInset: topInset,
            bottomInset: bottomInset,
            textScale: textScale,
            themeManager: themeManager,
            wallpaperManager: wallpaperManager,
            callbacks: callbacks
        )
    }
}
