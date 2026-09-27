import SwiftUI

extension SessionsScreen {
    var runtimeKindPillRow: some View {
        // Only show pills when the current sessions list actually contains
        // multiple runtimes — a single-runtime user (Codex-only) doesn't
        // need to filter, and the row would just be visual noise.
        let runtimeKinds = Set(sessionsModel.derivedData.allThreads.map(\.agentRuntimeKind))
        return Group {
            if runtimeKinds.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: BuddySpacing.xs) {
                        runtimeKindPill(label: Text("All"), kind: nil)
                        ForEach(orderedRuntimeKinds(present: runtimeKinds), id: \.self) { kind in
                            runtimeKindPill(
                                label: Text(verbatim: runtimeKindLabel(kind)),
                                kind: kind
                            )
                        }
                    }
                    .padding(.horizontal, BuddySpacing.xl)
                }
                .sessionsListRow(horizontal: 0)
            }
        }
    }

    private func runtimeKindPill(label: Text, kind: AgentRuntimeKind?) -> some View {
        Button {
            selectedRuntimeKindFilter = kind
        } label: {
            SessionsFilterChip(
                title: label,
                systemImage: kind == nil ? "square.grid.2x2" : nil,
                agentKind: kind,
                isSelected: selectedRuntimeKindFilter == kind
            )
        }
        .buttonStyle(.plain)
    }

    private func orderedRuntimeKinds(present: Set<AgentRuntimeKind>) -> [AgentRuntimeKind] {
        AgentRuntimeKind.presentationOrder.filter { present.contains($0) }
    }

    private func runtimeKindLabel(_ kind: AgentRuntimeKind) -> String {
        kind.titleDisplayLabel
    }

    /// Mint search field: surface fill, required-control outline, radius 16, 48pt.
    var sessionSearchBar: some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
        return HStack(spacing: BuddySpacing.xs) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityHidden(true)

            TextField("Search tasks", text: $sessionSearchQuery)
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .submitLabel(.search)

            if !sessionSearchQuery.isEmpty {
                Button {
                    sessionSearchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 17))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Clear search"))
            }
        }
        .padding(.leading, BuddySpacing.md)
        .padding(.trailing, sessionSearchQuery.isEmpty ? BuddySpacing.md : BuddySpacing.xxs)
        .frame(minHeight: BuddySize.control)
        .background(AgentBuddyTheme.surface, in: shape)
        .overlay {
            shape.strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1)
        }
    }

    var sessionFilterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: BuddySpacing.xs) {
                Menu {
                    Button("All hosts") { selectedServerFilterId = nil }
                    ForEach(connectedServerOptions, id: \.id) { option in
                        Button(option.name) { selectedServerFilterId = option.id }
                    }
                } label: {
                    SessionsFilterChip(
                        title: selectedServerFilterTitle,
                        systemImage: "laptopcomputer",
                        isSelected: selectedServerFilterId != nil,
                        opensMenu: true
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Show tasks from"))
                .accessibilityValue(selectedServerFilterTitle)

                Button {
                    showOnlyForks.toggle()
                } label: {
                    SessionsFilterChip(
                        title: Text("Forks only"),
                        systemImage: "arrow.triangle.branch",
                        isSelected: showOnlyForks
                    )
                }
                .buttonStyle(.plain)

                Menu {
                    ForEach(WorkspaceSortMode.allCases) { mode in
                        Button {
                            workspaceSortMode = mode
                        } label: {
                            Text(LocalizedStringKey(mode.title))
                        }
                    }
                } label: {
                    SessionsFilterChip(
                        title: Text(LocalizedStringKey(workspaceSortMode.title)),
                        systemImage: "arrow.up.arrow.down",
                        isSelected: workspaceSortMode != .mostRecent,
                        opensMenu: true
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Sort"))
                .accessibilityValue(Text(LocalizedStringKey(workspaceSortMode.title)))

                if selectedServerFilterId != nil || showOnlyForks {
                    Button {
                        selectedServerFilterId = nil
                        showOnlyForks = false
                    } label: {
                        Text("Clear filters")
                            .buddyText(.label, weight: .semibold)
                            .foregroundStyle(AgentBuddyTheme.link)
                            .padding(.horizontal, BuddySpacing.xs)
                            .frame(minHeight: BuddySize.minHitTarget)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, BuddySpacing.xl)
        }
        .sessionsListRow(horizontal: 0)
    }

    private var selectedServerFilterTitle: Text {
        guard let selectedServerFilterId,
              let name = connectedServerOptions.first(where: { $0.id == selectedServerFilterId })?.name
        else { return Text("All hosts") }
        return Text(verbatim: name)
    }
}
