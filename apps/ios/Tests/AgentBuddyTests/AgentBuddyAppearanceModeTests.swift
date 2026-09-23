import SwiftUI
import UIKit
import XCTest
@testable import AgentBuddy

final class AgentBuddyAppearanceModeTests: XCTestCase {
    func testPreferredColorSchemeMapping() {
        XCTAssertNil(AgentBuddyAppearanceMode.system.preferredColorScheme)
        XCTAssertEqual(AgentBuddyAppearanceMode.light.preferredColorScheme, .light)
        XCTAssertEqual(AgentBuddyAppearanceMode.dark.preferredColorScheme, .dark)
    }

    func testResolvedColorSchemeUsesSystemOnlyForSystemMode() {
        XCTAssertEqual(AgentBuddyAppearanceMode.system.resolvedColorScheme(systemColorScheme: .light), .light)
        XCTAssertEqual(AgentBuddyAppearanceMode.system.resolvedColorScheme(systemColorScheme: .dark), .dark)
        XCTAssertEqual(AgentBuddyAppearanceMode.light.resolvedColorScheme(systemColorScheme: .dark), .light)
        XCTAssertEqual(AgentBuddyAppearanceMode.dark.resolvedColorScheme(systemColorScheme: .light), .dark)
    }

    func testUserInterfaceStyleMapping() {
        XCTAssertEqual(AgentBuddyAppearanceMode.system.userInterfaceStyle, .unspecified)
        XCTAssertEqual(AgentBuddyAppearanceMode.light.userInterfaceStyle, .light)
        XCTAssertEqual(AgentBuddyAppearanceMode.dark.userInterfaceStyle, .dark)
    }
}
