import SwiftUI

/// What the composer pill needs, shared by the tab bar accessory (iOS 26) and
/// the pill above the tab bar (iOS 18–25).
struct HomeComposerPillActions {
    let onCompose: () -> Void
    let onVoice: (() -> Void)?
    let isStartingVoice: Bool

    func pill(_ style: HomeComposerPill.Style) -> HomeComposerPill {
        HomeComposerPill(
            onCompose: onCompose,
            onVoice: onVoice,
            isStartingVoice: isStartingVoice,
            style: style
        )
    }
}

/// Phone home on the system `TabView`.
///
/// iOS 26: the floating Liquid Glass tab bar, which minimizes when a list is
/// scrolled down; the composer pill is the tab bar's bottom accessory (hidden
/// on 主机 from iOS 26.1, where the accessory can be switched off per tab).
/// iOS 18–25: the standard tab bar with the pill as a card above it.
struct HomeShellSystemTabs<Page: View>: View {
    @Binding var selection: HomeShellTab
    let composer: HomeComposerPillActions
    @ViewBuilder let page: (HomeShellTab) -> Page

    var body: some View {
        TabView(selection: $selection) {
            ForEach(HomeShellTab.allCases) { tab in
                Tab(tab.title, systemImage: tab.systemImage, value: tab) {
                    page(tab)
                        .modifier(LegacyComposerInset(isVisible: tab.showsComposerPill, composer: composer))
                }
            }
        }
        // The selected tab gets the link green on top of the system's
        // selection bubble, so selection is not shown by shape alone.
        .tint(AgentBuddyTheme.link)
        .modifier(ComposerAccessory(isVisible: selection.showsComposerPill, composer: composer))
    }
}

// MARK: - iOS 26 accessory

private struct ComposerAccessory: ViewModifier {
    let isVisible: Bool
    let composer: HomeComposerPillActions

    func body(content: Content) -> some View {
        if #available(iOS 26.1, *) {
            content
                .tabViewBottomAccessory(isEnabled: isVisible) {
                    HomeComposerAccessoryContent(composer: composer)
                }
                .tabBarMinimizeBehavior(.onScrollDown)
        } else if #available(iOS 26.0, *) {
            content
                .tabViewBottomAccessory {
                    HomeComposerAccessoryContent(composer: composer)
                }
                .tabBarMinimizeBehavior(.onScrollDown)
        } else {
            content
        }
    }
}

@available(iOS 26.0, *)
private struct HomeComposerAccessoryContent: View {
    let composer: HomeComposerPillActions
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement

    var body: some View {
        composer.pill(placement == .inline ? .accessoryInline : .accessory)
    }
}

// MARK: - iOS 18–25 fallback

private struct LegacyComposerInset: ViewModifier {
    let isVisible: Bool
    let composer: HomeComposerPillActions

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
        } else {
            content.safeAreaInset(edge: .bottom, spacing: 0) {
                if isVisible {
                    composer.pill(.card)
                        .padding(.horizontal, BuddySpacing.md)
                        .padding(.bottom, BuddySpacing.xs)
                }
            }
        }
    }
}
