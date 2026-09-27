import SwiftUI

// MARK: - Partner (runtime) filter

/// Chip row that narrows the model list to one partner runtime. Shown when a
/// host offers more than one partner.
struct RuntimeFilterRow: View {
    let buckets: [RuntimeModelBucket]
    let totalCount: Int
    let selectedRuntime: AgentRuntimeKind?
    var contentInset: CGFloat = BuddySpacing.md
    let onSelect: (AgentRuntimeKind?) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: BuddySpacing.xs) {
                ModelPickerChip(
                    title: Text("All"),
                    count: totalCount,
                    isSelected: selectedRuntime == nil,
                    action: { onSelect(nil) }
                )
                ForEach(buckets) { bucket in
                    ModelPickerChip(
                        title: Text(verbatim: bucket.kind.titleDisplayLabel),
                        count: bucket.count,
                        kind: bucket.kind,
                        isSelected: selectedRuntime == bucket.kind,
                        action: { onSelect(bucket.kind) }
                    )
                }
            }
            .padding(.horizontal, contentInset)
        }
    }
}

/// "Partner" group of the model panel: the runtime filter chips when there is
/// a choice, or the single available partner as a static chip.
struct ModelPickerPartnerSection: View {
    let buckets: [RuntimeModelBucket]
    let totalCount: Int
    let selectedRuntime: AgentRuntimeKind?
    var contentInset: CGFloat = BuddySpacing.md
    let onSelect: (AgentRuntimeKind?) -> Void

    var body: some View {
        if !buckets.isEmpty {
            VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                ModelPickerSectionLabel(title: "Partner")
                    .padding(.horizontal, contentInset)
                if buckets.count > 1 {
                    RuntimeFilterRow(
                        buckets: buckets,
                        totalCount: totalCount,
                        selectedRuntime: selectedRuntime,
                        contentInset: contentInset,
                        onSelect: onSelect
                    )
                } else if let only = buckets.first {
                    HStack(spacing: 6) {
                        AgentIconView(kind: only.kind, size: 16)
                            .accessibilityHidden(true)
                        Text(verbatim: only.kind.titleDisplayLabel)
                            .lineLimit(1)
                    }
                    .buddyContextChip()
                    .padding(.horizontal, contentInset)
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }
}

// MARK: - Building blocks

/// Caption header separating the partner, model, reasoning and option groups.
struct ModelPickerSectionLabel: View {
    let title: LocalizedStringKey

    var body: some View {
        Text(title)
            .buddyText(.caption, weight: .medium)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Selectable capsule (partner filter, reasoning effort). The selection is
/// shown by fill, a checkmark and a heavier weight, never by colour alone.
/// The visual pill is 34pt; the hit area is 44pt.
struct ModelPickerChip: View {
    let title: Text
    var count: Int?
    var kind: AgentRuntimeKind?
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                        .accessibilityHidden(true)
                }
                if let kind {
                    AgentIconView(kind: kind, size: 16)
                        .accessibilityHidden(true)
                }
                title
                    .lineLimit(1)
                if let count {
                    Text(verbatim: "\(count)")
                        .monospacedDigit()
                        .fontWeight(.regular)
                }
            }
            .buddyText(.label, weight: isSelected ? .semibold : .medium)
            .foregroundStyle(isSelected ? AgentBuddyTheme.onAction : AgentBuddyTheme.textPrimary)
            .padding(.horizontal, BuddySpacing.sm)
            .frame(minHeight: BuddySize.compactPill)
            .background(isSelected ? AgentBuddyTheme.action : AgentBuddyTheme.surfaceSoft, in: Capsule())
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Mint search field: surface fill, control outline, 48pt tall, 16pt text.
struct ModelPickerSearchField: View {
    @Binding var query: String

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
        HStack(spacing: BuddySpacing.xs) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityHidden(true)
            TextField("Search models", text: $query)
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
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Clear search"))
            }
        }
        .padding(.leading, BuddySpacing.md)
        .padding(.trailing, query.isEmpty ? BuddySpacing.md : 0)
        .frame(minHeight: BuddySize.control)
        .background(AgentBuddyTheme.surface, in: shape)
        .overlay { shape.strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1) }
    }
}

