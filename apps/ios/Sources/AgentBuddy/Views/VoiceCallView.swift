import SwiftUI
import UIKit

struct VoiceCallView: View {
    @Environment(AppModel.self) private var appModel
    @Environment(VoiceRuntimeController.self) private var voiceRuntime
    @AppStorage("conversationTextSizeStep") private var conversationTextSizeStep = VoiceConversationTextSize.medium.rawValue
    @State private var screenModel = ConversationScreenModel()
#if DEBUG
    @State private var showDebugSheet = false
#endif

    private var textScale: CGFloat {
        VoiceConversationTextSize.clamped(rawValue: conversationTextSizeStep).scale
    }

    private var voiceContext: VoiceCallContext? {
        guard let session = voiceRuntime.activeVoiceSession,
              let thread = appModel.snapshot?.threadSnapshot(for: session.threadKey) else {
            return nil
        }
        return VoiceCallContext(session: session, thread: thread)
    }

    var body: some View {
        ZStack {
            AgentBuddyTheme.backgroundGradient
                .ignoresSafeArea()

            if let context = voiceContext {
                VoiceCreditsTranscriptView(
                    items: screenModel.transcript.items,
                    threadStatus: screenModel.transcript.threadStatus,
                    session: context.session,
                    textScale: textScale
                )
                .safeAreaInset(edge: .top, spacing: 0) {
                    topBar(context.session)
                }
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    bottomBar(context.session)
                }
            } else {
                ProgressView()
                    .tint(AgentBuddyTheme.accent)
            }
        }
        .interactiveDismissDisabled(true)
        .task(id: voiceContext?.session.id) {
            bindModel()
        }
#if DEBUG
        .sheet(isPresented: $showDebugSheet) {
            if let session = voiceRuntime.activeVoiceSession {
                VoiceCallDebugSheet(session: session)
            }
        }
#endif
    }

    private func bindModel() {
        guard let context = voiceContext else { return }
        screenModel.bind(
            thread: context.thread,
            appModel: appModel,
            agentDirectoryVersion: appModel.snapshot?.agentDirectoryVersion ?? 0
        )
    }

    private func topBar(_ session: VoiceSessionState) -> some View {
        VStack(spacing: 12) {
            HStack(spacing: 10) {
                CompactSpeakerIndicator(
                    title: "You",
                    level: visualWaveformLevel(
                        session.inputLevel,
                        active: session.phase == .listening || session.inputLevel > 0.01
                    ),
                    active: session.phase == .listening || session.inputLevel > 0.01,
                    tint: AgentBuddyTheme.accent
                )

                CompactSpeakerIndicator(
                    title: "Codex",
                    level: visualWaveformLevel(
                        session.outputLevel,
                        active: session.phase == .speaking || session.isSpeaking
                    ),
                    active: session.phase == .speaking || session.isSpeaking,
                    tint: AgentBuddyTheme.warning
                )

                Spacer(minLength: 0)

                routeButton(session)

#if DEBUG
                Button {
                    showDebugSheet = true
                } label: {
                    Image(systemName: "ladybug.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                        .frame(width: 34, height: 34)
                        .background(Circle().fill(AgentBuddyTheme.surface.opacity(0.92)))
                }
                .buttonStyle(.plain)
#endif
            }

            HStack(spacing: 8) {
                Text(session.threadTitle)
                    .font(AgentBuddyFont.styled(.caption, weight: .semibold))
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                    .lineLimit(1)

                Text("•")
                    .font(AgentBuddyFont.styled(.caption))
                    .foregroundColor(AgentBuddyTheme.textMuted)

                Text(session.phase.displayTitle)
                    .font(AgentBuddyFont.monospaced(.caption, weight: .semibold))
                    .foregroundColor(phaseColor(session.phase))

                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 14)
        .background(
            LinearGradient(
                colors: [Color.black.opacity(0.9), Color.black.opacity(0.52), .clear],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    private func bottomBar(_ session: VoiceSessionState) -> some View {
        HStack {
            Spacer()

            Button(role: .destructive) {
                Task { await voiceRuntime.stopActiveVoiceSession() }
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "phone.down.fill")
                        .font(.system(size: 16, weight: .semibold))
                    Text(session.phase == .error ? "Close" : "Hang Up")
                        .font(AgentBuddyFont.styled(.callout, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 22)
                .padding(.vertical, 14)
                .background(Capsule().fill(AgentBuddyTheme.danger))
            }
            .buttonStyle(.plain)

            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 18)
        .background(
            LinearGradient(
                colors: [.clear, Color.black.opacity(0.58), Color.black.opacity(0.92)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    private func routeButton(_ session: VoiceSessionState) -> some View {
        Button {
            Task { try? await voiceRuntime.toggleActiveVoiceSessionSpeaker() }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: routeIcon(session.route))
                    .font(.system(size: 12, weight: .semibold))
                Text(session.route.label)
                    .font(AgentBuddyFont.styled(.caption, weight: .semibold))
            }
            .foregroundColor(session.route.supportsSpeakerToggle ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textMuted)
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .background(Capsule().fill(AgentBuddyTheme.surface.opacity(0.92)))
        }
        .buttonStyle(.plain)
        .disabled(!session.route.supportsSpeakerToggle)
    }

    private func visualWaveformLevel(_ rawLevel: Float, active: Bool) -> Float {
        let scaled = min(1, rawLevel * 3.1)
        return active ? max(0.08, scaled) : max(0, scaled)
    }

    private func phaseColor(_ phase: VoiceSessionPhase) -> Color {
        switch phase {
        case .connecting, .thinking, .handoff:
            return AgentBuddyTheme.warning
        case .listening, .speaking:
            return AgentBuddyTheme.accent
        case .error:
            return AgentBuddyTheme.danger
        }
    }

    private func routeIcon(_ route: VoiceSessionAudioRoute) -> String {
        switch route {
        case .speaker:
            return "speaker.wave.3.fill"
        case .receiver:
            return "phone.fill"
        case .headphones:
            return "headphones"
        case .bluetooth:
            return "dot.radiowaves.left.and.right"
        case .airPlay:
            return "airplayaudio"
        case .carPlay:
            return "car.fill"
        case .unknown:
            return "speaker.wave.2.fill"
        }
    }
}

private struct VoiceCallContext {
    let session: VoiceSessionState
    let thread: AppThreadSnapshot
}

private enum VoiceConversationTextSize: Int {
    case xSmall = 0
    case small = 1
    case medium = 2
    case large = 3
    case xLarge = 4

    var scale: CGFloat {
        switch self {
        case .xSmall: 0.86
        case .small: 0.93
        case .medium: 1.0
        case .large: 1.1
        case .xLarge: 1.22
        }
    }

    static func clamped(rawValue: Int) -> VoiceConversationTextSize {
        let bounded = min(max(rawValue, xSmall.rawValue), xLarge.rawValue)
        return VoiceConversationTextSize(rawValue: bounded) ?? .medium
    }
}

private struct CompactSpeakerIndicator: View {
    let title: String
    let level: Float
    let active: Bool
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Circle()
                    .fill(active ? tint : AgentBuddyTheme.textMuted.opacity(0.45))
                    .frame(width: 7, height: 7)
                Text(title)
                    .font(AgentBuddyFont.styled(.caption, weight: .semibold))
                    .foregroundColor(active ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textSecondary)
            }

            AudioWaveformView(level: level, tint: tint)
                .frame(width: 86, height: 18)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Capsule().fill(AgentBuddyTheme.surface.opacity(0.92)))
    }
}

#if DEBUG
private struct VoiceCallDebugSheet: View {
    let session: VoiceSessionState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(session.debugEntries) { entry in
                Text("\(entry.timestamp.formatted(date: .omitted, time: .standard)) \(entry.line)")
                    .font(AgentBuddyFont.monospaced(.caption2))
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                    .textSelection(.enabled)
                    .listRowBackground(Color.black)
            }
            .scrollContentBackground(.hidden)
            .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Voice Debug")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}
#endif
