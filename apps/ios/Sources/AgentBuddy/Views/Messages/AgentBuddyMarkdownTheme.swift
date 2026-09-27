import SwiftUI
import Hairball
import HairballUI
import UIKit

// MARK: - Mint conversation sizes

extension AgentBuddyFont {
    /// Mint body size (16pt at the default Dynamic Type size) used for
    /// assistant prose; line height 26 comes from `markdownLineSpacing`.
    static var mintBodyPointSize: CGFloat {
        UIFontMetrics(forTextStyle: .body).scaledValue(for: BuddyTextStyle.body.size)
    }

    /// Mint code size (14pt at the default Dynamic Type size).
    static var mintCodePointSize: CGFloat {
        UIFontMetrics(forTextStyle: .callout).scaledValue(for: BuddyTextStyle.code.size)
    }
}

/// Extra spacing SwiftUI adds between lines so body text reaches the Mint
/// 16 / 26 rhythm (the font's natural line height is about 1.2× its size).
private func markdownLineSpacing(bodySize: CGFloat) -> CGFloat {
    max(0, bodySize * (BuddyTextStyle.body.lineHeight / BuddyTextStyle.body.size - 1.2))
}

// MARK: - AgentBuddy Markdown Themes

private func agentBuddyContentTheme(bodySize: CGFloat, codeSize: CGFloat) -> MarkdownTheme {
    var theme = MarkdownTheme.default
    theme.bodyFont = .custom(AgentBuddyFont.markdownFontName, size: bodySize)
    theme.bodyFontSize = bodySize
    theme.foregroundColor = AgentBuddyTheme.textPrimary
    theme.lineSpacing = markdownLineSpacing(bodySize: bodySize)
    theme.paragraphSpacing = 12
    theme.blockSpacing = 12

    theme.headingStyleSet = HeadingStyleSet(
        h1: HeadingStyle(fontSize: bodySize * 1.5, weight: .semibold,
                         topSpacing: 16, bottomSpacing: 8, color: AgentBuddyTheme.textPrimary),
        h2: HeadingStyle(fontSize: bodySize * 1.25, weight: .semibold,
                         topSpacing: 12, bottomSpacing: 6, color: AgentBuddyTheme.textPrimary),
        h3: HeadingStyle(fontSize: bodySize * 1.0625, weight: .semibold,
                         topSpacing: 10, bottomSpacing: 4, color: AgentBuddyTheme.textPrimary),
        h4: HeadingStyle(fontSize: bodySize, weight: .semibold, color: AgentBuddyTheme.textPrimary),
        h5: HeadingStyle(fontSize: bodySize, weight: .semibold, color: AgentBuddyTheme.textPrimary),
        h6: HeadingStyle(fontSize: bodySize, weight: .semibold, color: AgentBuddyTheme.textPrimary)
    )

    theme.inlineCode = InlineCodeStyle(
        backgroundColor: AgentBuddyTheme.surfaceSoft,
        textColor: AgentBuddyTheme.textPrimary,
        font: AgentBuddyFont.monospaced(size: codeSize),
        fontSize: codeSize
    )

    theme.codeBlock = CodeBlockStyle(
        backgroundColor: TimelineCodeStyle.fill(nested: false),
        textColor: AgentBuddyTheme.textPrimary,
        font: AgentBuddyFont.monospaced(size: codeSize),
        fontSize: codeSize,
        cornerRadius: BuddyRadius.control,
        showLanguageLabel: false,
        showCopyButton: false
    )

    theme.blockquote = BlockquoteStyle(
        borderColor: AgentBuddyTheme.border,
        borderWidth: 3,
        textColor: AgentBuddyTheme.textSecondary,
        padding: EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 4)
    )

    theme.table = TableStyle(
        borderStyle: .solid(color: AgentBuddyTheme.border, width: 1),
        headerBackground: AgentBuddyTheme.surfaceSoft,
        headerFontWeight: .semibold,
        backgroundStyle: .alternatingRows(
            even: AgentBuddyTheme.surface,
            odd: .clear
        ),
        cornerRadius: BuddyRadius.control
    )

    theme.list = ListStyleConfiguration(
        bulletMarker: .bullet,
        itemSpacing: 4,
        tightItemSpacing: 4
    )

    theme.link = LinkStyle(color: AgentBuddyTheme.link, underline: false)

    theme.thematicBreak = ThematicBreakStyle(
        color: AgentBuddyTheme.border,
        verticalPadding: 12
    )

    return theme
}