/// One model: runtime icon, name, optional "default" tag and description.
/// The selected model gets a checkmark and a semibold name.
struct ModelPickerRow: View {
    let model: ModelInfo
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: BuddySpacing.sm) {
                ModelRuntimeIcon(kind: model.agentRuntimeKind)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: BuddySpacing.xs) {
                        Text(verbatim: modelPickerDisplayName(model))
                            .buddyText(.body, weight: isSelected ? .semibold : .regular)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .lineLimit(2)
                        if model.isDefault {
                            Text("default")
                                .buddyText(.caption, weight: .medium)
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                                .padding(.horizontal, BuddySpacing.xs)
                                .background(AgentBuddyTheme.surfaceSoft, in: Capsule())
                        }
                    }
                    if !model.description.isEmpty {
                        Text(verbatim: model.description)
                            .buddyText(.caption)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "checkmark")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.link)
                    .opacity(isSelected ? 1 : 0)
                    .accessibilityHidden(true)
            }
            .padding(.vertical, BuddySpacing.sm)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Reasoning effort as selectable chips.
struct ModelPickerEffortChips: View {
    let efforts: [ReasoningEffortOption]
    let selection: String
    var contentInset: CGFloat = BuddySpacing.md
    let onSelect: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: BuddySpacing.xs) {
                ForEach(efforts) { effort in
                    let value = effort.reasoningEffort.wireValue
                    ModelPickerChip(
                        title: Text(verbatim: value),
                        isSelected: value == selection,
                        action: { onSelect(value) }
                    )
                }
            }
            .padding(.horizontal, contentInset)
        }
    }
}

/// Shown instead of the effort chips when the partner fixes the effort once
/// the task has started.
struct ModelPickerLockedEffortNote: View {
    var body: some View {
        Label {
            Text("Reasoning effort is locked after the first message.")
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "lock")
                .accessibilityHidden(true)
        }
        .buddyText(.label, weight: .regular)
        .foregroundStyle(AgentBuddyTheme.textSecondary)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Toggle capsule for Plan / Fast / Full access. On: action fill with a
/// checkmark (or the danger surface for `isDanger`); off: soft fill. The
/// 34pt pill sits in a 44pt hit area.
struct ModelPickerToggleChip: View {
    let title: LocalizedStringKey
    let systemImage: String
    let isOn: Bool
    var isDanger = false
    var hint: Text?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: isOn && !isDanger ? "checkmark" : systemImage)
                    .font(.system(size: 12, weight: .bold))
                    .accessibilityHidden(true)
                Text(title)
                    .lineLimit(1)
            }
            .buddyText(.label, weight: isOn ? .semibold : .medium)
            .foregroundStyle(foreground)
            .padding(.horizontal, BuddySpacing.sm)
            .frame(minHeight: BuddySize.compactPill)
            .background(fill, in: Capsule())
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue(isOn ? Text("On") : Text("Off"))
        .accessibilityHint(hint ?? Text(verbatim: ""))
    }

    private var foreground: Color {
        guard isOn else { return AgentBuddyTheme.textPrimary }
        return isDanger ? AgentBuddyTheme.danger : AgentBuddyTheme.onAction
    }

    private var fill: Color {
        guard isOn else { return AgentBuddyTheme.surfaceSoft }
        return isDanger ? AgentBuddyTheme.dangerSurface : AgentBuddyTheme.action
    }
}

/// One-line explanation under the toggles of what the current access
/// level allows. Full access is flagged with the danger role and an icon.
struct ModelPickerAccessNote: View {
    let isFullAccess: Bool

    var body: some View {
        Label {
            (isFullAccess
                ? Text("Full access: runs commands and edits files anywhere on the host, without asking.")
                : Text("Supervised: works in this project and asks before doing more."))
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: isFullAccess ? "exclamationmark.triangle.fill" : "lock")
                .accessibilityHidden(true)
        }
        .buddyText(.caption)
        .foregroundStyle(isFullAccess ? AgentBuddyTheme.danger : AgentBuddyTheme.textSecondary)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct ModelRuntimeIcon: View {
    let kind: AgentRuntimeKind

    var body: some View {
        AgentIconView(kind: kind, size: 20)
            .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            .accessibilityLabel(kind.displayLabel)
    }
}
