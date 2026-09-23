import SwiftUI

struct ProjectPickerSheet: View {
    let projects: [AppProject]
    let serverNamesById: [String: String]
    let onSelect: (AppProject) -> Void
    let onCreateNew: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(AppModel.self) private var appModel
    @State private var query = ""

    private var filtered: [AppProject] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { return projects }
        return projects.filter { project in
            let label = projectDefaultLabel(cwd: project.cwd).lowercased()
            let server = (serverNamesById[project.serverId] ?? "").lowercased()
            return label.contains(trimmed)
                || project.cwd.lowercased().contains(trimmed)
                || server.contains(trimmed)
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                search
                Divider().opacity(0.3)
                list
            }
            .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Projects")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { dismiss() }
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        onCreateNew()
                    } label: {
                        Label("New Project", systemImage: "plus")
                            .foregroundStyle(AgentBuddyTheme.accent)
                    }
                }
            }
        }
    }

    private var search: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(AgentBuddyTheme.textMuted)
            TextField("Search projects", text: $query)
                .agentBuddyFont(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .tint(AgentBuddyTheme.accent)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    @ViewBuilder
    private var list: some View {
        if filtered.isEmpty {
            emptyState
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(filtered, id: \.id) { project in
                        row(for: project)
                        Divider().opacity(0.15)
                    }
                }
            }
        }
    }

    private func row(for project: AppProject) -> some View {
        Button {
            onSelect(project)
            dismiss()
        } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "folder")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .frame(width: 22, alignment: .center)
                    .padding(.top, 2)

                VStack(alignment: .leading, spacing: 2) {
                    Text(projectDefaultLabel(cwd: project.cwd))
                        .agentBuddyFont(.body, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .lineLimit(1)
                    HStack(spacing: 6) {
                        if let serverName = serverNamesById[project.serverId] {
                            Text(serverName)
                                .foregroundStyle(AgentBuddyTheme.accent.opacity(0.75))
                        }
                        Text(PathDisplay.display(project.cwd, isLocal: appModel.isLocalServer(serverId: project.serverId)))
                            .foregroundStyle(AgentBuddyTheme.textMuted)
                    }
                    .agentBuddyMonoFont(size: 11, weight: .regular)
                    .lineLimit(1)
                }

                Spacer(minLength: 8)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "folder.badge.plus")
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(AgentBuddyTheme.textMuted)
            Text("No projects yet")
                .agentBuddyFont(.body, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            Text("Tap + to pick a directory and start your first thread.")
                .agentBuddyFont(.footnote)
                .foregroundStyle(AgentBuddyTheme.textMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button {
                onCreateNew()
            } label: {
                Text("New Project")
                    .agentBuddyFont(.footnote, weight: .semibold)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(AgentBuddyTheme.accent.opacity(0.15)))
                    .foregroundStyle(AgentBuddyTheme.accent)
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.vertical, 60)
    }
}
