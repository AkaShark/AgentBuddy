import Foundation

extension WatchProjection {
    // MARK: - Theme projection

    /// Build a resolved palette the watch can apply directly. Honors
    /// `ThemeManager.appearanceMode` plus `ThemeStore.colorScheme` (which
    /// already resolves `.system` against the live trait collection).
    @MainActor
    static func theme(from manager: ThemeManager) -> WatchThemePayload {
        let mode: WatchThemePayload.AppearanceMode = {
            switch manager.appearanceMode {
            case .system: return .system
            case .light:  return .light
            case .dark:   return .dark
            }
        }()
        let cs = ThemeStore.shared.colorScheme
        let t = cs == .dark ? manager.darkTheme : manager.lightTheme
        let bottom = ResolvedTheme.adjustBrightness(
            t.background,
            by: cs == .dark ? -0.02 : 0.01
        )
        return WatchThemePayload(
            appearanceMode: mode,
            isDark: cs == .dark,
            accent: t.accent,
            accentStrong: t.accentStrong,
            textPrimary: t.textPrimary,
            textSecondary: t.textSecondary,
            textMuted: t.textMuted,
            surface: t.surface,
            surfaceLight: t.surfaceLight,
            border: t.border,
            danger: t.danger,
            success: t.success,
            warning: t.warning,
            textOnAccent: t.textOnAccent,
            backgroundTop: t.background,
            backgroundBottom: bottom
        )
    }
}
