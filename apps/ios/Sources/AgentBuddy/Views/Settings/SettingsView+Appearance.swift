import SwiftUI

extension SettingsView {
    // MARK: - Appearance Section

    var appearanceSection: some View {
        Section {
            NavigationLink {
                AppearanceSettingsView()
            } label: {
                SettingsMintRowLabel("Appearance", systemImage: "paintbrush")
            }
            .settingsMintRow()

            NavigationLink {
                LanguageSettingsView()
            } label: {
                SettingsMintRowLabel("Language", systemImage: "globe")
            }
            .settingsMintRow()
        } header: {
            Text("Theme")
                .settingsMintHeader()
        }
    }

    // MARK: - Font Section

    var fontSection: some View {
        Section {
            ForEach(FontFamilyOption.allCases) { option in
                let isSelected = fontFamily == option.rawValue
                Button {
                    fontFamily = option.rawValue
                    ThemeManager.shared.syncFontPreference()
                } label: {
                    HStack(spacing: BuddySpacing.sm) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(option.displayName)
                                .buddyText(.body, weight: isSelected ? .semibold : .regular)
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                            // Sample rendered in the option's own font on purpose.
                            Text("The quick brown fox")
                                .font(AgentBuddyFont.sampleFont(family: option, size: 14))
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                        }
                        Spacer(minLength: BuddySpacing.xs)
                        if isSelected {
                            SettingsMintCheckmark()
                        }
                    }
                    .padding(.vertical, BuddySpacing.xxs)
                    .contentShape(Rectangle())
                }
                .settingsMintSelected(isSelected)
                .settingsMintRow()
            }
        } header: {
            Text("Font")
                .settingsMintHeader()
        }
    }
}
