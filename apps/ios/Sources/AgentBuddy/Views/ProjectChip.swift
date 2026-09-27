import SwiftUI

struct ProjectChip: View {
    let project: AppProject?
    let disabled: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                Image(systemName: "folder")
                    .font(.system(size: 13, weight: .medium))
                    .accessibilityHidden(true)
                Text(verbatim: label)
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
            }
            .buddyContextChip(isEnabled: !disabled)
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityLabel(Text("Project: \(label)"))
    }

    private var label: String {
        if let project {
            return projectDefaultLabel(cwd: project.cwd)
        }
        return disabled ? String(localized: "No host") : String(localized: "Choose project")
    }
}
