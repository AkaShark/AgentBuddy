import SwiftUI

/// Floating realtime-voice button (iPad/Mac split view). A 44pt surface circle
/// whose icon and outline tint reflect the voice session state.
struct HomeVoiceOrbButton: View {
    let session: VoiceSessionState?
    let isAvailable: Bool
    let isStarting: Bool
    let action: () -> Void

    private let buttonSize: CGFloat = 44

    private var phase: VoiceSessionPhase? {
        if isStarting { return .connecting }
        return session?.phase
    }

    private var isActive: Bool {
        phase != nil && phase != .error
    }

    private var hasRecoverableError: Bool {
        phase == .error
    }

    private var isDisabled: Bool {
        !isAvailable || isStarting || (session != nil && !hasRecoverableError)
    }

    private var accessibilityLabel: String {
        if !isAvailable { return "Realtime voice unavailable" }
        if isStarting { return "Connecting realtime voice" }
        if hasRecoverableError { return "Retry realtime voice" }
        return "Start realtime voice"
    }

    /// Tint for the mic glyph. When the session is live we use a warmer
    /// accent; error falls back to the danger color. Idle matches the
    /// muted glyph color used by the search button.
    private var iconColor: Color {
        guard let phase else { return AgentBuddyTheme.textSecondary }
        switch phase {
        case .connecting, .listening:
            return AgentBuddyTheme.link
        case .speaking, .thinking, .handoff:
            return AgentBuddyTheme.warning
        case .error:
            return AgentBuddyTheme.danger
        }
    }

    private var strokeColor: Color {
        isActive ? iconColor : AgentBuddyTheme.border
    }

    private var strokeWidth: CGFloat {
        isActive ? 1.5 : 1
    }

    var body: some View {
        Button(action: action) {
            Group {
                if isStarting {
                    ProgressView()
                        .controlSize(.small)
                        .tint(iconColor)
                } else {
                    Image(systemName: "waveform.and.mic")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(iconColor)
                }
            }
            .frame(width: buttonSize, height: buttonSize)
            .background(AgentBuddyTheme.surface, in: Capsule(style: .continuous))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 4)
        .overlay(
            Capsule(style: .continuous)
                .stroke(strokeColor, lineWidth: strokeWidth)
                .allowsHitTesting(false)
        )
        .disabled(isDisabled)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint("Starts a local realtime voice conversation.")
    }
}
