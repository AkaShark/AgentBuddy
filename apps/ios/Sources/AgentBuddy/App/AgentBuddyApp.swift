import SwiftUI

@main
struct AgentBuddyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var appModel = AppModel.shared
    @State private var voiceRuntime = VoiceRuntimeController.shared
    @State private var appRuntime = AppRuntimeController.shared
    @State private var themeManager = ThemeManager.shared
    @State private var wallpaperManager = WallpaperManager.shared
    @State private var languageManager = LanguageManager.shared
    @Environment(\.scenePhase) private var scenePhase

    @SceneBuilder
    var body: some Scene {
        #if targetEnvironment(macCatalyst)
        mainWindowGroup
            .defaultSize(width: 1120, height: 760)
            // NOTE: `.windowResizability` is a no-op on Catalyst.
            // Actual resize bounds are set from
            // `MacWindowTitleBarStyler` via
            // `UIWindowScene.sizeRestrictions`.
            .commands {
                AgentBuddyCommands(appModel: appModel)
            }
        #else
        mainWindowGroup
        #endif
    }

    private var mainWindowGroup: some Scene {
        WindowGroup {
            ContentView()
                .environment(appModel)
                .environment(appRuntime)
                .environment(voiceRuntime)
                .environment(themeManager)
                .environment(wallpaperManager)
                .environment(\.locale, languageManager.locale)
                .task {
                    appModel.start()
                    voiceRuntime.bind(appModel: appModel)
                    appRuntime.bind(appModel: appModel, voiceRuntime: voiceRuntime)
                    appDelegate.appRuntime = appRuntime
                    appRuntime.appDidBecomeActive()
                    #if targetEnvironment(macCatalyst)
                    LocalCodexBootstrap.shared.startIfNeeded(appModel: appModel)
                    #endif
                    // Pair host (BLE advertiser, ultrasonic emitter,
                    // Bonjour publish, WS listener) and the iPhone client
                    // (BLE scanner, ultrasonic reader, NISession) are
                    // strictly opt-in: they only start when the user
                    // opens the Pair screen in Settings → Experimental,
                    // and stop on disappear. The screen itself is gated
                    // behind `#if DEBUG`, so neither stack is reachable
                    // in Release builds.
                }
        }
        .onChange(of: scenePhase) { _, newPhase in
            LLog.info("lifecycle", "scenePhase changed", fields: ["phase": newPhase.debugName])
            switch newPhase {
            case .background:
                appRuntime.appDidEnterBackground()
            case .inactive:
                appRuntime.appDidBecomeInactive()
            case .active:
                appRuntime.appDidBecomeActive()
            default:
                break
            }
        }
    }
}

private extension ScenePhase {
    var debugName: String {
        switch self {
        case .active:
            return "active"
        case .inactive:
            return "inactive"
        case .background:
            return "background"
        @unknown default:
            return "unknown"
        }
    }
}
