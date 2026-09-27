import Foundation

enum ComposerApprovalOption: CaseIterable, Identifiable {
    case `default`
    case untrusted
    case onFailure
    case onRequest
    case never

    var id: String { wireValue }

    var title: String {
        switch self {
        case .default: return "Default"
        case .untrusted: return "Untrusted"
        case .onFailure: return "On failure"
        case .onRequest: return "On request"
        case .never: return "Never"
        }
    }

    var description: String {
        switch self {
        case .default: return "Use the thread or server default"
        case .untrusted: return "Always ask before taking action"
        case .onFailure: return "Ask only when a command fails"
        case .onRequest: return "Ask when escalation is requested"
        case .never: return "Run without asking for approval"
        }
    }

    var wireValue: String {
        switch self {
        case .default: return "inherit"
        case .untrusted: return "untrusted"
        case .onFailure: return "on-failure"
        case .onRequest: return "on-request"
        case .never: return "never"
        }
    }
}

enum ComposerSandboxOption: CaseIterable, Identifiable {
    case `default`
    case readOnly
    case workspaceWrite
    case fullAccess

    var id: String { wireValue }

    var title: String {
        switch self {
        case .default: return "Default"
        case .readOnly: return "Read only"
        case .workspaceWrite: return "Workspace write"
        case .fullAccess: return "Full access"
        }
    }

    var description: String {
        switch self {
        case .default: return "Use the thread or server default"
        case .readOnly: return "Can read files, but cannot edit them"
        case .workspaceWrite: return "Can edit files, but only in this workspace"
        case .fullAccess: return "Can edit files outside this workspace"
        }
    }

    var wireValue: String {
        switch self {
        case .default: return "inherit"
        case .readOnly: return "read-only"
        case .workspaceWrite: return "workspace-write"
        case .fullAccess: return "danger-full-access"
        }
    }
}
