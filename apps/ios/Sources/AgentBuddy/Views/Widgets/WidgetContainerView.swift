import SwiftUI

// MARK: - Container View

struct WidgetContainerView: View {
    let widget: WidgetState
    var originThreadId: String?
    var onMessage: ((Any) -> Void)?
    private let minimumInlineHeight: CGFloat = 200

    @State private var contentHeight: CGFloat
    @State private var isFullscreen = false

    init(widget: WidgetState, originThreadId: String? = nil, onMessage: ((Any) -> Void)? = nil) {
        self.widget = widget
        self.originThreadId = originThreadId
        self.onMessage = onMessage
        _contentHeight = State(initialValue: max(widget.height, 200))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            widgetView
                .frame(height: max(contentHeight, minimumInlineHeight))
            HStack(spacing: 8) {
                if !widget.isFinalized {
                    ProgressView()
                        .scaleEffect(0.6)
                        .tint(AgentBuddyTheme.accentStrong)
                }
                Spacer()
                if widget.isFinalized, originThreadId != nil, let slug = widget.appId, !slug.isEmpty {
                    savedAsChip(slug: slug)
                }
                Button {
                    isFullscreen = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .agentBuddyFont(size: 11, weight: .medium)
                        Text("Expand")
                            .agentBuddyFont(size: 12, weight: .medium)
                    }
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(AgentBuddyTheme.surfaceLight.opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                }
            }
            .padding(.top, 6)
        }
        .fullScreenCover(isPresented: $isFullscreen) {
            fullscreenWidget
        }
    }

    private var widgetView: some View {
        WidgetWebView(
            widgetHTML: widget.widgetHTML,
            isFinalized: widget.isFinalized,
            onMessage: onMessage,
            heightBinding: $contentHeight
        )
    }

    private var fullscreenWidget: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            WidgetWebView(
                widgetHTML: widget.widgetHTML,
                isFinalized: true,
                allowsScrollAndZoom: true,
                onMessage: onMessage
            )
            .ignoresSafeArea()

            Button {
                isFullscreen = false
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .agentBuddyFont(size: 28)
                    .foregroundColor(.white.opacity(0.7))
                    .padding(16)
            }
        }
    }

    /// Compact pill shown on finalized widgets carrying a non-empty `appId`.
    /// Tap routes to the matching saved app's detail view.
    private func savedAsChip(slug: String) -> some View {
        Button {
            if let saved = SavedAppsStore.shared.app(slug: slug, threadId: originThreadId) {
                SavedAppsNavigation.shared.requestOpen(appId: saved.id)
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "square.grid.2x2.fill")
                    .agentBuddyFont(size: 10, weight: .medium)
                Text("Saved as")
                    .agentBuddyFont(size: 11, weight: .medium)
                Text(slug)
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
            }
            .foregroundColor(AgentBuddyTheme.accent)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(AgentBuddyTheme.surfaceLight.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: 6))
        }
    }
}
