import SwiftUI

// MARK: - Language

struct LanguageSettingsView: View {
    @State private var languageManager = LanguageManager.shared

    var body: some View {
        ZStack {
            AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
            Form {
                Section {
                    ForEach(AppLanguage.allCases) { lang in
                        Button {
                            languageManager.set(lang)
                        } label: {
                            HStack(spacing: 10) {
                                Text(lang.displayName)
                                    .agentBuddyFont(.subheadline)
                                    .foregroundColor(AgentBuddyTheme.textPrimary)
                                Spacer()
                                if lang == languageManager.language {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(AgentBuddyTheme.accent)
                                }
                            }
                        }
                        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                    }
                } footer: {
                    Text("Switching language updates the app immediately.")
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                }
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Language")
    }
}
