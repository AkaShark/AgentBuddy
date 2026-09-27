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
                    HStack(spacing: 8) {
                        runtimeKindPill(label: "All", icon: "square.grid.2x2", kind: nil)
                        ForEach(orderedRuntimeKinds(present: runtimeKinds), id: \.self) { kind in
                            runtimeKindPill(
                                label: runtimeKindLabel(kind),
                                icon: runtimeKindIcon(kind),
                                kind: kind
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.vertical, 6)
            }
        }
    }

    private func runtimeKindPill(label: String, icon: String, kind: AgentRuntimeKind?) -> some View {
        let isActive = selectedRuntimeKindFilter == kind
        return Button {
            selectedRuntimeKindFilter = kind
        } label: {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .agentBuddyFont(size: 10, weight: .semibold)
                Text(label)
                    .lineLimit(1)
            }
            .agentBuddyFont(.caption)
            .foregroundColor(isActive ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textSecondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(isActive ? AgentBuddyTheme.accent : AgentBuddyTheme.surface.opacity(0.65))
            .overlay(
                Capsule()
                    .stroke(isActive ? AgentBuddyTheme.accent : AgentBuddyTheme.border.opacity(0.7), lineWidth: 1)
            )
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private func orderedRuntimeKinds(present: Set<AgentRuntimeKind>) -> [AgentRuntimeKind] {
        AgentRuntimeKind.presentationOrder.filter { present.contains($0) }
    }

    private func runtimeKindLabel(_ kind: AgentRuntimeKind) -> String {
        kind.titleDisplayLabel
    }

    private func runtimeKindIcon(_ kind: AgentRuntimeKind) -> String {
        // The filter pill renders an SF Symbol; the alleycat manifest
        // ships a PNG, not an SF Symbol name, so we use a generic
        // fallback here. Richer rendering of the actual agent icon
        // happens via `AgentIconView` everywhere else in the app.
        _ = kind
        return "person.fill"
    }

    var sessionSearchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(AgentBuddyTheme.textMuted)
                .agentBuddyFont(.caption)

            TextField("Search sessions", text: $sessionSearchQuery)
                .agentBuddyFont(.footnote)
                .foregroundColor(AgentBuddyTheme.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)

            if !sessionSearchQuery.isEmpty {
                Button {
                    sessionSearchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(AgentBuddyTheme.textMuted)
                        .agentBuddyFont(size: 14)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(AgentBuddyTheme.surface.opacity(0.55))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(AgentBuddyTheme.border.opacity(0.85), lineWidth: 1)
        )
        .cornerRadius(8)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    var sessionFilterRow: some View {
        HStack(spacing: 8) {
            Menu {
                Button("All servers") { selectedServerFilterId = nil }
                ForEach(connectedServerOptions, id: \.id) { option in
                    Button(option.name) { selectedServerFilterId = option.id }
                }
            } label: {
                filterChip(
                    title: selectedServerFilterTitle,
                    isActive: selectedServerFilterId != nil,
                    icon: "server.rack"
                )
            }
            .buttonStyle(.plain)

            Button {
                showOnlyForks.toggle()
            } label: {
                filterChip(
                    title: "Forks",
                    isActive: showOnlyForks,
                    icon: "arrow.triangle.branch"
                )
            }
            .buttonStyle(.plain)

            Menu {
                ForEach(WorkspaceSortMode.allCases) { mode in
                    Button(mode.title) { workspaceSortMode = mode }
                }
            } label: {
                filterChip(
                    title: workspaceSortMode.title,
                    isActive: workspaceSortMode != .mostRecent,
                    icon: "arrow.up.arrow.down"
                )
            }
            .buttonStyle(.plain)

            if selectedServerFilterId != nil || showOnlyForks {
                Button("Clear") {
                    selectedServerFilterId = nil
                    showOnlyForks = false
                }
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.accent)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
    }

    private var selectedServerFilterTitle: String {
        guard let selectedServerFilterId else { return "All servers" }
        return connectedServerOptions.first(where: { $0.id == selectedServerFilterId })?.name ?? "All servers"
    }

    private func filterChip(title: String, isActive: Bool, icon: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .agentBuddyFont(size: 10, weight: .semibold)
            Text(title)
                .lineLimit(1)
        }
        .agentBuddyFont(.caption)
        .foregroundColor(isActive ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textSecondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(isActive ? AgentBuddyTheme.accent : AgentBuddyTheme.surface.opacity(0.65))
        .overlay(
            RoundedRectangle(cornerRadius: 7)
                .stroke(isActive ? AgentBuddyTheme.accent : AgentBuddyTheme.border.opacity(0.7), lineWidth: 1)
        )
        .cornerRadius(7)
    }
}