private func agentBuddySystemTheme(bodySize: CGFloat, codeSize: CGFloat) -> MarkdownTheme {
    var theme = MarkdownTheme.default
    theme.bodyFont = .custom(AgentBuddyFont.markdownFontName, size: bodySize)
    theme.bodyFontSize = bodySize
    theme.foregroundColor = AgentBuddyTheme.textSecondary
    theme.lineSpacing = markdownLineSpacing(bodySize: bodySize)
    theme.paragraphSpacing = 8
    theme.blockSpacing = 8

    theme.headingStyleSet = HeadingStyleSet(
        h1: HeadingStyle(fontSize: bodySize * 1.31, weight: .semibold,
                         topSpacing: 12, bottomSpacing: 6, color: AgentBuddyTheme.textPrimary),
        h2: HeadingStyle(fontSize: bodySize * 1.15, weight: .semibold,
                         topSpacing: 10, bottomSpacing: 4, color: AgentBuddyTheme.textPrimary),
        h3: HeadingStyle(fontSize: bodySize * 1.08, weight: .semibold,
                         topSpacing: 8, bottomSpacing: 4, color: AgentBuddyTheme.textPrimary),
        h4: HeadingStyle(fontSize: bodySize, weight: .semibold, color: AgentBuddyTheme.textPrimary),
        h5: HeadingStyle(fontSize: bodySize, weight: .semibold, color: AgentBuddyTheme.textPrimary),
        h6: HeadingStyle(fontSize: bodySize, weight: .semibold, color: AgentBuddyTheme.textPrimary)
    )

    theme.inlineCode = InlineCodeStyle(
        backgroundColor: AgentBuddyTheme.surfaceSoft,
        textColor: AgentBuddyTheme.textPrimary,
        font: AgentBuddyFont.monospaced(size: codeSize),
        fontSize: codeSize
    )

    theme.codeBlock = CodeBlockStyle(
        backgroundColor: TimelineCodeStyle.fill(nested: false),
        textColor: AgentBuddyTheme.textPrimary,
        font: AgentBuddyFont.monospaced(size: codeSize),
        fontSize: codeSize,
        cornerRadius: BuddyRadius.control,
        showLanguageLabel: false,
        showCopyButton: false
    )

    theme.blockquote = BlockquoteStyle(
        borderColor: AgentBuddyTheme.border,
        borderWidth: 3,
        textColor: AgentBuddyTheme.textSecondary,
        padding: EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 4)
    )

    theme.table = TableStyle(
        borderStyle: .solid(color: AgentBuddyTheme.border, width: 1),
        headerBackground: AgentBuddyTheme.surfaceSoft,
        headerFontWeight: .semibold,
        backgroundStyle: .alternatingRows(
            even: AgentBuddyTheme.surface,
            odd: .clear
        ),
        cornerRadius: BuddyRadius.control
    )

    theme.list = ListStyleConfiguration(
        bulletMarker: .bullet,
        itemSpacing: 3,
        tightItemSpacing: 3
    )

    theme.link = LinkStyle(color: AgentBuddyTheme.link, underline: false)

    theme.thematicBreak = ThematicBreakStyle(
        color: AgentBuddyTheme.border,
        verticalPadding: 8
    )

    return theme
}

// MARK: - Syntax Highlighting Theme Mapping

/// Shared highlighter instance — theme is switched at runtime via `setTheme(_:)`.
private let sharedHighlighter = HighlightrCodeSyntaxHighlighter(theme: "atom-one-dark")

