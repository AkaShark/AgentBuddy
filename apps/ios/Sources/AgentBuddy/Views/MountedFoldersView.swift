import SwiftUI
import UniformTypeIdentifiers

struct MountedFoldersView: View {
    enum PickerMode: Equatable {
        case add
        case reconnect(UUID)
    }

    @Environment(\.dismiss) private var dismiss
    @State private var store = UserMountStore.shared
    @State private var pickerMode: PickerMode?
    @State private var pendingRemoval: UserMount?

    var body: some View {
        NavigationStack {
            Group {
                if store.mounts.isEmpty {
                    emptyState
                } else {
                    list
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .buddyPageBackground()
            .navigationTitle("Mounted folders")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Done") { dismiss() }
                        .fontWeight(.semibold)
                        .foregroundStyle(AgentBuddyTheme.link)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        pickerMode = .add
                    } label: {
                        Image(systemName: "plus")
                    }
                    .foregroundStyle(AgentBuddyTheme.link)
                    .accessibilityLabel(Text("Add folder"))
                }
            }
        }
        .buddySheetStyle()
        .fileImporter(
            isPresented: Binding(
                get: { pickerMode != nil },
                set: { if !$0 { pickerMode = nil } }
            ),
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            let mode = pickerMode
            pickerMode = nil
            handlePick(result: result, mode: mode)
        }
        .confirmationDialog(
            removalPrompt,
            isPresented: Binding(
                get: { pendingRemoval != nil },
                set: { if !$0 { pendingRemoval = nil } }
            ),
            titleVisibility: .visible,
            presenting: pendingRemoval
        ) { mount in
            Button("Remove", role: .destructive) {
                Task { await store.remove(id: mount.id) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: - List

    private var list: some View {
        ScrollView {
            VStack(spacing: BuddySpacing.sm) {
                ForEach(store.mounts) { mount in
                    row(for: mount)
                }
                footerExplainer
            }
            .padding(.horizontal, BuddySpacing.xl)
            .padding(.top, BuddySpacing.sm)
            .padding(.bottom, BuddySpacing.xl)
        }
    }

    private func row(for mount: UserMount) -> some View {
        let status = store.statuses[mount.id]
        return VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            HStack(spacing: BuddySpacing.sm) {
                statusIcon(for: status)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: mount.name)
                        .buddyText(.heading)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                    Text(verbatim: "/mnt/\(mount.name)")
                        .buddyText(.code)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                Spacer(minLength: BuddySpacing.xs)
                Menu {
                    if needsReconnect(status) {
                        Button {
                            pickerMode = .reconnect(mount.id)
                        } label: {
                            Label("Reconnect", systemImage: "arrow.clockwise")
                        }
                    }
                    Button(role: .destructive) {
                        pendingRemoval = mount
                    } label: {
                        Label("Remove", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Text("More actions"))
            }
            Text(verbatim: mount.displayPath)
                .buddyText(.code)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .lineLimit(2)
                .truncationMode(.middle)
            if let detail = statusDetail(for: status) {
                HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                    Image(systemName: "exclamationmark.circle")
                        .accessibilityHidden(true)
                    Text(verbatim: detail)
                        .buddyText(.label, weight: .regular)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .foregroundStyle(AgentBuddyTheme.danger)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.surface, radius: BuddyRadius.card, padding: BuddySpacing.md)
    }

    private var footerExplainer: some View {
        Text("Mounts persist across launches. Removing only detaches the mount inside iSH; files in the source folder are not deleted.")
            .buddyText(.caption)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, BuddySpacing.xxs)
            .padding(.top, BuddySpacing.xs)
    }

    // MARK: - Empty state

    private var emptyState: some View {
        VStack {
            BuddyEmptyState(
                systemImage: "externaldrive.badge.icloud",
                title: "No folders mounted",
                message: "Pick a folder from Files (iCloud Drive, On My iPhone, or a third-party provider) to make it available inside iSH at /mnt/<name>.",
                actionTitle: "Add folder",
                actionSystemImage: "plus"
            ) {
                pickerMode = .add
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, BuddySpacing.xl)
        .padding(.top, BuddySpacing.md)
    }

    // MARK: - Helpers

    private func handlePick(result: Result<[URL], Error>, mode: PickerMode?) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            Task {
                switch mode {
                case .reconnect(let id):
                    await store.reconnect(id: id, newUrl: url)
                case .add, .none:
                    await store.addByPicking(url: url)
                }
            }
        case .failure(let error):
            LLog.warn("mount", "file picker failed", fields: ["error": String(describing: error)])
        }
    }

    private func statusIcon(for status: MountStatus?) -> some View {
        let (symbol, tint): (String, Color) = {
            switch status {
            case .mounted:
                return ("checkmark.circle.fill", AgentBuddyTheme.success)
            case .resolutionFailed, .mountFailed:
                return ("exclamationmark.triangle.fill", AgentBuddyTheme.danger)
            case nil:
                return ("circle.dotted", AgentBuddyTheme.textSecondary)
            }
        }()
        return Image(systemName: symbol)
            .font(.system(size: 20, weight: .medium))
            .foregroundStyle(tint)
            .frame(width: 24)
            .accessibilityHidden(true)
    }

    private func statusDetail(for status: MountStatus?) -> String? {
        switch status {
        case .resolutionFailed(let message):
            return "Couldn't reach this folder: \(message)"
        case .mountFailed(let rc, let message):
            return "Mount failed (rc=\(rc)): \(message)"
        case .mounted, nil:
            return nil
        }
    }

    private func needsReconnect(_ status: MountStatus?) -> Bool {
        switch status {
        case .resolutionFailed, .mountFailed: return true
        case .mounted, nil: return false
        }
    }

    private var removalPrompt: String {
        if let pendingRemoval {
            return "Remove \(pendingRemoval.name)?"
        }
        return "Remove mount?"
    }
}

#if DEBUG
#Preview {
    MountedFoldersView()
}
#endif
