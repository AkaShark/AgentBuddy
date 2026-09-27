import SwiftUI

struct RealtimeVoiceScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) var appModel
    @Environment(VoiceRuntimeController.self) var voiceRuntime
    let threadKey: ThreadKey
    let onEnd: () -> Void
    let onToggleSpeaker: () -> Void

    @State var apiKey = ""
    @State var isSavingApiKey = false
    @State var hasCheckedAuth = false
    @State var hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
    @State var apiKeyError: String?
    @State var isRetryingAfterAuthSave = false

    private var glowPalette: GlowPalette {
        .from(colorScheme: colorScheme)
    }

    var primaryTextColor: Color {
        AgentBuddyTheme.textPrimary
    }

    var secondaryTextColor: Color {
        AgentBuddyTheme.textSecondary
    }

    var promptFillColor: Color {
        AgentBuddyTheme.surface.opacity(colorScheme == .dark ? 0.82 : 0.92)
    }

    var promptStrokeColor: Color {
        AgentBuddyTheme.border.opacity(colorScheme == .dark ? 0.55 : 0.8)
    }

    var controlFillColor: Color {
        AgentBuddyTheme.surfaceLight.opacity(colorScheme == .dark ? 0.72 : 0.88)
    }
    private var session: VoiceSessionState? {
        guard let session = voiceRuntime.activeVoiceSession,
              session.threadKey == threadKey else { return nil }
        return session
    }

    var server: AppServerSnapshot? {
        appModel.snapshot?.serverSnapshot(for: threadKey.serverId)
    }

    private var phase: VoiceSessionPhase {
        session?.phase ?? .connecting
    }

    private var handoffThreadKey: ThreadKey? {
        session?.handoffRemoteThreadKey
    }

    private var inputLevel: CGFloat {
        CGFloat(session?.scaledInputLevel ?? 0)
    }

    private var outputLevel: CGFloat {
        CGFloat(session?.scaledOutputLevel ?? 0)
    }

    private var glowIntensity: CGFloat {
        switch phase {
        case .listening:
            return max(0.3, inputLevel)
        case .speaking:
            return max(0.3, outputLevel)
        case .thinking, .handoff:
            return 0.4
        case .connecting:
            return 0.25
        case .error:
            return 0.1
        }
    }

    private var transcriptHistory: [VoiceSessionTranscriptEntry] {
        session.map {
            $0.transcriptHistory.filter { !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        } ?? []
    }

    private var visibleTranscriptEntries: [VoiceSessionTranscriptEntry] {
        var entries = transcriptHistory
        guard let session else { return entries }

        let liveText = session.transcriptText?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !liveText.isEmpty else { return entries }

        let speaker = session.transcriptSpeaker?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
            ? session.transcriptSpeaker!
            : (session.phase == .speaking ? "Codex" : "You")
        let liveId = session.transcriptLiveMessageID ?? "live-\(speaker.lowercased())"
        let timestamp = entries.first(where: { $0.id == liveId })?.timestamp ?? Date()
        let entry = VoiceSessionTranscriptEntry(
            id: liveId,
            speaker: speaker,
            text: liveText,
            timestamp: timestamp
        )

        if let existingIndex = entries.firstIndex(where: { $0.id == liveId }) {
            entries[existingIndex] = entry
        } else {
            entries.append(entry)
        }
        return entries
    }

    private var transcriptScrollSignature: String? {
        guard let last = visibleTranscriptEntries.last else { return nil }
        return "\(last.id):\(last.text.count)"
    }

    private var shouldShowApiKeyPrompt: Bool {
        guard hasCheckedAuth,
              let server,
              server.isLocal,
              phase == .connecting,
              !hasStoredApiKey else {
            return false
        }
        return true
    }

    var trimmedApiKey: String {
        apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        ZStack {
            Color(hex: glowPalette.background)
                .ignoresSafeArea()

            SiriEdgeGlow(
                intensity: glowIntensity,
                phase: phase,
                palette: glowPalette
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                transcriptContent
                    .padding(.horizontal, 32)

                if let handoffThreadKey {
                    InlineHandoffView(
                        threadKey: handoffThreadKey,
                        maxHeight: 220
                    )
                    .padding(.horizontal, 18)
                    .padding(.top, 18)
                    .transition(.opacity)
                }

                Spacer()

                bottomControls
                    .padding(.bottom, 40)
            }

            if shouldShowApiKeyPrompt {
                realtimeApiKeyPrompt
                    .padding(.horizontal, 20)
            }
        }
        .statusBarHidden()
        .task {
            do {
                _ = try await appModel.client.refreshAccount(
                    serverId: threadKey.serverId,
                    params: AppRefreshAccountRequest(refreshToken: false)
                )
                await appModel.refreshSnapshot()
                await MainActor.run {
                    hasCheckedAuth = true
                    hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
                    apiKeyError = nil
                }
            } catch {
                await MainActor.run {
                    hasCheckedAuth = true
                    hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
                    apiKeyError = error.localizedDescription
                }
            }
        }
        .onChange(of: session?.id) { _, next in
            if next == nil, !isRetryingAfterAuthSave {
                onEnd()
            } else if next != nil {
                isRetryingAfterAuthSave = false
            }
        }
    }

    @ViewBuilder
    private var transcriptContent: some View {
        VStack(spacing: 16) {
            HStack(spacing: 8) {
                VoiceScreenPulsingDot(
                    color: phaseColor,
                    isActive: phase == .listening || phase == .speaking
                )

                Text(phase.displayTitle.uppercased())
                    .font(AgentBuddyFont.monospaced(.caption, weight: .bold))
                    .foregroundColor(phaseColor)
                    .tracking(2)
            }

            if phase == .error,
               let error = session?.lastError?.trimmingCharacters(in: .whitespacesAndNewlines),
               !error.isEmpty {
                Text(error)
                    .font(AgentBuddyFont.styled(.caption))
                    .foregroundColor(secondaryTextColor)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .padding(.horizontal, 16)
            }

            if visibleTranscriptEntries.isEmpty {
                AudioWaveformView(
                    level: Float(phase == .listening ? inputLevel : outputLevel),
                    tint: phaseColor
                )
                .frame(width: 180, height: 40)
                .opacity(phase == .connecting ? 0.3 : 0.8)
            } else {
                GeometryReader { geometry in
                    ScrollViewReader { proxy in
                        ScrollView(.vertical, showsIndicators: false) {
                            VStack(spacing: 18) {
                                ForEach(Array(visibleTranscriptEntries.enumerated()), id: \.element.id) { index, entry in
                                    transcriptLine(
                                        entry,
                                        isLive: false,
                                        recencyIndex: visibleTranscriptEntries.count - index - 1
                                    )
                                    .id(entry.id)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: geometry.size.height, alignment: .center)
                        }
                        .onChange(of: transcriptScrollSignature) { _, _ in
                            guard let next = visibleTranscriptEntries.last?.id else { return }
                            withAnimation(.easeOut(duration: 0.18)) {
                                proxy.scrollTo(next, anchor: .bottom)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    private func transcriptLine(
        _ entry: VoiceSessionTranscriptEntry,
        isLive: Bool,
        recencyIndex: Int
    ) -> some View {
        let isUser = entry.speaker == "You"
        let isSystem = entry.speaker == "System"
        let opacity: Double = switch recencyIndex {
        case 0:
            isLive ? 1.0 : 0.96
        case 1:
            0.72
        case 2:
            0.5
        default:
            0.34
        }
        let textStyle: Font.TextStyle = isUser || isSystem ? .body : .title2
        let fontWeight: Font.Weight = isSystem ? .regular : (isUser ? .regular : .medium)

        return Text(entry.text)
            .font(AgentBuddyFont.styled(textStyle, weight: fontWeight))
            .foregroundColor(primaryTextColor.opacity(opacity))
            .multilineTextAlignment(.center)
            .lineSpacing(isUser ? 4 : 6)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity)
    }

    private var bottomControls: some View {
        HStack(spacing: 40) {
            if let session {
                Button(action: onToggleSpeaker) {
                    VStack(spacing: 6) {
                        Image(systemName: session.route.iconName)
                            .font(.system(size: 20, weight: .medium))
                            .frame(width: 52, height: 52)
                            .background(controlFillColor)
                            .clipShape(Circle())

                        Text(session.route.label)
                            .font(AgentBuddyFont.monospaced(.caption2, weight: .medium))
                    }
                    .foregroundColor(
                        session.route.supportsSpeakerToggle ? primaryTextColor : secondaryTextColor.opacity(0.6)
                    )
                }
                .buttonStyle(.plain)
                .disabled(!session.route.supportsSpeakerToggle)
            }

            Button(action: onEnd) {
                Image(systemName: "xmark")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(AgentBuddyTheme.textOnAccent)
                    .frame(width: 64, height: 64)
                    .background(AgentBuddyTheme.danger)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
    }

    private var phaseColor: Color {
        switch phase {
        case .connecting:
            return Color(hex: glowPalette.accent)
        case .listening:
            return Color(hex: glowPalette.accentStrong)
        case .speaking, .thinking, .handoff:
            return Color(hex: glowPalette.warning)
        case .error:
            return AgentBuddyTheme.danger
        }
    }
}

private struct VoiceScreenPulsingDot: View {
    let color: Color
    let isActive: Bool

    @State private var scale: CGFloat = 1.0

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 8, height: 8)
            .scaleEffect(scale)
            .onAppear {
                guard isActive else { return }
                withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                    scale = 1.4
                }
            }
            .onChange(of: isActive) { _, active in
                if active {
                    withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                        scale = 1.4
                    }
                } else {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        scale = 0.7
                    }
                }
            }
    }
}
