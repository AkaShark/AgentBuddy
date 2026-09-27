import SwiftUI

struct AppearanceSettingsView: View {
    @Environment(ThemeManager.self) private var themeManager
    @State private var activeThemePicker: ThemePickerKind?
    @AppStorage("conversationTextSizeStep") private var textSizeStep = ConversationTextSize.large.rawValue

    var body: some View {
        Form {
            appearanceModeSection
            fontSizeSection
            conversationPreviewSection
            lightThemeSection
            darkThemeSection
        }
        .settingsMintList()
        .navigationTitle("Appearance")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $activeThemePicker) { pickerKind in
            ThemePickerSheet(
                title: pickerKind.title,
                themes: themes(for: pickerKind),
                selectedSlug: selectedSlug(for: pickerKind)
            ) { slug in
                selectTheme(slug, for: pickerKind)
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .buddySheetStyle()
        }
    }

    // MARK: - Appearance Mode

    private var appearanceModeSection: some View {
        Section {
            Picker(
                "Appearance",
                selection: Binding(
                    get: { themeManager.appearanceMode },
                    set: { themeManager.setAppearanceMode($0) }
                )
            ) {
                ForEach(AgentBuddyAppearanceMode.allCases) { mode in
                    Text(mode.displayName).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .tint(AgentBuddyTheme.action)
            .padding(.vertical, BuddySpacing.xxs)
            .settingsMintRow()
        } header: {
            Text("Mode")
                .settingsMintHeader()
        } footer: {
            Text("Match the device setting, or keep AgentBuddy fixed in light or dark mode.")
                .settingsMintFooter()
        }
    }

    // MARK: - Font Size

    private var fontSizeSection: some View {
        Section {
            VStack(spacing: BuddySpacing.sm) {
                HStack {
                    Text("Font Size")
                        .buddyText(.body)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                    Spacer()
                    Text(ConversationTextSize.clamped(rawValue: textSizeStep).label)
                        .buddyText(.label)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }

                HStack(spacing: BuddySpacing.xs) {
                    // Fixed sizes on purpose: the two glyphs show the scale ends.
                    Text(verbatim: "A")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .accessibilityHidden(true)

                    Slider(
                        value: Binding(
                            get: { Double(textSizeStep) },
                            set: { textSizeStep = Int($0.rounded()) }
                        ),
                        in: Double(ConversationTextSize.tiny.rawValue)...Double(ConversationTextSize.huge.rawValue),
                        step: 1
                    )
                    .tint(AgentBuddyTheme.action)
                    .accessibilityLabel(Text("Font Size"))
                    .accessibilityValue(Text(ConversationTextSize.clamped(rawValue: textSizeStep).label))

                    Text(verbatim: "A")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .accessibilityHidden(true)
                }
            }
            .padding(.vertical, BuddySpacing.xs)
            .settingsMintRow()
        } header: {
            Text("Font Size")
                .settingsMintHeader()
        } footer: {
            Text("Pinch in conversations to adjust, or use this slider. Applies across the app.")
                .settingsMintFooter()
        }
    }

    // MARK: - Conversation Preview

    private var conversationPreviewSection: some View {
        Section {
            VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                UserBubble(text: "Hey clanker, why is prod on fire", compact: true)

                ToolCallCardView(model: ToolCallCardModel(
                    kind: .commandExecution,
                    title: "Command",
                    summary: "rg 'TODO: fix later' --count",
                    status: .completed,
                    duration: "0.3s",
                    sections: []
                ))

                AssistantBubble(
                    text: """
                    Found the issue. Someone deployed this:

                    ```python
                    if is_friday():
                        yolo_deploy(skip_tests=True)
                    ```
                    I'm not mad, just disappointed.
                    """,
                    compact: true
                )

                UserBubble(text: "That was you, clanker", compact: true)
            }
            .padding(.vertical, BuddySpacing.sm)
            .environment(\.textScale, ConversationTextSize.clamped(rawValue: textSizeStep).scale)
            .id(themeManager.themeVersion)
            .listRowBackground(AgentBuddyTheme.background)
            .listRowInsets(EdgeInsets(top: 0, leading: BuddySpacing.sm, bottom: 0, trailing: BuddySpacing.sm))
        } header: {
            Text("Preview")
                .settingsMintHeader()
        }
    }

    // MARK: - Light Theme

    private var lightThemeSection: some View {
        Section {
            themePicker(
                themes: themeManager.lightThemes,
                selectedSlug: themeManager.selectedLightSlug,
                pickerKind: .light
            )
        } header: {
            Text("Light theme")
                .settingsMintHeader()
        }
    }

    // MARK: - Dark Theme

    private var darkThemeSection: some View {
        Section {
            themePicker(
                themes: themeManager.darkThemes,
                selectedSlug: themeManager.selectedDarkSlug,
                pickerKind: .dark
            )
        } header: {
            Text("Dark theme")
                .settingsMintHeader()
        }
    }

    // MARK: - Theme Picker

    private func themePicker(
        themes: [ThemeIndexEntry],
        selectedSlug: String,
        pickerKind: ThemePickerKind
    ) -> some View {
        let selected = themes.first(where: { $0.slug == selectedSlug }) ?? themes.first
        return Button {
            activeThemePicker = pickerKind
        } label: {
            ThemePickerRow(entry: selected, trailingAccessory: .chevron)
                .frame(minHeight: BuddySize.minHitTarget)
        }
        .buttonStyle(.plain)
        .settingsMintRow()
    }

    private func themes(for pickerKind: ThemePickerKind) -> [ThemeIndexEntry] {
        switch pickerKind {
        case .light:
            themeManager.lightThemes
        case .dark:
            themeManager.darkThemes
        }
    }

    private func selectedSlug(for pickerKind: ThemePickerKind) -> String {
        switch pickerKind {
        case .light:
            themeManager.selectedLightSlug
        case .dark:
            themeManager.selectedDarkSlug
        }
    }

    private func selectTheme(_ slug: String, for pickerKind: ThemePickerKind) {
        switch pickerKind {
        case .light:
            themeManager.selectLightTheme(slug)
        case .dark:
            themeManager.selectDarkTheme(slug)
        }
    }
}

private enum ThemePickerKind: String, Identifiable {
    case light
    case dark

    var id: String { rawValue }

    var title: String {
        switch self {
        case .light:
            "Light Theme"
        case .dark:
            "Dark Theme"
        }
    }
}

#if DEBUG
#Preview("Appearance") {
    AgentBuddyPreviewScene(includeBackground: false) {
        NavigationStack {
            AppearanceSettingsView()
        }
    }
}
#endif
