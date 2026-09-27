import SwiftUI

/// "开始一个新想法" sheet on phone. Wraps the shared `NewThreadHeroView`
/// composer (same launch path as the iPad detail pane) and owns its own
/// project picker so the picker can present over the sheet.
struct NewTaskSheet: View {
    let model: HomeDashboardModel
    let onThreadCreated: (ThreadKey) -> Void
    let onCreateProject: () -> Void
    let onDismiss: () -> Void

    @State private var showsProjectPicker = false

    var body: some View {
        NavigationStack {
            NewThreadHeroView(
                project: model.selectedProject,
                connectedServers: model.connectedServers,
                selectedServerId: model.selectedServerId,
                onSelectServer: { serverId in
                    // Changing host re-validates the project for that host;
                    // the draft text stays in the composer.
                    model.selectedServerId = serverId
                },
                onOpenProjectPicker: { showsProjectPicker = true },
                onThreadCreated: onThreadCreated,
                onCancel: onDismiss,
                autoFocus: true
            )
        }
        .sheet(isPresented: $showsProjectPicker) {
            ProjectPickerSheet(
                projects: model.projects,
                serverNamesById: Dictionary(
                    model.connectedServers.map { ($0.id, $0.displayName) },
                    uniquingKeysWith: { first, _ in first }
                ),
                onSelect: { project in
                    model.selectedServerId = project.serverId
                    model.selectedProject = project
                },
                onCreateNew: {
                    showsProjectPicker = false
                    onCreateProject()
                }
            )
            .buddySheetStyle()
        }
        .buddySheetStyle()
    }
}
