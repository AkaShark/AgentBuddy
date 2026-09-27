import SwiftUI
import Hairball
import HairballUI
import UIKit

struct AgentBuddyCodeBlockRenderer: CodeBlockRenderer {
    @ViewBuilder
    func makeBody(configuration: CodeBlockConfiguration) -> some View {
        if isDiffLanguage(configuration.language) {
            VStack(alignment: .leading, spacing: 0) {
                if configuration.hasLanguage {
                    HStack {
                        Text(configuration.languageDisplayName)
                            .buddyText(.caption, weight: .medium)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                        Spacer()
                    }
                    .padding(.horizontal, BuddySpacing.sm)
                    .padding(.top, BuddySpacing.xs)
                    .padding(.bottom, BuddySpacing.xxs)
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    SyntaxHighlightedDiffText(
                        diff: configuration.code,
                        titleHint: configuration.language,
                        fontSize: BuddyTextStyle.code.size
                    )
                    .padding(configuration.theme.codeBlock.padding)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .background(configuration.theme.codeBlock.backgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: configuration.theme.codeBlock.cornerRadius, style: .continuous))
            .modifier(CodeBlockTerminalContextMenu(code: configuration.code))
        } else {
            DefaultCodeBlockRenderer().makeBody(configuration: configuration)
                .clipShape(RoundedRectangle(cornerRadius: configuration.theme.codeBlock.cornerRadius, style: .continuous))
                .modifier(CodeBlockTerminalContextMenu(code: configuration.code))
        }
    }
}

/// Adds a "Run in terminal" + "Copy" context menu to a chat code block.
private struct CodeBlockTerminalContextMenu: ViewModifier {
    let code: String

    func body(content: Content) -> some View {
        content.contextMenu {
            Button {
                UIPasteboard.general.string = code
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
            }
            if AppModel.shared.store.activeTerminalId() != nil {
                Button {
                    let bytes = Data(code.utf8)
                    Task {
                        _ = try? await AppModel.shared.store.writeToActiveTerminal(bytes: bytes)
                    }
                } label: {
                    Label("Run in Terminal", systemImage: "terminal")
                }
            }
        }
    }
}
