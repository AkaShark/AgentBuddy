import SwiftUI

// MARK: - Tab bar

/// Bottom navigation for the three primary destinations. The current tab is
/// marked by a filled icon, heavier label and the accessibility selected
/// trait, never by colour alone.
struct HomeShellTabBar: View {
    @Binding var selection: HomeShellTab

    var body: some View {
        tabs.buddyChromeTypeLimit()
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            ForEach(HomeShellTab.allCases) { tab in
                let isSelected = tab == selection
                Button {
                    selection = tab
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: isSelected ? tab.selectedSystemImage : tab.systemImage)
                            .font(.system(size: 20, weight: isSelected ? .semibold : .regular))
                            .frame(height: 24)
                        Text(tab.title)
                            .buddyText(.caption, weight: isSelected ? .semibold : .regular)
                    }
                    .foregroundStyle(isSelected ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: BuddySize.control)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.top, BuddySpacing.xxs)
    }
}

// MARK: - Composer pill

/// "有个想法？交给搭子…" entry. Tapping the text opens the new-task composer;
/// the trailing button starts a realtime voice session when voice is enabled,
/// otherwise it also opens the composer.
///
/// `.card` draws its own surface (iOS 18–25, iPad sidebar); inside the iOS 26
/// tab bar accessory the system supplies the Liquid Glass capsule, so the
/// accessory styles draw no background. `.accessoryInline` is the compact form
/// shown next to the minimized tab bar.
struct HomeComposerPill: View {
    enum Style: Equatable {
        case card
        case accessory
        case accessoryInline
    }

    let onCompose: () -> Void
    var onVoice: (() -> Void)?
    var isStartingVoice = false
    var style: Style = .card

    private var buttonDiameter: CGFloat { style == .accessoryInline ? 32 : 40 }

    /// On the card the theme colour is exact; on Liquid Glass the hierarchical
    /// style lets the system keep the text legible over whatever scrolls under.
    private var placeholderStyle: AnyShapeStyle {
        style == .card ? AnyShapeStyle(AgentBuddyTheme.textSecondary) : AnyShapeStyle(.secondary)
    }

    var body: some View {
        styledRow.buddyChromeTypeLimit()
    }

    @ViewBuilder
    private var styledRow: some View {
        if style == .card {
            row
                .padding(.vertical, BuddySpacing.xxs)
                .background(AgentBuddyTheme.surface, in: RoundedRectangle(cornerRadius: BuddyRadius.composer, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: BuddyRadius.composer, style: .continuous)
                        .strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
                }
                .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
        } else {
            row
        }
    }

    private var row: some View {
        HStack(spacing: BuddySpacing.xs) {
            Button(action: onCompose) {
                Text("Got an idea? Hand it to AgentBuddy…")
                    .buddyText(style == .accessoryInline ? .label : .body)
                    .foregroundStyle(placeholderStyle)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, minHeight: BuddySize.minHitTarget, alignment: .leading)
                    .padding(.leading, style == .card ? BuddySpacing.lg : BuddySpacing.md)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Start a new task"))

            trailingButton
                .padding(.trailing, style == .card ? BuddySpacing.xxs : BuddySpacing.xs)
        }
    }

    @ViewBuilder
    private var trailingButton: some View {
        if let onVoice {
            Button(action: onVoice) {
                ZStack {
                    Circle().fill(AgentBuddyTheme.action)
                        .frame(width: buttonDiameter, height: buttonDiameter)
                    if isStartingVoice {
                        ProgressView().tint(AgentBuddyTheme.onAction)
                    } else {
                        Image(systemName: "waveform")
                            .font(.system(size: style == .accessoryInline ? 15 : 18, weight: .semibold))
                            .foregroundStyle(AgentBuddyTheme.onAction)
                    }
                }
                .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .disabled(isStartingVoice)
            .accessibilityLabel(Text("Start a voice session"))
        } else {
            BuddyIconButton(
                systemImage: "arrow.up",
                accessibilityLabel: "Start a new task",
                tone: .action,
                diameter: buttonDiameter,
                action: onCompose
            )
        }
    }
}
