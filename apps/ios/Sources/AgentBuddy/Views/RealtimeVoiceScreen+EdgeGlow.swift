import SwiftUI

struct GlowPalette: Equatable {
    let background: String
    let accent: String
    let accentStrong: String
    let warning: String
    let success: String
    let danger: String

    static func from(colorScheme: ColorScheme) -> GlowPalette {
        let theme = colorScheme == .dark ? ThemeStore.shared.dark : ThemeStore.shared.light
        return GlowPalette(
            background: theme.background,
            accent: theme.accent,
            accentStrong: theme.accentStrong,
            warning: theme.warning,
            success: theme.success,
            danger: theme.danger
        )
    }
}

struct SiriEdgeGlow: View {
    let intensity: CGFloat
    let phase: VoiceSessionPhase
    let palette: GlowPalette

    var body: some View {
        TimelineView(.animation(minimumInterval: 0.5)) { timeline in
            let _ = timeline.date
            GeometryReader { geometry in
                let cornerRadius: CGFloat = UIScreen.main.displayCornerRadius
                let rect = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                let gradient = makeAngularGradient(for: phase)

                ZStack {
                    rect
                        .strokeBorder(gradient, lineWidth: 4 + intensity * 3)

                    rect
                        .strokeBorder(gradient, lineWidth: 6 + intensity * 4)
                        .blur(radius: 4)

                    rect
                        .strokeBorder(gradient, lineWidth: 8 + intensity * 6)
                        .blur(radius: 12)

                    rect
                        .strokeBorder(gradient, lineWidth: 12 + intensity * 8)
                        .blur(radius: 20)
                        .opacity(0.7)
                }
                .opacity(Double(intensity))
                .frame(width: geometry.size.width, height: geometry.size.height)
            }
        }
        .animation(.easeInOut(duration: 0.6), value: phase)
    }

    private func makeAngularGradient(for phase: VoiceSessionPhase) -> AngularGradient {
        let colors = phaseColors(for: phase)
        var positions = (0..<colors.count).map { index in
            let base = Double(index) / Double(colors.count)
            return base + Double.random(in: -0.08...0.08)
        }.sorted()
        positions = positions.map { min(1, max(0, $0)) }

        let stops = zip(colors, positions).map { color, position in
            Gradient.Stop(color: color, location: position)
        }
        return AngularGradient(gradient: Gradient(stops: stops), center: .center)
    }

    private func phaseColors(for phase: VoiceSessionPhase) -> [Color] {
        let accent = Color(hex: palette.accent)
        let accentStrong = Color(hex: palette.accentStrong)
        let warning = Color(hex: palette.warning)
        let success = Color(hex: palette.success)
        let danger = Color(hex: palette.danger)

        switch phase {
        case .listening:
            return [
                accentStrong,
                accentStrong.opacity(0.7),
                accent,
                success,
                accentStrong.opacity(0.5),
                accent.opacity(0.8),
            ]
        case .speaking:
            return [
                warning,
                warning.opacity(0.7),
                warning.opacity(0.9),
                warning.opacity(0.5),
                warning.opacity(0.8),
                warning.opacity(0.6),
            ]
        case .thinking, .handoff:
            return [
                warning.opacity(0.6),
                accent.opacity(0.4),
                warning.opacity(0.4),
                accentStrong.opacity(0.3),
                warning.opacity(0.5),
                accent.opacity(0.3),
            ]
        case .connecting:
            return [
                accent.opacity(0.4),
                accentStrong.opacity(0.3),
                accent.opacity(0.2),
                Color.gray.opacity(0.2),
                accent.opacity(0.3),
                accentStrong.opacity(0.2),
            ]
        case .error:
            return [
                danger,
                danger.opacity(0.6),
                danger.opacity(0.5),
                danger.opacity(0.4),
                danger.opacity(0.3),
                danger.opacity(0.5),
            ]
        }
    }
}

private extension UIScreen {
    var displayCornerRadius: CGFloat {
        let key = "_displayCornerRadius"
        guard let value = self.value(forKey: key) as? CGFloat, value > 0 else {
            return 50
        }
        return value
    }
}
