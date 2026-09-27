import SwiftUI

// MARK: - Task state (presentation only)

/// Presentation state of a task card. It is derived from Rust-owned snapshot
/// data in the feature layer; this type never decides task state itself.
/// Connection state is shown separately (`BuddyConnectionState`), so a lost
/// connection never turns a remote task into "failed" or "completed".
enum BuddyTaskState: Equatable {
    case idle
    case running
    case stopping
    case awaitingApproval
    case awaitingInput
    case completed
    case failed
    case interrupted

    var title: LocalizedStringKey {
        switch self {
        case .idle: return "Idle"
        case .running: return "Running"
        case .stopping: return "Stopping…"
        case .awaitingApproval: return "Needs your OK"
        case .awaitingInput: return "Waiting for your reply"
        case .completed: return "Completed"
        case .failed: return "Failed"
        case .interrupted: return "Stopped"
        }
    }

    /// Shape carries meaning too, so state never depends on colour alone.
    var systemImage: String {
        switch self {
        case .idle: return "circle"
        case .running: return "circle.dotted"
        case .stopping: return "stop.circle"
        case .awaitingApproval: return "checkmark.shield"
        case .awaitingInput: return "text.bubble"
        case .completed: return "checkmark.circle"
        case .failed: return "exclamationmark.circle"
        case .interrupted: return "pause.circle"
        }
    }

    var fill: Color {
        switch self {
        case .running, .stopping: return AgentBuddyTheme.brand
        case .awaitingApproval, .awaitingInput: return AgentBuddyTheme.warningSurface
        case .completed: return AgentBuddyTheme.successSurface
        case .failed: return AgentBuddyTheme.dangerSurface
        case .idle, .interrupted: return AgentBuddyTheme.surfaceSoft
        }
    }

    var foreground: Color {
        switch self {
        case .running, .stopping: return AgentBuddyTheme.onBrand
        case .awaitingApproval, .awaitingInput: return AgentBuddyTheme.warning
        case .completed: return AgentBuddyTheme.success
        case .failed: return AgentBuddyTheme.danger
        case .idle, .interrupted: return AgentBuddyTheme.textSecondary
        }
    }

    var needsAttention: Bool {
        self == .awaitingApproval || self == .awaitingInput
    }
}

// MARK: - Connection state

enum BuddyConnectionState: Equatable {
    case discovering
    case connecting
    case connected
    case disconnected
    case failed

    var title: LocalizedStringKey {
        switch self {
        case .discovering: return "Looking for hosts…"
        case .connecting: return "Connecting…"
        case .connected: return "Connected"
        case .disconnected: return "Disconnected"
        case .failed: return "Connection failed"
        }
    }

    var dotColor: Color {
        switch self {
        case .connected: return AgentBuddyTheme.success
        case .connecting, .discovering: return AgentBuddyTheme.warning
        case .disconnected: return AgentBuddyTheme.textMuted
        case .failed: return AgentBuddyTheme.danger
        }
    }
}

// MARK: - Pills

/// Filled status capsule: icon + text on a semantic surface.
struct BuddyStatusPill: View {
    let state: BuddyTaskState
    var compact = false

    var body: some View {
        Label {
            Text(state.title)
        } icon: {
            Image(systemName: state.systemImage)
                .accessibilityHidden(true)
        }
        .labelStyle(.titleAndIcon)
        .buddyText(compact ? .caption : .label, weight: .medium)
        .foregroundStyle(state.foreground)
        .padding(.horizontal, compact ? BuddySpacing.xs : BuddySpacing.sm)
        .padding(.vertical, compact ? 3 : 6)
        .background(state.fill, in: Capsule())
        .fixedSize()
    }
}

/// Unfilled status line used inside a card that already carries the colour
/// ("○ 进行中" on the brand task card).
struct BuddyStatusLabel: View {
    let state: BuddyTaskState
    var tint: Color?

    var body: some View {
        Label {
            Text(state.title)
        } icon: {
            Image(systemName: state.systemImage)
                .fontWeight(.semibold)
                .accessibilityHidden(true)
        }
        .buddyText(.label, weight: .medium)
        .foregroundStyle(tint ?? state.foreground)
    }
}

/// Dot + text connection indicator ("● 已连接").
struct BuddyConnectionPill: View {
    let state: BuddyConnectionState
    var title: Text?
    var filled = true

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(state.dotColor)
                .frame(width: 8, height: 8)
                .accessibilityHidden(true)
            (title ?? Text(state.title))
                .buddyText(.label, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)
        }
        .padding(.horizontal, filled ? BuddySpacing.sm : 0)
        .frame(minHeight: filled ? BuddySize.compactPill : nil)
        .background(filled ? AgentBuddyTheme.surfaceSoft : .clear, in: Capsule())
    }
}
