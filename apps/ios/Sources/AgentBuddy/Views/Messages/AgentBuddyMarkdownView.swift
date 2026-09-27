import SwiftUI
import Hairball
import HairballUI

// MARK: - Reusable bubble components

enum AgentBuddyMarkdownStyleVariant {
    case content
    case system
}

struct AgentBuddyMarkdownView: View {
    let markdown: String
    var style: AgentBuddyMarkdownStyleVariant = .content
    var bodySize: CGFloat = AgentBuddyFont.mintBodyPointSize
    var codeSize: CGFloat = AgentBuddyFont.mintCodePointSize
    var selectionEnabled = true

    @State private var debugSettings = DebugSettings.shared

    var body: some View {
        if debugSettings.enabled && debugSettings.disableMarkdown {
            Text(markdown)
                .font(.system(size: bodySize, design: .monospaced))
                .foregroundStyle(style == .system ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.textPrimary)
                .textSelection(.enabled)
        } else {
            renderedMarkdown(selectionEnabled: selectionEnabled)
        }
    }

    @ViewBuilder
    private func renderedMarkdown(selectionEnabled: Bool) -> some View {
        let view = MarkdownView(markdown, processors: [LatexTransformer()])
        switch style {
        case .content:
            view.agentBuddyContentMarkdown(
                bodySize: bodySize, codeSize: codeSize,
                selectionEnabled: selectionEnabled
            )
        case .system:
            view.agentBuddySystemMarkdown(
                bodySize: bodySize, codeSize: codeSize,
                selectionEnabled: selectionEnabled
            )
        }
    }
}

struct InlineSelectableMarkdownMessage<Content: View>: View {
    let markdown: String
    var style: AgentBuddyMarkdownStyleVariant = .content
    var bodySize: CGFloat = AgentBuddyFont.mintBodyPointSize
    var codeSize: CGFloat = AgentBuddyFont.mintCodePointSize
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
    }
}

private extension AgentBuddyMarkdownStyleVariant {
    var cacheKey: String {
        switch self {
        case .content:
            return "content"
        case .system:
            return "system"
        }
    }
}
