import SwiftUI
import Hairball
import HairballUI

extension View {
    @ViewBuilder
    func applyStreamingEffect(_ effect: (any StreamingTextEffect)?) -> some View {
        if let effect {
            self.streamingTextEffect(effect)
        } else {
            self
        }
    }
}

struct StreamingAssistantBubble: View {
    @Environment(WallpaperManager.self) private var wallpaperManager
    @Environment(\.activeThreadKey) private var threadKey
    let itemId: String
    let text: String
    var isStreaming: Bool = false
    var label: String? = nil
    var themeVersion: Int = 0
    var onSnapshotRendered: (() -> Void)? = nil
    private let contentFontSize: CGFloat
    private let codeFontSize = AgentBuddyFont.mintCodePointSize

    /// Renderer is resolved once during init. For streaming items, this
    /// creates the renderer eagerly (before deltas arrive) so the `if let`
    /// branch is taken on the very first body evaluation. The coordinator
    /// returns the same renderer when deltas later call `appendDelta`.
    private let resolvedRenderer: StreamingMarkdownRenderer?

    init(
        itemId: String,
        text: String,
        isStreaming: Bool = false,
        label: String? = nil,
        themeVersion: Int = 0,
        bodySize: CGFloat = AgentBuddyFont.mintBodyPointSize,
        onSnapshotRendered: (() -> Void)? = nil
    ) {
        self.itemId = itemId
        self.text = text
        self.isStreaming = isStreaming
        self.label = label
        self.themeVersion = themeVersion
        self.contentFontSize = bodySize
        self.onSnapshotRendered = onSnapshotRendered

        let coord = StreamingRendererCoordinator.shared
        if isStreaming {
            self.resolvedRenderer = coord.renderer(for: itemId, currentText: text)
        } else {
            self.resolvedRenderer = nil
        }
    }

    private var typingConfig: TypingEffectConfig {
        wallpaperManager.resolveTypingEffect(for: threadKey)
    }

    var body: some View {
        Group {
            if shouldUseSegmentedRenderer {
                AssistantBlocksBubble(
                    segments: segmentedRenderSegments,
                    label: label
                )
            } else {
                streamingMarkdownBody
            }
        }
        .onChange(of: text) {
            onSnapshotRendered?()
        }
    }

    private var shouldUseSegmentedRenderer: Bool {
        !isStreaming || MessageContentBridge.containsMath(text)
    }

    private var segmentedRenderSegments: [MessageRenderCache.AssistantSegment] {
        StreamingAssistantRenderCache.shared.segments(itemId: itemId, text: text)
    }

    private var streamingMarkdownBody: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                if let label {
                    Text(label)
                        .buddyText(.caption, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                if let resolvedRenderer {
                    StreamingMarkdownContentView(renderer: resolvedRenderer)
                        .tokenReveal(TokenRevealConfig(duration: max(typingConfig.revealDuration, 0.01), mode: typingConfig.effectiveRevealMode))
                        .applyStreamingEffect(typingConfig.resolvedEffect)
                        .revealGranularity(typingConfig.effectiveGranularity)
                        .agentBuddyContentMarkdown(
                            bodySize: contentFontSize,
                            codeSize: codeFontSize,
                            selectionEnabled: !isStreaming
                        )
                        .transaction { $0.animation = nil }
                } else {
                    AgentBuddyMarkdownView(
                        markdown: text,
                        style: .content,
                        bodySize: contentFontSize,
                        codeSize: codeFontSize
                    )
                    .fixedSize(horizontal: false, vertical: true)
                    .tokenReveal(.disabled)
                    .transaction { $0.animation = nil }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer(minLength: 20)
        }
    }
}
