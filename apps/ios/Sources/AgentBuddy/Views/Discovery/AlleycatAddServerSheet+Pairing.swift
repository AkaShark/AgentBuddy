import AVFoundation
import SwiftUI
import UIKit

extension AlleycatAddServerSheet {
    func requestInitialScanIfNeeded() {
        guard !AgentBuddyPlatform.rendersAsMacApp else { return }
        guard startScanningOnAppear, !didRequestInitialScan, parsedParams == nil else { return }
        didRequestInitialScan = true
        Task { @MainActor in
            await Task.yield()
            requestCameraAndScan()
        }
    }

    var pairingSection: some View {
        DiscoveryFormSection("Pairing") {
            // Mac (iOS-on-Mac) shows paste-JSON only; iOS shows
            // QR scanning first, with paste available as a production fallback
            // for users who already copied the pairing payload.
            if AgentBuddyPlatform.rendersAsMacApp {
                pasteJSONPairingControls
            } else {
                qrPairingControls
            }
        }
    }

    @ViewBuilder
    private var pasteJSONPairingControls: some View {
        Text("Run \(Self.pairCommandLabel) on the host you want to connect to, then paste the JSON it prints below.")
            .buddyText(.body)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .fixedSize(horizontal: false, vertical: true)

        pasteJSONEntryControls(minHeight: 110)
    }

    @ViewBuilder
    private var qrPairingControls: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            HStack(alignment: .top, spacing: BuddySpacing.md) {
                BuddyIconTile(
                    content: .symbol("qrcode.viewfinder"),
                    fill: AgentBuddyTheme.onBrand.opacity(0.1),
                    foreground: AgentBuddyTheme.onBrand
                )
                Text("The pairing code comes from AgentBuddy on your computer. Open it there, then scan the code with this phone.")
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.onBrand)
                    .fixedSize(horizontal: false, vertical: true)
            }
            BuddyButton(
                parsedParams == nil ? "Scan Pairing QR" : "Rescan QR",
                systemImage: "qrcode.viewfinder",
                kind: parsedParams == nil ? .primary : .secondary
            ) {
                requestCameraAndScan()
            }
        }
        .buddyCard(.brand, radius: BuddyRadius.card, padding: BuddySpacing.lg)

        DisclosureGroup(
            isExpanded: $showPaste,
            content: {
                pasteJSONEntryControls(minHeight: 90)
                    .padding(.top, BuddySpacing.xs)
            },
            label: {
                Text("Paste Pairing JSON")
                    .buddyText(.label)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .frame(minHeight: BuddySize.minHitTarget)
            }
        )
        .tint(AgentBuddyTheme.textSecondary)
        .padding(.top, BuddySpacing.xxs)
    }

    @ViewBuilder
    private func pasteJSONEntryControls(minHeight: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            DiscoveryPasteEditor(
                text: $pasteJSON,
                placeholder: #"{"v":1,"node_id":"...","token":"...","relay":"https://..."}"#,
                minHeight: minHeight
            )

            HStack(spacing: BuddySpacing.sm) {
                BuddyButton("Paste from Clipboard", systemImage: "doc.on.clipboard", kind: .soft) {
                    if let clipboard = UIPasteboard.general.string {
                        pasteJSON = clipboard
                    }
                }

                BuddyButton(
                    parsedParams == nil ? "Parse JSON" : "Reparse JSON",
                    kind: .secondary
                ) {
                    handleScannedPayload(pasteJSON)
                }
                .disabled(pasteJSON.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
    }

    private static let pairCommandLabel = "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr"

    func handleScannedPayload(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        do {
            let params = try alleycat.parsePairPayload(json: trimmed)
            parsedParams = params
            displayName = suggestedDisplayName(for: params)
            parseError = nil
            connectError = nil
            agentError = nil
            agents = []
            selectedAgentNames = []
            loadAgents(params: params)
        } catch {
            parsedParams = nil
            agents = []
            selectedAgentNames = []
            parseError = error.localizedDescription
        }
    }

    private func requestCameraAndScan() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            showScanner = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                Task { @MainActor in
                    if granted {
                        showScanner = true
                    } else {
                        cameraDenied = true
                    }
                }
            }
        case .denied, .restricted:
            cameraDenied = true
        @unknown default:
            cameraDenied = true
        }
    }

    func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
