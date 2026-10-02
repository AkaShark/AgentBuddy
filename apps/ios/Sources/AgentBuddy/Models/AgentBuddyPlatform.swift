import Foundation
import SwiftUI
import UIKit

enum AgentBuddyPlatform {
    /// UIKit may still run the iPad app on Apple Silicon Macs.
    /// The native macOS app is the separate Tauri app in apps/desktop.
    static let rendersAsMacApp = ProcessInfo.processInfo.isiOSAppOnMac
    static let supportsLocalRuntime = true
    static let supportsVoiceRuntime = true

    private enum LocalRuntimeBootstrapState {
        case idle
        case starting
        case ready
    }

    private nonisolated(unsafe) static var bootstrapState: LocalRuntimeBootstrapState = .idle
    private static let bootstrapLock = NSLock()

    static func bootstrapLocalRuntimeIfNeeded() {
        // [baozi-fork] The embedded iSH/Alpine Linux emulator aborts at boot on
        // this build/signing config ("invalid vdso" -> SIGABRT) and takes the whole
        // app down before the UI renders. The Linux shell agent is not needed for a
        // Claude Code client, so skip booting it and let the UI come up.
        let ishDisabledForFork = true
        if ishDisabledForFork {
            NSLog("[ish] local runtime bootstrap intentionally disabled (baozi-fork)")
            return
        }
        guard beginLocalRuntimeBootstrap() else { return }

        migrateWorkDirIfHostPath()
        let fm = FileManager.default
        guard let bundleFs = Bundle.main.url(forResource: "fs", withExtension: nil) else {
            NSLog("[ish] bundled fs not found")
            finishLocalRuntimeBootstrap(.idle)
            return
        }
        let appSupport = try? fm.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let docs = try? fm.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        guard let appSupport, let docs else {
            NSLog("[ish] could not resolve sandbox dirs")
            finishLocalRuntimeBootstrap(.idle)
            return
        }
        let bundlePath = bundleFs.path
        let appSupportPath = appSupport.path
        let docsPath = docs.path
        // First-launch rootfs extraction can take 10-30s. Bootstrapping
        // synchronously on the main actor froze the UI and made the
        // Terminal route race the kernel boot. Run it on a background
        // queue and let `instance_or_wait` on the Rust side handle the
        // race so the UI stays responsive.
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                try ishBootstrap(
                    bundleFsPath: bundlePath,
                    applicationSupportDir: appSupportPath,
                    documentsDir: docsPath
                )
                finishLocalRuntimeBootstrap(.ready)
                Task { @MainActor in
                    await UserMountStore.shared.loadAndRemountAll()
                }
            } catch {
                NSLog("[ish] bootstrap failed: \(error)")
                finishLocalRuntimeBootstrap(.idle)
            }
        }
    }

    private static func beginLocalRuntimeBootstrap() -> Bool {
        bootstrapLock.lock()
        defer { bootstrapLock.unlock() }
        switch bootstrapState {
        case .idle:
            bootstrapState = .starting
            return true
        case .starting, .ready:
            return false
        }
    }

    private static func finishLocalRuntimeBootstrap(_ state: LocalRuntimeBootstrapState) {
        bootstrapLock.lock()
        bootstrapState = state
        bootstrapLock.unlock()
    }

    /// iSH cannot see iOS sandbox paths. If the persisted `workDir` is one
    /// (carried over from an older build that ran shell commands directly in
    /// the iOS sandbox, or from the @AppStorage default), reset it to a
    /// fakefs-internal path so the model doesn't waste a cd-probe round-trip
    /// on every fresh turn.
    private static func migrateWorkDirIfHostPath() {
        let key = "workDir"
        let stored = UserDefaults.standard.string(forKey: key) ?? ""
        let hostPrefixes = ["/var/", "/private/", "/Users/", "/Library/", "/System/", "/Applications/"]
        let isHostPath = hostPrefixes.contains { stored.hasPrefix($0) }
        if stored.isEmpty || isHostPath {
            UserDefaults.standard.set("/root", forKey: key)
        }
    }

    static func defaultLocalWorkingDirectory() -> String {
        return ishDefaultCwd()
    }

    static func localRuntimeDisplayName() -> String {
        let device = UIDevice.current.name.trimmingCharacters(in: .whitespacesAndNewlines)
        return device.isEmpty ? "This Device" : device
    }


    static func isRegularSurface(horizontalSizeClass: UserInterfaceSizeClass?) -> Bool {
        horizontalSizeClass == .regular
    }
}
