import SwiftUI
import Hairball
import HairballUI

// MARK: - Streaming Effect Preview

struct StreamingEffectPreview: View {
    var config: TypingEffectConfig
    @State private var renderer: StreamingMarkdownRenderer
    @State private var feedTask: Task<Void, Never>?

    private static let sampleText = """
    Found the issue — the `SessionManager` was **dropping the refresh token** on every cold start because `loadCredentials()` ran before the keychain unlock callback.

    I moved the credential load into the `didBecomeActive` handler and added a retry with exponential backoff:

    ```swift
    func restoreSession() async throws {
        let creds = try await keychain.load(.session)
        try await client.resume(with: creds)
    }
    ```

    This also fixes the **"phantom logout"** bug users reported on iOS 18. The token was valid but got discarded before the refresh exchange could complete.
    """

    init(config: TypingEffectConfig) {
        self.config = config
        self._renderer = State(initialValue: StreamingMarkdownRenderer(
            processors: [LatexTransformer()],
            throttleInterval: 0.016
        ))
    }

    var body: some View {
        StreamingMarkdownContentView(renderer: renderer)
            .tokenReveal(TokenRevealConfig(
                duration: max(config.revealDuration, 0.01),
                mode: config.effectiveRevealMode
            ))
            .applyStreamingEffect(config.resolvedEffect)
            .revealGranularity(config.effectiveGranularity)
            .agentBuddyContentMarkdown(
                bodySize: 14,
                codeSize: 14,
                selectionEnabled: false
            )
            .onAppear { startFeed() }
            .onDisappear { feedTask?.cancel() }
    }

    private func startFeed() {
        let text = Self.sampleText
        feedTask = Task {
            while !Task.isCancelled {
                renderer.reset()
                // Simulate realistic token arrival: 3-8 chars per chunk
                // with variable inter-token delays.
                var index = text.startIndex
                while index < text.endIndex && !Task.isCancelled {
                    let chunkSize = Int.random(in: 3...8)
                    let batchEnd = text.index(index, offsetBy: chunkSize, limitedBy: text.endIndex) ?? text.endIndex
                    let chunk = String(text[index..<batchEnd])
                    await MainActor.run { renderer.append(chunk) }
                    index = batchEnd
                    let delay = Int.random(in: 20...60)
                    try? await Task.sleep(for: .milliseconds(delay))
                }
                await MainActor.run { renderer.finish() }
                try? await Task.sleep(for: .seconds(1.0))
            }
        }
    }
}
