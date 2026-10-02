import SwiftUI

// MARK: - Card surfaces

/// Semantic card fills. Content is grouped with whitespace first; a card is used
/// when something is a unit the user acts on (task, host, confirmation).
enum BuddySurfaceTone {
    /// surface with a hairline border (host cards, detail summaries).
    case surface
    /// surfaceSoft, no border (project hero, result card).
    case soft
    /// brand (the current / running task).
    case brand
    /// warningSurface (confirmation needed).
    case warning
    /// successSurface.
    case success
    /// errorSurface.
    case danger

    var fill: Color {
        switch self {
        case .surface: return AgentBuddyTheme.surface
        case .soft: return AgentBuddyTheme.surfaceSoft
        case .brand: return AgentBuddyTheme.brand
        case .warning: return AgentBuddyTheme.warningSurface
        case .success: return AgentBuddyTheme.successSurface
        case .danger: return AgentBuddyTheme.dangerSurface
        }
    }

    var hasBorder: Bool { self == .surface }
}

private struct BuddyCardModifier: ViewModifier {
    let tone: BuddySurfaceTone
    let radius: CGFloat
    let padding: CGFloat?

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        content
            .padding(padding ?? 0)
            .background(tone.fill, in: shape)
            .overlay {
                if tone.hasBorder {
                    shape.strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
                }
            }
            .clipShape(shape)
    }
}

extension View {
    /// Wraps content in a card with a semantic fill and a fixed component radius.
    func buddyCard(
        _ tone: BuddySurfaceTone = .surface,
        radius: CGFloat = BuddyRadius.card,
        padding: CGFloat? = BuddySpacing.lg
    ) -> some View {
        modifier(BuddyCardModifier(tone: tone, radius: radius, padding: padding))
    }

    /// Page background for Mint screens.
    func buddyPageBackground() -> some View {
        background(AgentBuddyTheme.background.ignoresSafeArea())
    }
}

// MARK: - Shapes

/// User message bubble: 19 / 19 / 5 / 19 (top-leading, top-trailing,
/// bottom-trailing, bottom-leading). Never collapse to four equal corners.
struct BuddyUserBubbleShape: Shape {
    func path(in rect: CGRect) -> Path {
        UnevenRoundedRectangle(
            topLeadingRadius: BuddyBubbleRadius.large,
            bottomLeadingRadius: BuddyBubbleRadius.large,
            bottomTrailingRadius: BuddyBubbleRadius.tail,
            topTrailingRadius: BuddyBubbleRadius.large,
            style: .continuous
        )
        .path(in: rect)
    }
}

// MARK: - Chips

/// Capsule tag for project / partner names. End caps are always half the
/// visual height.
struct BuddyChip: View {
    enum Tone {
        /// surfaceSoft (on page or white cards).
        case soft
        /// Subtle contrasting fill on the brand card.
        case onBrand
        /// surface with border.
        case outline
    }

    let text: Text
    var systemImage: String?
    var tone: Tone = .soft

    init(_ title: String, systemImage: String? = nil, tone: Tone = .soft) {
        self.text = Text(verbatim: title)
        self.systemImage = systemImage
        self.tone = tone
    }

    init(localized title: LocalizedStringKey, systemImage: String? = nil, tone: Tone = .soft) {
        self.text = Text(title)
        self.systemImage = systemImage
        self.tone = tone
    }

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                Image(systemName: systemImage)
                    .imageScale(.small)
                    .accessibilityHidden(true)
            }
            text.lineLimit(1)
        }
        .buddyText(.label)
        .foregroundStyle(tone == .onBrand ? AgentBuddyTheme.onBrand : AgentBuddyTheme.textPrimary)
        .padding(.horizontal, BuddySpacing.sm + 2)
        .frame(minHeight: 32)
        .background(fill, in: Capsule())
        .overlay {
            if tone == .outline {
                Capsule().strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
            }
        }
    }

    private var fill: Color {
        switch tone {
        case .soft: return AgentBuddyTheme.surfaceSoft
        case .onBrand: return AgentBuddyTheme.brandChipFill
        case .outline: return AgentBuddyTheme.surface
        }
    }
}

// MARK: - Icon tile

/// Rounded square tile used at the leading edge of rows and cards.
struct BuddyIconTile: View {
    enum Content {
        case symbol(String)
        case initial(String)
        case brandMark
    }

    let content: Content
    var fill: Color = AgentBuddyTheme.surfaceSoft
    var foreground: Color = AgentBuddyTheme.textPrimary
    var size: CGFloat = BuddySize.rowTile

    private var cornerRadius: CGFloat {
        if case .brandMark = content { return size * BuddyRadius.brandTileRatio }
        return min(BuddyRadius.tile, size * 0.36)
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(fill)
            switch content {
            case .symbol(let name):
                Image(systemName: name)
                    .font(.system(size: size * 0.42, weight: .medium))
            case .initial(let letter):
                Text(verbatim: letter)
                    .font(.system(size: size * 0.42, weight: .semibold))
            case .brandMark:
                BuddyLinkShape()
                    .stroke(style: StrokeStyle(lineWidth: size * 0.8 * 2.8 / 24, lineCap: .round, lineJoin: .round))
                    .frame(width: size * 0.8, height: size * 0.8)
            }
        }
        .foregroundStyle(foreground)
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

// MARK: - Context chip

/// Tappable context chip (host / project / partner): surfaceSoft capsule at
/// compact-pill height inside a 44pt hit area.
private struct BuddyContextChipModifier: ViewModifier {
    let isEnabled: Bool

    func body(content: Content) -> some View {
        content
            .buddyText(.label)
            .foregroundStyle(isEnabled ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.onDisabled)
            .lineLimit(1)
            .padding(.horizontal, BuddySpacing.sm)
            .frame(minHeight: BuddySize.compactPill)
            .background(isEnabled ? AgentBuddyTheme.surfaceSoft : AgentBuddyTheme.disabled, in: Capsule())
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
    }
}

extension View {
    func buddyContextChip(isEnabled: Bool = true) -> some View {
        modifier(BuddyContextChipModifier(isEnabled: isEnabled))
    }
}
