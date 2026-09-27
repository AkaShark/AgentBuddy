import SwiftUI

struct RuntimeFilterRow: View {
    let buckets: [RuntimeModelBucket]
    let totalCount: Int
    let selectedRuntime: AgentRuntimeKind?
    let onSelect: (AgentRuntimeKind?) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                RuntimeFilterPill(
                    label: "All",
                    count: totalCount,
                    selected: selectedRuntime == nil,
                    onTap: { onSelect(nil) }
                )
                ForEach(buckets) { bucket in
                    RuntimeFilterPill(
                        label: bucket.kind.titleDisplayLabel,
                        count: bucket.count,
                        kind: bucket.kind,
                        selected: selectedRuntime == bucket.kind,
                        onTap: { onSelect(bucket.kind) }
                    )
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

private struct RuntimeFilterPill: View {
    let label: String
    let count: Int
    var kind: AgentRuntimeKind? = nil
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 5) {
                if let kind {
                    AgentIconView(kind: kind, size: 12)
                }
                Text("\(label) \(count)")
                    .lineLimit(1)
            }
            .agentBuddyFont(.caption2, weight: .medium)
            .foregroundColor(selected ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textPrimary)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(selected ? AgentBuddyTheme.accent : AgentBuddyTheme.surfaceLight)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
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
