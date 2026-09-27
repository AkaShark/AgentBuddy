import SwiftUI

extension WallpaperSelectionView {
    var typingEffectTabContent: some View {
        ScrollView(.vertical, showsIndicators: false) {
            typingEffectSection
                .padding(.top, 4)
                .padding(.bottom, 16)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Typing Effect

    private var typingEffectScope: WallpaperScope? {
        if let threadKey { return .thread(threadKey) }
        if let resolvedServerId { return .server(resolvedServerId) }
        return nil
    }

    private var selectedGranularity: GranularityKind {
        GranularityKind(rawValue: typingEffectConfig.granularity) ?? .block
    }

    private var selectedEffect: StreamingEffectKind? {
        typingEffectConfig.effects.first.flatMap { StreamingEffectKind(rawValue: $0) }
    }

    private func selectEffect(_ kind: StreamingEffectKind?) {
        typingEffectConfig.effects = kind.map { [$0.rawValue] } ?? []
        persistTypingEffect()
    }

    private func persistTypingEffect() {
        if let scope = typingEffectScope {
            wallpaperManager.setTypingEffect(typingEffectConfig, scope: scope)
        }
    }

    private var typingEffectSection: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            Text("Typing Effect")
                .buddyText(.heading)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)
                .padding(.horizontal, BuddySpacing.md)

            // Effect picker
            HStack {
                Text("Effect")
                    .buddyText(.label)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                Spacer()
                Picker("Effect", selection: Binding(
                    get: { selectedEffect?.rawValue ?? "" },
                    set: { newValue in
                        if newValue.isEmpty {
                            selectEffect(nil)
                        } else if let kind = StreamingEffectKind(rawValue: newValue) {
                            selectEffect(kind)
                        }
                    }
                )) {
                    Text("None").tag("")
                    ForEach(StreamingEffectKind.allCases) { kind in
                        Text(kind.rawValue).tag(kind.rawValue)
                    }
                }
                .pickerStyle(.menu)
                .tint(AgentBuddyTheme.link)
                .frame(minHeight: BuddySize.minHitTarget)
            }
            .padding(.horizontal, BuddySpacing.md)

            // Speed slider
            VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                Text("Reveal Speed")
                    .buddyText(.label)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)

                HStack(spacing: BuddySpacing.sm) {
                    Image(systemName: "hare")
                        .font(.system(size: 15))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .accessibilityHidden(true)

                    Slider(
                        value: Binding(
                            get: { typingEffectConfig.revealDuration },
                            set: {
                                typingEffectConfig.revealDuration = $0
                                persistTypingEffect()
                            }
                        ),
                        in: 0.03...1.2,
                        step: 0.01
                    )
                    .tint(AgentBuddyTheme.action)
                    .accessibilityLabel(Text("Reveal Speed"))

                    Image(systemName: "tortoise")
                        .font(.system(size: 15))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .accessibilityHidden(true)
                }
            }
            .padding(.horizontal, BuddySpacing.md)

            // Granularity
            segmentedControl(
                GranularityKind.allCases,
                isSelected: { selectedGranularity == $0 },
                title: { Text($0.shortLabel) }
            ) { kind in
                typingEffectConfig.granularity = kind.rawValue
                persistTypingEffect()
            }

            // Reveal mode
            segmentedControl(
                ["Linear", "Continuous"],
                isSelected: { typingEffectConfig.revealMode == $0 },
                title: { Text($0) }
            ) { mode in
                typingEffectConfig.revealMode = mode
                persistTypingEffect()
            }
        }
    }

    /// Mint segmented control: surfaceSoft track, the selected segment on the
    /// action fill with semibold text; every segment is at least 44pt tall.
    private func segmentedControl<Option: Hashable>(
        _ options: [Option],
        isSelected: @escaping (Option) -> Bool,
        title: @escaping (Option) -> Text,
        onSelect: @escaping (Option) -> Void
    ) -> some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.self) { option in
                let selected = isSelected(option)
                Button {
                    onSelect(option)
                } label: {
                    title(option)
                        .buddyText(.label, weight: selected ? .semibold : .regular)
                        .foregroundStyle(selected ? AgentBuddyTheme.onAction : AgentBuddyTheme.textSecondary)
                        .lineLimit(1)
                        .padding(.horizontal, BuddySpacing.xxs)
                        .frame(maxWidth: .infinity, minHeight: BuddySize.minHitTarget)
                        .background {
                            if selected {
                                RoundedRectangle(cornerRadius: BuddyRadius.control - 3, style: .continuous)
                                    .fill(AgentBuddyTheme.action)
                                    .padding(3)
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .background(
            AgentBuddyTheme.surfaceSoft,
            in: RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
        )
        .padding(.horizontal, BuddySpacing.md)
    }
}
