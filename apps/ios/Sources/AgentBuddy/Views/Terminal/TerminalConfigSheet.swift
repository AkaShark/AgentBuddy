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
                Section("Font") {
                    Stepper(value: $draftFontSize, in: 10...24, step: 1) {
                        HStack {
                            Text("Size")
                                .font(.custom("SFMono-Regular", size: 13))
                            Spacer()
                            Text("\(Int(draftFontSize)) pt")
                                .font(.custom("SFMono-Regular", size: 13))
                                .foregroundColor(.secondary)
                        }
                    }
                    Slider(
                        value: $draftFontSize,
                        in: 10...24,
                        step: 1
                    ) {
                        Text("Size")
                    }
                }
                .onChange(of: draftFontSize) { _, _ in
                    applyDraft()
                }
                Section("Theme") {
                    Picker("Theme", selection: $draftThemeId) {
                        ForEach(TerminalThemeChoice.allCases) { choice in
                            Text(choice.title).tag(choice.id)
                        }
                    }
                    .pickerStyle(.inline)
                    .onChange(of: draftThemeId) { _, _ in applyDraft() }
                }
                Section("Cursor") {
                    Toggle("Blink", isOn: $draftCursorBlink)
                        .onChange(of: draftCursorBlink) { _, _ in applyDraft() }
                }
            }
            .navigationTitle("Terminal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        applyDraft()
                        appliedForDismiss = true
                        dismiss()
                    }
                }
            }
            .onDisappear {
                if !appliedForDismiss {
                    applyDraft()
                }
            }
        }
    }

    private func applyDraft() {
        fontSize = draftFontSize
        themeId = draftThemeId
        cursorBlink = draftCursorBlink
        onApply(draftFontSize, draftThemeId, draftCursorBlink)
    }
}
