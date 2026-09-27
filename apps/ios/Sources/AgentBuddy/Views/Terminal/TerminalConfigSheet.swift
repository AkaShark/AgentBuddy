import SwiftUI

struct TerminalConfigSheet: View {
    @Binding var fontSize: Double
    @Binding var themeId: String
    @Binding var cursorBlink: Bool
    let onApply: (Double, String, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var draftFontSize: Double
    @State private var draftThemeId: String
    @State private var draftCursorBlink: Bool
    @State private var appliedForDismiss = false

    init(
        fontSize: Binding<Double>,
        themeId: Binding<String>,
        cursorBlink: Binding<Bool>,
        onApply: @escaping (Double, String, Bool) -> Void
    ) {
        self._fontSize = fontSize
        self._themeId = themeId
        self._cursorBlink = cursorBlink
        self.onApply = onApply
        self._draftFontSize = State(initialValue: fontSize.wrappedValue)
        self._draftThemeId = State(initialValue: themeId.wrappedValue)
        self._draftCursorBlink = State(initialValue: cursorBlink.wrappedValue)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Stepper(value: $draftFontSize, in: 10...24, step: 1) {
                        HStack {
                            Text("Size")
                                .buddyText(.body)
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                            Spacer()
                            Text("\(Int(draftFontSize)) pt")
                                .buddyText(.body)
                                .monospacedDigit()
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                        }
                    }
                    Slider(
                        value: $draftFontSize,
                        in: 10...24,
                        step: 1
                    ) {
                        Text("Size")
                    }
                    .tint(AgentBuddyTheme.action)
                } header: {
                    sectionHeader("Font")
                }
                .listRowBackground(AgentBuddyTheme.surface)
                .onChange(of: draftFontSize) { _, _ in
                    applyDraft()
                }
                Section {
                    Picker("Theme", selection: $draftThemeId) {
                        ForEach(TerminalThemeChoice.allCases) { choice in
                            Text(choice.title)
                                .buddyText(.body)
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                                .tag(choice.id)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    .tint(AgentBuddyTheme.action)
                    .onChange(of: draftThemeId) { _, _ in applyDraft() }
                } header: {
                    sectionHeader("Theme")
                }
                .listRowBackground(AgentBuddyTheme.surface)
                Section {
                    Toggle(isOn: $draftCursorBlink) {
                        Text("Blink")
                            .buddyText(.body)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                    }
                    .tint(AgentBuddyTheme.action)
                    .onChange(of: draftCursorBlink) { _, _ in applyDraft() }
                } header: {
                    sectionHeader("Cursor")
                }
                .listRowBackground(AgentBuddyTheme.surface)
            }
            .scrollContentBackground(.hidden)
            .buddyPageBackground()
            .navigationTitle("Terminal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        applyDraft()
                        appliedForDismiss = true
                        dismiss()
                    }
                    .foregroundStyle(AgentBuddyTheme.link)
                }
            }
            .onDisappear {
                if !appliedForDismiss {
                    applyDraft()
                }
            }
        }
    }

    private func sectionHeader(_ title: LocalizedStringKey) -> some View {
        Text(title)
            .buddyText(.caption, weight: .medium)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
    }

    private func applyDraft() {
        fontSize = draftFontSize
        themeId = draftThemeId
        cursorBlink = draftCursorBlink
        onApply(draftFontSize, draftThemeId, draftCursorBlink)
    }
}
