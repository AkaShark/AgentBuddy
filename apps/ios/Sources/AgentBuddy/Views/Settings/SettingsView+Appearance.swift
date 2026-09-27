import SwiftUI

extension SettingsView {
    // MARK: - Appearance Section

    var appearanceSection: some View {
        Section {
            NavigationLink {
                AppearanceSettingsView()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "paintbrush")
                        .foregroundColor(AgentBuddyTheme.accent)
                        .frame(width: 20)
                    Text("Appearance")
                        .agentBuddyFont(.subheadline)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                }
            }
            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

            NavigationLink {
                LanguageSettingsView()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "globe")
                        .foregroundColor(AgentBuddyTheme.accent)
                        .frame(width: 20)
                    Text("Language")
                        .agentBuddyFont(.subheadline)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                }
            }
            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
        } header: {
            Text("Theme")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
    }

    // MARK: - Font Section

    var fontSection: some View {
        Section {
            ForEach(FontFamilyOption.allCases) { option in
                Button {
                    fontFamily = option.rawValue
                    ThemeManager.shared.syncFontPreference()
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(option.displayName)
                                .agentBuddyFont(.subheadline)
                                .foregroundColor(AgentBuddyTheme.textPrimary)
                            Text("The quick brown fox")
                                .font(AgentBuddyFont.sampleFont(family: option, size: 14))
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                        }
                        Spacer()
                        if fontFamily == option.rawValue {
                            Image(systemName: "checkmark")
                                .agentBuddyFont(.subheadline, weight: .semibold)
                                .foregroundColor(AgentBuddyTheme.accentStrong)
                        }
                    }
                }
                .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            }
        } header: {
            Text("Font")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
    }
}
