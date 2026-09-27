import SwiftUI

// MARK: - Tab bar

/// Bottom navigation for the three primary destinations. The current tab is
/// marked by a filled icon, heavier label and the accessibility selected
/// trait, never by colour alone.
struct HomeShellTabBar: View {
    @Binding var selection: HomeShellTab

    var body: some View {
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
struct HomeComposerPill: View {
    let onCompose: () -> Void
    var onVoice: (() -> Void)?
    var isStartingVoice = false

    var body: some View {
        HStack(spacing: BuddySpacing.xs) {
            Button(action: onCompose) {
                Text("Got an idea? Hand it to AgentBuddy…")
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, minHeight: BuddySize.control, alignment: .leading)
                    .padding(.leading, BuddySpacing.lg)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Start a new task"))

            trailingButton
                .padding(.trailing, BuddySpacing.xxs)
        }
        .padding(.vertical, BuddySpacing.xxs)
        .background(AgentBuddyTheme.surface, in: RoundedRectangle(cornerRadius: BuddyRadius.composer, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: BuddyRadius.composer, style: .continuous)
                .strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
        }
        .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
    }

    @ViewBuilder
    private var trailingButton: some View {
        if let onVoice {
            Button(action: onVoice) {
                ZStack {
                    Circle().fill(AgentBuddyTheme.action)
                    if isStartingVoice {
                        ProgressView().tint(AgentBuddyTheme.onAction)
                    } else {
                        Image(systemName: "waveform")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(AgentBuddyTheme.onAction)
                    }
                }
                .frame(width: 44, height: 44)
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
                diameter: 44,
                action: onCompose
            )
        }
    }
}
