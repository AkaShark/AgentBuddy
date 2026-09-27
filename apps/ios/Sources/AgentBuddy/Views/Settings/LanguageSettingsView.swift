import SwiftUI

// MARK: - Language

struct LanguageSettingsView: View {
    @State private var languageManager = LanguageManager.shared

    var body: some View {
        Form {
            Section {
                ForEach(AppLanguage.allCases) { lang in
                    let isSelected = lang == languageManager.language
                    Button {
                        languageManager.set(lang)
                    } label: {
                        HStack(spacing: BuddySpacing.sm) {
                            Text(lang.displayName)
                                .buddyText(.body, weight: isSelected ? .semibold : .regular)
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                            Spacer()
                            if isSelected {
                                SettingsMintCheckmark()
                            }
                        }
                        .frame(minHeight: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                    }
                    .settingsMintSelected(isSelected)
                    .settingsMintRow()
                }
            } footer: {
                Text("Switching language updates the app immediately.")
                    .settingsMintFooter()
            }
        }
        .settingsMintList()
        .navigationTitle("Language")
    }
}
