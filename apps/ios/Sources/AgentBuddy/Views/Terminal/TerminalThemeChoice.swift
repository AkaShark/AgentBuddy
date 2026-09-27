import Foundation

enum TerminalThemeChoice: String, CaseIterable, Identifiable {
    case agentBuddyDark = "litter-dark"
    case catppuccinFrappe = "catppuccin-frappe"
    case catppuccinFrappeLight = "catppuccin-frappe-light"
    case solarizedDark = "solarized-dark"
    case solarizedLight = "solarized-light"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .agentBuddyDark: return "AgentBuddy Dark"
        case .catppuccinFrappe: return "Catppuccin Frappé"
        case .catppuccinFrappeLight: return "Catppuccin Frappé Light"
        case .solarizedDark: return "Solarized Dark"
        case .solarizedLight: return "Solarized Light"
        }
    }

    var preset: TerminalThemePreset {
        switch self {
        case .agentBuddyDark: return .litterDark
        case .catppuccinFrappe: return .catppuccinFrappe
        case .catppuccinFrappeLight: return .catppuccinFrappeLight
        case .solarizedDark: return .solarized(dark: true)
        case .solarizedLight: return .solarized(dark: false)
        }
    }

    static func preset(forId id: String) -> TerminalThemePreset {
        (TerminalThemeChoice(rawValue: id) ?? .agentBuddyDark).preset
    }
}
