import SwiftUI

func timelineFormatDuration(_ durationMs: Int?) -> String? {
    guard let durationMs, durationMs >= 0 else { return nil }
    if durationMs >= 1_000 {
        return String(format: "%.1fs", Double(durationMs) / 1_000.0)
    }
    return "\(durationMs)ms"
}

extension ToolCallStatus {
    var themeColor: Color {
        switch self {
        case .completed:
            return AgentBuddyTheme.success
        case .inProgress:
            return AgentBuddyTheme.warning
        case .failed:
            return AgentBuddyTheme.danger
        case .unknown:
            return AgentBuddyTheme.textSecondary
        }
    }
}
