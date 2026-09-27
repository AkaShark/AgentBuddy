import SwiftUI

/// Project (host + folder) picker sheet. Every row shows its host so projects
/// with the same folder name on different computers stay distinguishable.
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
            ScrollView {
                VStack(alignment: .leading, spacing: BuddySpacing.md) {
                    search
                    if filtered.isEmpty {
                        emptyState
                    } else {
                        VStack(spacing: 0) {
                            ForEach(Array(filtered.enumerated()), id: \.element.id) { index, project in
                                if index > 0 { BuddyDivider() }
                                row(for: project)
                            }
                        }
                    }
                }
                .padding(.horizontal, BuddySpacing.xl)
                .padding(.top, BuddySpacing.xs)
                .padding(.bottom, BuddySpacing.xl)
            }
            .buddyPageBackground()
            .navigationTitle("Projects")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { dismiss() }
                        .foregroundStyle(AgentBuddyTheme.link)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onCreateNew) {
                        Label("New project", systemImage: "plus")
                    }
                    .foregroundStyle(AgentBuddyTheme.link)
                }
            }
        }
    }

    private var search: some View {
        HStack(spacing: BuddySpacing.xs) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityHidden(true)
            TextField("Search projects", text: $query)
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .tint(AgentBuddyTheme.focus)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Clear search"))
            }
        }
        .padding(.leading, BuddySpacing.md)
        .frame(minHeight: BuddySize.control)
        .background(AgentBuddyTheme.surface, in: RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
                .strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1)
        }
    }

    private func row(for project: AppProject) -> some View {
        let name = projectDefaultLabel(cwd: project.cwd)
        let path = PathDisplay.display(project.cwd, isLocal: appModel.isLocalServer(serverId: project.serverId))
        let detail = [serverNamesById[project.serverId], path].compactMap { $0 }.joined(separator: " · ")
        return Button {
            onSelect(project)
            dismiss()
        } label: {
            BuddyListRow(title: Text(verbatim: name), subtitle: Text(verbatim: detail)) {
                BuddyIconTile(content: .initial(name.first.map { String($0).uppercased() } ?? "#"))
            }
            .foregroundStyle(AgentBuddyTheme.textSecondary)
        }
        .buttonStyle(.plain)
    }

    private var emptyState: some View {
        BuddyEmptyState(
            systemImage: query.isEmpty ? "folder.badge.plus" : "magnifyingglass",
            title: query.isEmpty ? "No projects yet" : "No matching projects",
            message: query.isEmpty
                ? "Pick a folder on one of your hosts to create your first project."
                : "Try another name, host or path, or open a new folder.",
            actionTitle: "New project",
            actionSystemImage: "plus",
            actionKind: query.isEmpty ? .primary : .secondary,
            action: onCreateNew
        )
    }
}
