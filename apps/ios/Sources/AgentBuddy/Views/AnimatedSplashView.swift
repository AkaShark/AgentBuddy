import SwiftUI

/// The linked brackets connect the app identity to the agents below it.
/// Splash lifetime remains owned by AppDelegate's splash window.
struct AnimatedSplashView: View {
    let appReady: Bool
    var compact: Bool = false
    let onFinished: () -> Void

    var body: some View {
        ZStack {
            if !compact {
                AgentBuddyTheme.background.ignoresSafeArea()
            }

            VStack(spacing: BuddySpacing.xl) {
                BuddyBrandMark(size: BuddySize.splashMark)

                if !compact {
                    Text("AgentBuddy")
                        .buddyText(.display)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                }
            }
            .offset(y: compact ? 0 : -BuddySpacing.xl)

            if !compact {
                VStack {
                    Spacer()
                    SplashCarouselText()
                        .padding(.bottom, BuddySpacing.huge)
                }
            }
        }
    }
}

/// Bundled names are available before discovery populates agent metadata.
private struct SplashCarouselText: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric(relativeTo: .callout) private var rowHeight = BuddyTextStyle.code.lineHeight
    @ScaledMetric(relativeTo: .callout) private var nameWidth = BuddySize.splashAgentName

    private let providers = [
        "codex", "pi", "amp", "opencode", "claude", "droid", "hermes", "devin", "grok",
    ]

    var body: some View {
        HStack(spacing: 0) {
            if reduceMotion {
                Text(verbatim: providers[0])
                    .buddyText(.code)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .frame(width: nameWidth, height: rowHeight * 3, alignment: .trailing)
            } else {
                SpinningWordCarousel(providers: providers, rowHeight: rowHeight)
                    .frame(width: nameWidth, height: rowHeight * 3)
            }

            Text(" on your phone")
                .buddyText(.code)
                .foregroundStyle(AgentBuddyTheme.textMuted)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("AgentBuddy") + Text(" on your phone"))
    }
}

private struct SpinningWordCarousel: View {
    let providers: [String]
    let rowHeight: CGFloat
    @State private var startDate = Date.now

    private static let frameInterval: TimeInterval = 1.0 / 30.0
    private let perWord = 0.9
    private let transitionFraction = 0.45

    var body: some View {
        TimelineView(.periodic(from: startDate, by: Self.frameInterval)) { timeline in
            let phase = easedPhase(t: timeline.date.timeIntervalSince(startDate))
            let cycleHeight = rowHeight * CGFloat(providers.count)

            ZStack(alignment: .trailing) {
                ForEach(Array(providers.enumerated()), id: \.element) { index, provider in
                    let y = wrap((CGFloat(index) - CGFloat(phase)) * rowHeight, range: cycleHeight)
                    let distance = min(abs(y) / rowHeight, 1)
                    let isSelected = distance < 0.35

                    Text(verbatim: provider)
                        .buddyText(.code)
                        .foregroundStyle(isSelected ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.textMuted)
                        .opacity(isSelected ? 1 : max(0.18, 1 - Double(distance) * 0.7))
                        .offset(y: y)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
        }
        .clipped()
        .mask {
            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0),
                    .init(color: .black, location: 0.3),
                    .init(color: .black, location: 0.7),
                    .init(color: .clear, location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .onAppear { startDate = .now }
    }

    private func easedPhase(t: TimeInterval) -> Double {
        let cycleT = t.truncatingRemainder(dividingBy: perWord * Double(providers.count))
        let wordIndex = floor(cycleT / perWord)
        let withinWord = cycleT - wordIndex * perWord
        let dwell = perWord * (1 - transitionFraction)
        guard withinWord >= dwell else { return wordIndex }
        let progress = (withinWord - dwell) / (perWord * transitionFraction)
        return wordIndex + 0.5 - 0.5 * cos(progress * .pi)
    }

    private func wrap(_ y: CGFloat, range: CGFloat) -> CGFloat {
        var value = y.truncatingRemainder(dividingBy: range)
        if value > range / 2 { value -= range }
        if value < -range / 2 { value += range }
        return value
    }
}

#if DEBUG
#Preview("Linked agents splash") {
    AnimatedSplashView(appReady: true) {}
}
#endif
