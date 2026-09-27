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
        VStack(alignment: .leading, spacing: 12) {
            Text("Typing Effect")
                .agentBuddyFont(size: 16, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .padding(.horizontal, 16)

            // Effect picker
            HStack {
                Text("Effect")
                    .agentBuddyFont(size: 13, weight: .medium)
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
                .tint(AgentBuddyTheme.accent)
            }
            .padding(.horizontal, 16)

            // Speed slider
            VStack(alignment: .leading, spacing: 6) {
                Text("Reveal Speed")
                    .agentBuddyFont(size: 13, weight: .medium)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)

                HStack(spacing: 10) {
                    Image(systemName: "hare")
                        .font(.system(size: 11))
                        .foregroundStyle(AgentBuddyTheme.textMuted)

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
                    .tint(AgentBuddyTheme.accent)

                    Image(systemName: "tortoise")
                        .font(.system(size: 11))
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
            }
            .padding(.horizontal, 16)

            // Granularity
            HStack(spacing: 0) {
                ForEach(GranularityKind.allCases) { kind in
                    Button {
                        typingEffectConfig.granularity = kind.rawValue
                        persistTypingEffect()
                    } label: {
                        Text(kind.shortLabel)
                            .agentBuddyFont(size: 12, weight: selectedGranularity == kind ? .semibold : .regular)
                            .foregroundStyle(selectedGranularity == kind ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                            .background(selectedGranularity == kind ? AgentBuddyTheme.accent : .clear)
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(AgentBuddyTheme.surfaceLight.opacity(0.8))
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(AgentBuddyTheme.border.opacity(0.6), lineWidth: 1))
            .padding(.horizontal, 16)

            // Reveal mode
            HStack(spacing: 0) {
                ForEach(["Linear", "Continuous"], id: \.self) { mode in
                    Button {
                        typingEffectConfig.revealMode = mode
                        persistTypingEffect()
                    } label: {
                        Text(mode)
                            .agentBuddyFont(size: 12, weight: typingEffectConfig.revealMode == mode ? .semibold : .regular)
                            .foregroundStyle(typingEffectConfig.revealMode == mode ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                            .background(typingEffectConfig.revealMode == mode ? AgentBuddyTheme.accent : .clear)
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(AgentBuddyTheme.surfaceLight.opacity(0.8))
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(AgentBuddyTheme.border.opacity(0.6), lineWidth: 1))
            .padding(.horizontal, 16)
        }
    }
}
