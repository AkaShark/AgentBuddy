import SwiftUI

/// Primary Apps surface: list of all `SavedApp`s on disk. Always-visible
/// from the Home Dashboard's Apps toolbar button.
struct AppsListView: View {
    @State private var store = SavedAppsStore.shared
    @State private var navigation = SavedAppsNavigation.shared
    @State private var renameTarget: SavedApp?
    @State private var renameText: String = ""
    @State private var deleteTarget: SavedApp?
    @State private var detailAppId: String?

    var body: some View {
        Group {
            if store.apps.isEmpty {
                emptyState
            } else {
                list
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .buddyPageBackground()
        .navigationTitle("Apps")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            store.reload()
            if let pending = navigation.consumeRequest() {
                detailAppId = pending
            }
        }
        .onChange(of: navigation.pendingOpenAppId) { _, newValue in
            if let id = newValue {
                detailAppId = id
                navigation.consumeRequest()
            }
        }
        .navigationDestination(item: $detailAppId) { appId in
            SavedAppDetailView(appId: appId)
        }
        .sheet(item: $renameTarget) { app in
            SavedAppRenameSheet(
                text: $renameText,
                onCancel: { renameTarget = nil },
                onSave: {
                    let trimmed = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { renameTarget = nil; return }
                    _ = try? store.rename(id: app.id, title: trimmed)
                    renameTarget = nil
                }
            )
            .presentationDetents([.medium])
            .buddySheetStyle()
        }
        .alert(
            "Delete \"\(deleteTarget?.title ?? "")\"?",
            isPresented: Binding(
                get: { deleteTarget != nil },
                set: { if !$0 { deleteTarget = nil } }
            )
        ) {
            Button("Cancel", role: .cancel) { deleteTarget = nil }
            Button("Delete", role: .destructive) {
                if let target = deleteTarget {
                    try? store.delete(id: target.id)
                }
                deleteTarget = nil
            }
        } message: {
            Text("This removes the app, its saved HTML, and its persisted state.")
        }
    }

    private var list: some View {
        List {
            ForEach(sortedApps, id: \.id) { app in
                Button {
                    detailAppId = app.id
                } label: {
                    row(for: app)
                }
                .buttonStyle(.plain)
                .listRowBackground(AgentBuddyTheme.surface)
                .listRowSeparatorTint(AgentBuddyTheme.border)
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        deleteTarget = app
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                    .tint(AgentBuddyTheme.danger)
                    Button {
                        renameText = app.title
                        renameTarget = app
                    } label: {
                        Label("Rename", systemImage: "pencil")
                    }
                    .tint(AgentBuddyTheme.link)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .listStyle(.insetGrouped)
    }

    private var sortedApps: [SavedApp] {
        store.apps.sorted(by: { $0.updatedAtMs > $1.updatedAtMs })
    }

    private func row(for app: SavedApp) -> some View {
        BuddyListRow(
            title: Text(verbatim: app.title),
            subtitle: Text("Updated \(relativeUpdated(app))")
        ) {
            BuddyIconTile(content: .initial(monogramInitials(from: app.title)))
        }
        .foregroundStyle(AgentBuddyTheme.textSecondary)
    }

    private func monogramInitials(from title: String) -> String {
        let words = title.split(separator: " ").prefix(2)
        let letters = words.compactMap { $0.first }.map(String.init).joined()
        return letters.isEmpty ? "?" : letters.uppercased()
    }

    private func relativeUpdated(_ app: SavedApp) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(app.updatedAtMs) / 1000.0)
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }

    private var emptyState: some View {
        VStack {
            BuddyEmptyState(
                systemImage: "square.grid.2x2",
                title: "No apps yet",
                message: "When the AI generates an interactive widget with an app_id, it saves here automatically."
            )
            Spacer(minLength: 0)
        }
        .padding(.horizontal, BuddySpacing.xl)
        .padding(.top, BuddySpacing.md)
    }
}

/// Rename sheet shared by the apps list and the saved-app screen: Mint sheet
/// with a bordered title field, Cancel and one primary Save.
struct SavedAppRenameSheet: View {
    @Binding var text: String
    let onCancel: () -> Void
    let onSave: () -> Void

    @FocusState private var isFocused: Bool

    private var canSave: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            Text("Rename App")
                .buddyText(.title)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)

            TextField("Title", text: $text)
                .focused($isFocused)
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .tint(AgentBuddyTheme.focus)
                .padding(.horizontal, BuddySpacing.md)
                .frame(minHeight: BuddySize.control)
                .background(AgentBuddyTheme.surface, in: shape)
                .overlay { shape.strokeBorder(isFocused ? AgentBuddyTheme.focus : AgentBuddyTheme.borderControl, lineWidth: 1) }

            HStack(spacing: BuddySpacing.sm) {
                BuddyButton("Cancel", kind: .secondary, action: onCancel)
                BuddyButton("Save", action: onSave)
                    .disabled(!canSave)
            }

            Spacer()
        }
        .padding(BuddySpacing.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyPageBackground()
    }
}
