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
        Section {
            // Mac (Catalyst + iOS-on-Mac) shows paste-JSON only; iOS shows
            // QR scanning first, with paste available as a production fallback
            // for users who already copied the pairing payload.
            if AgentBuddyPlatform.rendersAsMacApp {
                pasteJSONPairingControls
            } else {
                qrPairingControls
            }
        } header: {
            Text("Pairing")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    @ViewBuilder
    private var pasteJSONPairingControls: some View {
        Text("Run \(Self.pairCommandLabel) on the host you want to connect to, then paste the JSON it prints below.")
            .agentBuddyFont(.caption)
            .foregroundColor(AgentBuddyTheme.textSecondary)
            .fixedSize(horizontal: false, vertical: true)

        pasteJSONEntryControls(minHeight: 110)
    }

    @ViewBuilder
    private var qrPairingControls: some View {
        Button {
            requestCameraAndScan()
        } label: {
            HStack {
                Image(systemName: "qrcode.viewfinder")
                    .foregroundColor(AgentBuddyTheme.accent)
                Text(parsedParams == nil ? "Scan Pairing QR" : "Rescan QR")
                    .agentBuddyFont(.subheadline)
                    .foregroundColor(AgentBuddyTheme.accent)
            }
        }

        DisclosureGroup(
            isExpanded: $showPaste,
            content: {
                pasteJSONEntryControls(minHeight: 90)
            },
            label: {
                Text("Paste Pairing JSON")
                    .agentBuddyFont(.footnote)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            }
        )
    }

    @ViewBuilder
    private func pasteJSONEntryControls(minHeight: CGFloat) -> some View {
        TextEditor(text: $pasteJSON)
            .agentBuddyFont(.caption)
            .foregroundColor(AgentBuddyTheme.textPrimary)
            .scrollContentBackground(.hidden)
            .frame(minHeight: minHeight)
            .overlay(alignment: .topLeading) {
                if pasteJSON.isEmpty {
                    Text(#"{"v":1,"node_id":"...","token":"...","relay":"https://..."}"#)
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                        .padding(.top, 8)
                        .padding(.leading, 4)
                        .allowsHitTesting(false)
                }
            }

        HStack {
            Button("Paste from Clipboard") {
                if let clipboard = UIPasteboard.general.string {
                    pasteJSON = clipboard
                }
            }
            .agentBuddyFont(.footnote)
            .foregroundColor(AgentBuddyTheme.accent)

            Spacer()

            Button(parsedParams == nil ? "Parse JSON" : "Reparse JSON") {
                handleScannedPayload(pasteJSON)
            }
            .agentBuddyFont(.footnote)
            .foregroundColor(AgentBuddyTheme.accent)
            .disabled(pasteJSON.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
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
