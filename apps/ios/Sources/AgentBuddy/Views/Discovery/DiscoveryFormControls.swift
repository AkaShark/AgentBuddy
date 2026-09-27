import SwiftUI

// Mint form pieces shared by the add-host sheets (manual entry, SSH login,
// SSH partner picker, QR pairing). They only style; every binding and action
// stays with the sheet that owns it.

// MARK: - Section

/// Caption title, content, optional footer. Content sits on the page; fields
/// and rows carry their own surface.
struct DiscoveryFormSection<Content: View>: View {
    let title: Text?
    let footer: Text?
    @ViewBuilder var content: () -> Content

    init(_ title: LocalizedStringKey? = nil, footer: Text? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.title = title.map { Text($0) }
        self.footer = footer
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            if let title {
                title
                    .buddyText(.caption, weight: .medium)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityAddTraits(.isHeader)
            }
            content()
            if let footer {
                footer
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Field

private struct DiscoveryFieldModifier: ViewModifier {
    let minHeight: CGFloat
    let monospaced: Bool

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
        content
            .buddyText(monospaced ? .code : .body)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .tint(AgentBuddyTheme.focus)
            .padding(.horizontal, BuddySpacing.md)
            .frame(maxWidth: .infinity, minHeight: minHeight, alignment: .leading)
            .background(AgentBuddyTheme.surface, in: shape)
            .overlay { shape.strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1) }
    }
}

extension View {
    /// Mint text input: surface fill, borderControl outline, radius 16, at
    /// least 48pt tall. Pasted keys and JSON use the code face.
    func discoveryFieldStyle(minHeight: CGFloat = BuddySize.control, monospaced: Bool = false) -> some View {
        modifier(DiscoveryFieldModifier(minHeight: minHeight, monospaced: monospaced))
    }
}

/// Multi-line paste area (private key, pairing JSON) with a placeholder.
struct DiscoveryPasteEditor: View {
    @Binding var text: String
    let placeholder: String
    var minHeight: CGFloat = 120

    var body: some View {
        TextEditor(text: $text)
            .scrollContentBackground(.hidden)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
            .padding(.vertical, BuddySpacing.xxs)
            .overlay(alignment: .topLeading) {
                if text.isEmpty {
                    Text(verbatim: placeholder)
                        .buddyText(.code)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .padding(.top, BuddySpacing.sm)
                        .padding(.leading, BuddySpacing.xxs + 1)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
            .discoveryFieldStyle(minHeight: minHeight, monospaced: true)
    }
}

// MARK: - Segmented choice

/// Two or three mutually exclusive options on a surfaceSoft track. The
/// selected option gets a surface fill, bold label and the selected trait,
/// so it never depends on colour alone.
struct DiscoverySegmentedPicker<Value: Hashable>: View {
    let options: [(value: Value, title: LocalizedStringKey)]
    @Binding var selection: Value

    var body: some View {
        HStack(spacing: BuddySpacing.xxs) {
            ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                let isSelected = option.value == selection
                Button {
                    selection = option.value
                } label: {
                    Text(option.title)
                        .buddyText(.label, weight: isSelected ? .semibold : .medium)
                        .foregroundStyle(isSelected ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: BuddySize.minHitTarget)
                        .background {
                            if isSelected {
                                RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                                    .fill(AgentBuddyTheme.surface)
                                    .overlay {
                                        RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                                            .strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1)
                                    }
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(2)
        .background(
            AgentBuddyTheme.surfaceSoft,
            in: RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
        )
    }
}

// MARK: - Toggle row

struct DiscoveryToggleRow: View {
    let title: LocalizedStringKey
    var detail: LocalizedStringKey?
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                if let detail {
                    Text(detail)
                        .buddyText(.caption)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .tint(AgentBuddyTheme.action)
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.sm)
        .frame(minHeight: BuddySize.control)
        .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
    }
}

// MARK: - Host summary

/// Tile + name + address line at the top of a connect sheet.
struct DiscoveryHostSummary: View {
    let systemImage: String
    let name: String
    let address: String

    var body: some View {
        HStack(spacing: BuddySpacing.md) {
            BuddyIconTile(content: .symbol(systemImage))
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: name)
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
                Text(verbatim: address)
                    .buddyText(.code)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Partner rows

/// Selectable partner (agent) row: icon, name, detail line and a check
/// circle. Selected rows show a filled check, unselected an empty circle,
/// unavailable rows say so in text.
struct DiscoveryAgentRow: View {
    enum Mark {
        case selected
        case unselected
        /// Not selectable; `note` explains why when the detail line doesn't.
        case unavailable(note: LocalizedStringKey?)
    }

    let kind: AgentRuntimeKind
    let title: String
    let detail: String
    let isBeta: Bool
    let mark: Mark

    private var isUnavailable: Bool {
        if case .unavailable = mark { return true }
        return false
    }

    var body: some View {
        HStack(spacing: BuddySpacing.md) {
            AgentIconView(kind: kind, size: 28)
                .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
                .opacity(isUnavailable ? 0.45 : 1)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: BuddySpacing.xs) {
                    Text(verbatim: title)
                        .buddyText(.heading)
                        .foregroundStyle(isUnavailable ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.textPrimary)
                    if isBeta {
                        BetaBadge()
                    }
                }
                Text(verbatim: detail)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailingMark
        }
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.sm)
        .frame(minHeight: 64)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var isSelected: Bool {
        if case .selected = mark { return true }
        return false
    }

    @ViewBuilder
    private var trailingMark: some View {
        switch mark {
        case .selected:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(AgentBuddyTheme.action)
                .accessibilityHidden(true)
        case .unselected:
            Image(systemName: "circle")
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(AgentBuddyTheme.borderControl)
                .accessibilityHidden(true)
        case .unavailable(let note):
            if let note {
                Text(note)
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
        }
    }
}

/// Header for a partner list: caption title plus an "All / None" text action.
struct DiscoveryAgentListHeader: View {
    let showsToggle: Bool
    let allSelected: Bool
    let isEnabled: Bool
    let toggleAll: () -> Void

    var body: some View {
        HStack {
            Text("Agents")
                .buddyText(.caption, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            if showsToggle {
                Button(allSelected ? "None" : "All", action: toggleAll)
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.link)
                    .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
                    .buttonStyle(.plain)
                    .disabled(!isEnabled)
            }
        }
    }
}