/// Maps a AgentBuddy theme slug to the closest Highlightr theme name.
/// Direct matches are checked first, then known family prefixes, then light/dark fallback.
private let highlightrDirectMap: [String: String] = [
    "codex-dark": "atom-one-dark",
    "codex-light": "atom-one-light",
    "dark-plus-B1yOZ-Hy": "vs2015",
    "light-plus": "vs",
    "one-dark-pro-D": "atom-one-dark",
    "material-theme": "material",
    "material-theme-darker-D": "material-darker",
    "material-theme-lighter": "material-lighter",
    "material-theme-ocean": "ocean",
    "material-theme-palenight": "material-palenight",
    "catppuccin-mocha-Ry8aD-5u": "mocha",
    "catppuccin-latte-Bd1wq-gC": "one-light",
    "catppuccin-frappe": "atom-one-dark",
    "catppuccin-macchiato": "atom-one-dark",
    "tokyo-night": "tokyo-night-dark",
    "kanagawa-wave": "atom-one-dark",
    "kanagawa-dragon-VscOyZL-": "atom-one-dark",
    "kanagawa-lotus": "atom-one-light",
    "houston": "atom-one-dark",
    "poimandres": "panda-syntax-dark",
    "vitesse-black": "atom-one-dark",
    "vitesse-dark": "atom-one-dark",
    "vitesse-light": "atom-one-light",
    "linear-dark": "atom-one-dark",
    "linear-light": "atom-one-light",
    "sentry-dark": "atom-one-dark",
    "notion-dark-BTRKJ-yg": "atom-one-dark",
    "notion-light": "atom-one-light",
    "temple-dark": "atom-one-dark",
    "lobster-dark-dxSKfHK-": "atom-one-dark",
    "matrix-dark": "green-screen",
    "absolutely-dark": "atom-one-dark",
    "absolutely-light": "atom-one-light",
    "proof-light": "atom-one-light",
    "pierre-dark": "atom-one-dark",
    "pierre-light": "atom-one-light",
    "slack-dark": "atom-one-dark",
    "slack-ochin-CRg": "atom-one-light",
    "oscurange-C": "atom-one-dark",
    "ayu-dark": "atom-one-dark",
    "laserwave": "shades-of-purple",
    "vesper": "atom-one-dark",
    "min-dark-": "atom-one-dark",
    "min-light": "atom-one-light",
    "snazzy-light": "snazzy",
    "rose-pine-x": "rose-pine",
]

private let highlightrFamilyPrefixes = [
    "dracula", "monokai", "nord", "solarized-dark", "solarized-light",
    "night-owl", "one-light", "github-dark", "github-light",
    "gruvbox-dark-hard", "gruvbox-dark-medium", "gruvbox-dark-soft",
    "gruvbox-light-hard", "gruvbox-light-medium", "gruvbox-light-soft",
    "everforest-dark", "everforest-light",
    "rose-pine-dawn", "rose-pine-moon",
]

private func highlightrThemeName(for slug: String, type: ThemeDefinition.ThemeType) -> String {
    if let mapped = highlightrDirectMap[slug] { return mapped }

    for prefix in highlightrFamilyPrefixes {
        if slug.hasPrefix(prefix) {
            // Highlightr uses the same names for these (ros-pine vs rose-pine handled)
            let hlName = slug
                .replacingOccurrences(of: "github-dark-default", with: "github-dark")
                .replacingOccurrences(of: "github-dark-dimmed", with: "github-dark-dimmed")
                .replacingOccurrences(of: "github-dark-high-contrast", with: "github-dark")
                .replacingOccurrences(of: "github-light-default", with: "github")
                .replacingOccurrences(of: "github-light-high-contrast", with: "github")
                .replacingOccurrences(of: "everforest-dark", with: "atom-one-dark")
                .replacingOccurrences(of: "everforest-light", with: "atom-one-light")
                .replacingOccurrences(of: "rose-pine-dawn", with: "ros-pine-dawn")
                .replacingOccurrences(of: "rose-pine-moon", with: "ros-pine-moon")
            if hlName != slug { return hlName }
            return prefix
        }
    }

    // Fallback: generic dark/light
    return type == .dark ? "atom-one-dark" : "atom-one-light"
}

