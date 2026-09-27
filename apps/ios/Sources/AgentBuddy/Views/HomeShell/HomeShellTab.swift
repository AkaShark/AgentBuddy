import SwiftUI

/// The three primary destinations on phone: 任务 / 项目 / 主机.
/// Conversations are pushed on top of the shell as their own navigation
/// layer, so the tab bar never competes with the composer.
enum HomeShellTab: String, CaseIterable, Identifiable {
    case tasks
    case projects
    case hosts

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .tasks: return "Tasks"
        case .projects: return "Projects"
        case .hosts: return "Hosts"
        }
    }

    var systemImage: String {
        switch self {
        case .tasks: return "rectangle.stack"
        case .projects: return "folder"
        case .hosts: return "laptopcomputer"
        }
    }

    var selectedSystemImage: String {
        switch self {
        case .tasks: return "rectangle.stack.fill"
        case .projects: return "folder.fill"
        case .hosts: return "laptopcomputer"
        }
    }

    /// Whether the "有个想法？" composer pill sits above the tab bar.
    var showsComposerPill: Bool {
        self != .hosts
    }
}
