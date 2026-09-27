import XCTest
@testable import AgentBuddy

/// Covers the Mint semantic roles added to `ResolvedTheme`: explicit
/// `agentbuddy.*` keys win, and themes without them keep their old values.
final class ResolvedThemeSemanticTests: XCTestCase {
    func testExplicitSemanticKeysResolveVerbatim() {
        let theme = ResolvedTheme(slug: "mint", definition: ThemeDefinition(name: "Mint", type: .light, colors: [
            "editor.background": "#F5F6F1",
            "editor.foreground": "#172B20",
            "button.background": "#172B20",
            "agentbuddy.brand": "#C3E7B5",
            "agentbuddy.onBrand": "#23442C",
            "agentbuddy.onAction": "#F5F6F1",
            "agentbuddy.error": "#A63832",
            "agentbuddy.errorSurface": "#FBE9E6",
            "agentbuddy.warning": "#785B22",
            "agentbuddy.warningSurface": "#F3EAD3",
            "agentbuddy.success": "#32683E",
            "agentbuddy.successSurface": "#E5F0DF",
            "agentbuddy.borderControl": "#7A8D7D",
            "agentbuddy.focus": "#476F40",
            "agentbuddy.disabled": "#E1E6DE",
            "agentbuddy.onDisabled": "#68766A",
            "agentbuddy.textBody": "#172B20",
        ]))

        XCTAssertEqual(theme.brand, "#C3E7B5")
        XCTAssertEqual(theme.onBrand, "#23442C")
        XCTAssertEqual(theme.textOnAccent, "#F5F6F1")
        XCTAssertEqual(theme.danger, "#A63832")
        XCTAssertEqual(theme.dangerSurface, "#FBE9E6")
        XCTAssertEqual(theme.warning, "#785B22")
        XCTAssertEqual(theme.warningSurface, "#F3EAD3")
        XCTAssertEqual(theme.success, "#32683E")
        XCTAssertEqual(theme.successSurface, "#E5F0DF")
        XCTAssertEqual(theme.borderControl, "#7A8D7D")
        XCTAssertEqual(theme.focus, "#476F40")
        XCTAssertEqual(theme.disabled, "#E1E6DE")
        XCTAssertEqual(theme.onDisabled, "#68766A")
        XCTAssertEqual(theme.textBody, "#172B20")
    }

    func testThemesWithoutSemanticKeysKeepLegacyValues() {
        let light = ResolvedTheme(slug: "legacy-light", definition: ThemeDefinition(name: "Legacy", type: .light, colors: [
            "editor.background": "#FFFFFF",
            "editor.foreground": "#0D0D0D",
            "button.background": "#0169CC",
        ]))
        XCTAssertEqual(light.danger, "#D32F2F")
        XCTAssertEqual(light.success, "#2E7D32")
        XCTAssertEqual(light.warning, "#E65100")
        XCTAssertEqual(light.textOnAccent, "#FFFFFF")
        XCTAssertEqual(light.codeBackground, "#FFFFFF")
        XCTAssertEqual(light.textBody, ResolvedTheme.dimColor("#0D0D0D", factor: 0.88))
        XCTAssertEqual(light.focus, light.accentStrong)
        XCTAssertEqual(light.disabled, light.surfaceLight)

        let dark = ResolvedTheme(slug: "legacy-dark", definition: ThemeDefinition(name: "Legacy", type: .dark, colors: [
            "editor.background": "#111111",
            "editor.foreground": "#FCFCFC",
        ]))
        XCTAssertEqual(dark.danger, "#FF5555")
        XCTAssertEqual(dark.success, "#6EA676")
        XCTAssertEqual(dark.warning, "#E2A644")
    }

    func testBlendCompositesOverOpaqueBackground() {
        XCTAssertEqual(ResolvedTheme.blend("#000000", over: "#FFFFFF", alpha: 0), "#FFFFFF")
        XCTAssertEqual(ResolvedTheme.blend("#000000", over: "#FFFFFF", alpha: 1), "#000000")
        XCTAssertEqual(ResolvedTheme.blend("#FF0000", over: "#0000FF", alpha: 0.5), "#7F007F")
    }

    @MainActor
    func testBundledMintThemesDeclareEverySemanticRole() throws {
        let bundle = Bundle(for: ThemeManager.self)
        for slug in [ThemeManager.defaultLightSlug, ThemeManager.defaultDarkSlug] {
            let url = try XCTUnwrap(
                bundle.url(forResource: slug, withExtension: "json") ?? Bundle.main.url(forResource: slug, withExtension: "json"),
                "missing \(slug).json"
            )
            let definition = try JSONDecoder().decode(ThemeDefinition.self, from: Data(contentsOf: url))
            for key in ["brand", "onBrand", "onAction", "borderControl", "focus", "success", "successSurface",
                        "warning", "warningSurface", "error", "errorSurface", "disabled", "onDisabled"] {
                XCTAssertNotNil(definition.colors["agentbuddy.\(key)"], "\(slug) is missing agentbuddy.\(key)")
            }
        }
    }
}
