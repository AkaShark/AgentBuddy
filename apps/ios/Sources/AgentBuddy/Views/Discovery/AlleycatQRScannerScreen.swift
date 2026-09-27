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
            Color.black.ignoresSafeArea()
            QRCaptureSheet(
                onScan: onScan,
                onCancel: onCancel,
                onPermissionDenied: onPermissionDenied
            )
            .ignoresSafeArea()

            LinearGradient(
                colors: [Color.black.opacity(0.55), Color.black.opacity(0.0)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 320)
            .frame(maxHeight: .infinity, alignment: .top)
            .ignoresSafeArea()
            .allowsHitTesting(false)

            VStack(spacing: 16) {
                topBar
                instructionsCard
                Spacer()
                framingHint
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
    }

    private var topBar: some View {
        HStack {
            Spacer()
            Button(action: onCancel) {
                Text("Cancel")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(.black.opacity(0.45), in: Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("alleycat.scanner.cancelButton")
        }
    }

    private var instructionsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Pair with AgentBuddy")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)

            stepRow(number: "1", title: "On the host you want to connect to, run:")
            commandRow
            stepRow(number: "2", title: "Point this camera at the QR code it prints.")
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.black.opacity(0.55))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(.white.opacity(0.12), lineWidth: 0.8)
        )
    }

    private func stepRow(number: String, title: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(number)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.black)
                .frame(width: 20, height: 20)
                .background(AgentBuddyTheme.accent, in: Circle())
            Text(title)
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.92))
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var commandRow: some View {
        HStack(spacing: 10) {
            Text(Self.pairCommand)
                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(.white.opacity(0.12))
                )
            Button(action: copyCommand) {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 36, height: 36)
                    .background(.white.opacity(0.14), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("alleycat.scanner.copyCommandButton")
        }
        .padding(.leading, 30)
    }

    private var framingHint: some View {
        Text("Hold steady — the QR code is detected automatically.")
            .font(.system(size: 12))
            .foregroundColor(.white.opacity(0.75))
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(.black.opacity(0.4), in: Capsule())
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
