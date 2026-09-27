import AVFoundation
import SwiftUI
import UIKit

// MARK: - QR Scanner

struct AlleycatQRScannerScreen: View {
    let onScan: (String) -> Void
    let onCancel: () -> Void
    let onPermissionDenied: () -> Void

    private static let pairCommand = "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr"

    @State private var copied = false

    var body: some View {
        ZStack {
            QRCaptureSheet(
                onScan: onScan,
                onCancel: onCancel,
                onPermissionDenied: onPermissionDenied
            )
            .ignoresSafeArea()
            .accessibilityHidden(true)

            VStack(spacing: BuddySpacing.md) {
                topBar
                instructionsCard
                Spacer()
                framingHint
            }
            .padding(.horizontal, BuddySpacing.md)
            .padding(.top, BuddySpacing.xs)
            .padding(.bottom, BuddySpacing.xl)
        }
    }

    private var topBar: some View {
        HStack {
            Spacer()
            Button(action: onCancel) {
                Text("Cancel")
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .padding(.horizontal, BuddySpacing.md)
                    .frame(minHeight: BuddySize.minHitTarget)
                    .background(AgentBuddyTheme.surface, in: Capsule())
                    .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 4)
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("alleycat.scanner.cancelButton")
        }
    }

    /// Explains where the code comes from. Only the public setup command is
    /// shown here; pairing details stay inside the QR code.
    private var instructionsCard: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                Text("Scan the pairing code")
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text("The code comes from AgentBuddy on your computer.")
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            stepRow(number: "1", title: "Open AgentBuddy on the computer and go to Pairing.")
            stepRow(number: "2", title: "Point this camera at the QR code it shows.")

            BuddyDivider()

            VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                Text("No desktop app? On the host you want to connect to, run:")
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                commandRow
            }
        }
        .padding(BuddySpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            AgentBuddyTheme.surface,
            in: RoundedRectangle(cornerRadius: BuddyRadius.card, style: .continuous)
        )
        .shadow(color: AgentBuddyTheme.floatingShadow, radius: 24, y: 8)
    }

    private func stepRow(number: String, title: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.sm) {
            Text(verbatim: number)
                .buddyText(.caption, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.onBrand)
                .frame(width: 24, height: 24)
                .background(AgentBuddyTheme.brand, in: Circle())
                .accessibilityHidden(true)
            Text(title)
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var commandRow: some View {
        HStack(spacing: BuddySpacing.xs) {
            Text(verbatim: Self.pairCommand)
                .buddyText(.code)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, BuddySpacing.sm)
                .padding(.vertical, BuddySpacing.xs)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    AgentBuddyTheme.surfaceSoft,
                    in: RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                )
                .textSelection(.enabled)
            Button(action: copyCommand) {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(copied ? AgentBuddyTheme.success : AgentBuddyTheme.textPrimary)
                    .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                    .background(AgentBuddyTheme.surfaceSoft, in: Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(copied ? Text("Copied") : Text("Copy command"))
            .accessibilityIdentifier("alleycat.scanner.copyCommandButton")
        }
    }

    private var framingHint: some View {
        Text("Hold steady — the QR code is detected automatically.")
            .buddyText(.label, weight: .regular)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .multilineTextAlignment(.center)
            .padding(.horizontal, BuddySpacing.md)
            .padding(.vertical, BuddySpacing.xs)
            .background(AgentBuddyTheme.surface, in: Capsule())
            .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 4)
    }

    private func copyCommand() {
        UIPasteboard.general.string = Self.pairCommand
        withAnimation(.easeOut(duration: 0.15)) { copied = true }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.4))
            withAnimation(.easeOut(duration: 0.15)) { copied = false }
        }
    }
}

private struct QRCaptureSheet: UIViewControllerRepresentable {
    let onScan: (String) -> Void
    let onCancel: () -> Void
    let onPermissionDenied: () -> Void

    func makeUIViewController(context: Context) -> QRScannerViewController {
        let controller = QRScannerViewController()
        controller.onScan = onScan
        controller.onCancel = onCancel
        controller.onPermissionDenied = onPermissionDenied
        return controller
    }

    func updateUIViewController(_ uiViewController: QRScannerViewController, context: Context) {}
}

private final class QRScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onScan: ((String) -> Void)?
    var onCancel: (() -> Void)?
    var onPermissionDenied: (() -> Void)?

    private let captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let metadataQueue = DispatchQueue(label: "com.alleycat.qrscanner")
    private var didReportScan = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureSession()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        guard !captureSession.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession.isRunning {
            captureSession.stopRunning()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
    }

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video) else {
            onPermissionDenied?()
            return
        }
        guard let input = try? AVCaptureDeviceInput(device: device) else {
            onPermissionDenied?()
            return
        }
        if captureSession.canAddInput(input) {
            captureSession.addInput(input)
        } else {
            onPermissionDenied?()
            return
        }

        let output = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(output) {
            captureSession.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: metadataQueue)
            if output.availableMetadataObjectTypes.contains(.qr) {
                output.metadataObjectTypes = [.qr]
            }
        } else {
            onPermissionDenied?()
            return
        }

        let preview = AVCaptureVideoPreviewLayer(session: captureSession)
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
        previewLayer = preview
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !didReportScan else { return }
        guard let payload = metadataObjects
            .compactMap({ $0 as? AVMetadataMachineReadableCodeObject })
            .first(where: { $0.type == .qr })?
            .stringValue
        else { return }
        didReportScan = true
        DispatchQueue.main.async { [weak self] in
            self?.captureSession.stopRunning()
            self?.onScan?(payload)
        }
    }
}
