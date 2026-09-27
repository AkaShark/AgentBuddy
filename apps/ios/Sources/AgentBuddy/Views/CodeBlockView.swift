import SwiftUI

/// Monospaced code block. Only the code area scrolls sideways; surrounding
/// text keeps wrapping.
struct CodeBlockView: View {
    let language: String
    let code: String
    var fontSize: CGFloat = BuddyTextStyle.code.size
    /// True when the block sits inside a surface card (tool call details), so
    /// it uses `surfaceSoft` instead of the page-level code fill.
    var nested: Bool = false

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            if isDiffLanguage(language) {
                SyntaxHighlightedDiffText(
                    diff: code,
                    titleHint: language.isEmpty ? nil : language,
                    fontSize: BuddyTextStyle.code.size
                )
                .padding(BuddySpacing.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text(code)
                    .agentBuddyMonoFont(size: fontSize)
                    .lineSpacing(fontSize * (BuddyTextStyle.code.lineHeight / BuddyTextStyle.code.size - 1.2))
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .textSelection(.enabled)
                    .padding(BuddySpacing.sm)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .timelineCodeSurface(nested: nested)
    }
}

#if DEBUG
#Preview("Code Block") {
    ZStack {
        AgentBuddyTheme.background.ignoresSafeArea()
        CodeBlockView(
            language: "swift",
            code: """
            struct SchedulerGate {
                let repoJobs = 100_000

                func canEnqueue(_ pending: Int) -> Bool {
                    pending < repoJobs
                }
            }
            """
        )
        .padding(20)
    }
}
#endif