/// Returns the current Highlightr theme name based on the active AgentBuddy theme.
private func currentHighlightrTheme(for colorScheme: ColorScheme) -> String {
    let resolved = colorScheme == .dark ? ThemeStore.shared.dark : ThemeStore.shared.light
    return highlightrThemeName(for: resolved.slug, type: resolved.type)
}

/// Syncs the shared highlighter to match the current AgentBuddy theme.
private func syncHighlighterTheme(for colorScheme: ColorScheme) {
    let desired = currentHighlightrTheme(for: colorScheme)
    if sharedHighlighter.themeName != desired {
        sharedHighlighter.setTheme(desired)
    }
}

// MARK: - Auto-Scaling Markdown Modifiers

private struct ScaledContentMarkdownModifier: ViewModifier {
    @Environment(\.textScale) private var textScale
    let baseBodySize: CGFloat
    let baseCodeSize: CGFloat
    let selectionEnabled: Bool

    func body(content: Content) -> some View {
        let scaledBody = baseBodySize * textScale
        let scaledCode = baseCodeSize * textScale
        // Follow the app's resolved scheme (the one every theme colour uses),
        // not the SwiftUI environment. The environment can still carry the
        // system scheme on the first pass and flips during app-switcher
        // snapshots; Hairball's Equatable code blocks would keep colours
        // highlighted in either. Real appearance changes re-ID the root view.
        let _ = syncHighlighterTheme(for: ThemeStore.shared.colorScheme)
        let themed = content
            .markdownTheme(agentBuddyContentTheme(bodySize: scaledBody, codeSize: scaledCode))
            .lineSpacing(markdownLineSpacing(bodySize: scaledBody))
            .codeSyntaxHighlighter(sharedHighlighter)
            .codeBlockRenderer(AgentBuddyCodeBlockRenderer())
        if selectionEnabled {
            themed.textSelection(.enabled)
        } else {
            themed
        }
    }
}

private struct ScaledSystemMarkdownModifier: ViewModifier {
    @Environment(\.textScale) private var textScale
    let baseBodySize: CGFloat
    let baseCodeSize: CGFloat
    let selectionEnabled: Bool

    func body(content: Content) -> some View {
        let scaledBody = baseBodySize * textScale
        let scaledCode = baseCodeSize * textScale
        // Follow the app's resolved scheme (the one every theme colour uses),
        // not the SwiftUI environment. The environment can still carry the
        // system scheme on the first pass and flips during app-switcher
        // snapshots; Hairball's Equatable code blocks would keep colours
        // highlighted in either. Real appearance changes re-ID the root view.
        let _ = syncHighlighterTheme(for: ThemeStore.shared.colorScheme)
        let themed = content
            .markdownTheme(agentBuddySystemTheme(bodySize: scaledBody, codeSize: scaledCode))
            .lineSpacing(markdownLineSpacing(bodySize: scaledBody))
            .codeSyntaxHighlighter(sharedHighlighter)
            .codeBlockRenderer(AgentBuddyCodeBlockRenderer())
        if selectionEnabled {
            themed.textSelection(.enabled)
        } else {
            themed
        }
    }
}

extension View {
    func agentBuddyContentMarkdown(
        bodySize: CGFloat = AgentBuddyFont.mintBodyPointSize,
        codeSize: CGFloat = AgentBuddyFont.mintCodePointSize,
        selectionEnabled: Bool = true
    ) -> some View {
        modifier(
            ScaledContentMarkdownModifier(
                baseBodySize: bodySize,
                baseCodeSize: codeSize,
                selectionEnabled: selectionEnabled
            )
        )
    }

    func agentBuddySystemMarkdown(
        bodySize: CGFloat = AgentBuddyFont.mintBodyPointSize,
        codeSize: CGFloat = AgentBuddyFont.mintCodePointSize,
        selectionEnabled: Bool = true
    ) -> some View {
        modifier(
            ScaledSystemMarkdownModifier(
                baseBodySize: bodySize,
                baseCodeSize: codeSize,
                selectionEnabled: selectionEnabled
            )
        )
    }
}
