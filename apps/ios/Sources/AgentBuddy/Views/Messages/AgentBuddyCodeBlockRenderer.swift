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
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                    .padding(.bottom, 4)
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    SyntaxHighlightedDiffText(
                        diff: configuration.code,
                        titleHint: configuration.language,
                        fontSize: AgentBuddyFont.conversationDiffPointSize
                    )
                    .padding(configuration.theme.codeBlock.padding)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .background(configuration.theme.codeBlock.backgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: configuration.theme.codeBlock.cornerRadius))
            .modifier(GlassRectModifier(cornerRadius: 8))
            .modifier(CodeBlockTerminalContextMenu(code: configuration.code))
        } else {
            DefaultCodeBlockRenderer().makeBody(configuration: configuration)
                .modifier(GlassRectModifier(cornerRadius: 8))
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
