import SwiftUI
import UIKit

/// Full-screen terminal. The Ghostty surface fills the entire body —
/// keystrokes go straight to the PTY via the hidden first-responder text
/// field, and the Esc/Ctrl/Tab/arrows row docks above the system keyboard
/// as an input accessory view (set up inside `GhosttyHostView`).
///
/// The header (backend chip + "Aa" appearance button) sits on top; we keep
/// the SSH trust banner as a transient overlay when needed.
struct TerminalScreen: View {
    let cwd: String?
    var preferredAlleycatNodeId: String? = nil

    @State var controller = TerminalSessionController()
    @State var backendOptions: [TerminalBackendOption] = []
    @State var selectedBackendID: String?
    @State private var didStart = false
    @State var terminalGridSize = TerminalGridSize(cols: 80, rows: 24)
    @State var terminalSurfaceSize: CGSize = .zero
    @State var ghosttyRenderer = GhosttyTerminalRenderer()
    @State var nativeRendererHasOutput = false
    @State private var showConfigSheet = false
    @AppStorage("agentbuddy.terminal.fontSize") var storedFontSize: Double = 13.0
    @AppStorage("agentbuddy.terminal.themeId") var storedThemeId: String = "litter-dark"
    @AppStorage("agentbuddy.terminal.cursorBlink") var storedCursorBlink: Bool = true
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    let accent = Color(red: 0, green: 1, blue: 0.612)
    let alleycatServerIdPrefix = "alleycat:"

    var body: some View {
        GeometryReader { geometry in
            let terminalInsets = terminalHorizontalInsets(for: geometry)

            VStack(spacing: 0) {
                terminalNavigationBar(topInset: geometry.safeAreaInsets.top)
                backendBar
                terminalSurface(
                    contentLeadingInset: terminalInsets.leading,
                    contentTrailingInset: terminalInsets.trailing
                )
            }
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .top)
            .background(Color.black)
        }
        .background(Color.black.ignoresSafeArea())
        .ignoresSafeArea(.container, edges: [.top, .bottom, .horizontal])
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .toolbar(.hidden, for: .navigationBar)
        .sshHostKeyChangeAlert($controller.sshHostKeyChange)
        .task {
            attachOutputSink()
            guard !didStart else { return }
            didStart = true
            let options = refreshBackendOptions()
            if let initial = initialBackend(from: options, cwd: cwd) {
                selectedBackendID = initial.id
                await controller.open(backend: initial.backend)
            }
            applyConfigSettings()
        }
        .onReceive(NotificationCenter.default.publisher(for: .agentBuddySavedServersDidChange)) { _ in
            reconcileBackendOptions()
        }
        .onChange(of: appSnapshotRevision) { _, _ in
            reconcileBackendOptions()
        }
        .onDisappear {
            // End any active first-responder hold so the keyboard tears
            // down and SwiftUI releases first responder. Without this the
            // keyboard can linger after navigating back, leaving the
            // parent screen unable to receive touches until the OS gives
            // up on the detached responder chain.
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder),
                to: nil,
                from: nil,
                for: nil
            )
            controller.setOutputSink(nil)
            controller.close()
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                ghosttyRenderer.setOccluded(false)
            case .inactive, .background:
                ghosttyRenderer.setOccluded(true)
            @unknown default:
                break
            }
        }
    }

    private func terminalNavigationBar(topInset: CGFloat) -> some View {
        ZStack {
            HStack {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 54, height: 54)
                        .background(Color.white.opacity(0.09))
                        .clipShape(Circle())
                        .overlay {
                            Circle()
                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                        }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")

                Spacer(minLength: 0)
            }

            Text("Terminal")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 14)
        .padding(.top, topInset + 8)
        .frame(height: topInset + 86)
        .background(Color.black)
    }

    private func terminalHorizontalInsets(for geometry: GeometryProxy) -> (leading: CGFloat, trailing: CGFloat) {
        let leading = max(geometry.safeAreaInsets.leading, 0)
        let trailing = max(geometry.safeAreaInsets.trailing, 0)
        guard UIDevice.current.userInterfaceIdiom == .phone,
              leading == 0,
              trailing == 0,
              geometry.size.width > geometry.size.height else {
            return (leading, trailing)
        }
        return (58, 58)
    }

    var selectedBackend: TerminalBackendOption? {
        backendOptions.first { $0.id == selectedBackendID } ?? backendOptions.first
    }

    private var appSnapshotRevision: UInt64 {
        AppModel.shared.snapshotRevision
    }

    private var backendBar: some View {
        HStack(spacing: 10) {
            Menu {
                ForEach(backendOptions) { option in
                    Button {
                        selectBackend(option)
                    } label: {
                        Label(option.title, systemImage: option.systemImage)
                    }
                }
                if backendOptions.count <= 1 {
                    Divider()
                    Button("No remote terminal servers") {}
                        .disabled(true)
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: selectedBackend?.systemImage ?? "terminal")
                    Text(selectedBackend?.title ?? "No server")
                        .lineLimit(1)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 10, weight: .bold))
                }
                .font(.custom("SFMono-Regular", size: 12))
                .foregroundColor(accent)
                .padding(.horizontal, 10)
                .frame(height: 34)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }

            Text(selectedBackend?.subtitle ?? "Add a remote server")
                .font(.custom("SFMono-Regular", size: 11))
                .foregroundColor(.white.opacity(0.48))
                .lineLimit(1)

            Spacer(minLength: 0)

            phaseChip

            Button {
                showConfigSheet = true
            } label: {
                Text("Aa")
                    .font(.custom("SFMono-Regular", size: 13))
                    .foregroundColor(accent)
                    .frame(width: 34, height: 30)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .accessibilityLabel("Theme and font")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(Color.black)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.white.opacity(0.08))
                .frame(height: 1)
        }
        .sheet(isPresented: $showConfigSheet) {
            TerminalConfigSheet(
                fontSize: $storedFontSize,
                themeId: $storedThemeId,
                cursorBlink: $storedCursorBlink,
                onApply: { fontSize, themeId, cursorBlink in
                    applyConfigSettings(
                        fontSize: fontSize,
                        themeId: themeId,
                        cursorBlink: cursorBlink,
                        regrid: true
                    )
                }
            )
        }
    }

    private var phaseChip: some View {
        HStack(spacing: 4) {
            Image(systemName: phaseIcon)
                .font(.system(size: 10, weight: .semibold))
            Text(phaseLabel)
                .font(.custom("SFMono-Regular", size: 11))
                .lineLimit(1)
        }
        .foregroundColor(phaseColor)
        .padding(.horizontal, 8)
        .frame(height: 22)
        .background(phaseColor.opacity(0.12))
        .clipShape(Capsule())
    }

    private var phaseIcon: String {
        switch controller.phase {
        case .idle, .connecting: return "circle.dotted"
        case .running: return "terminal"
        case .exited: return "checkmark.circle"
        case .failed: return "exclamationmark.triangle"
        }
    }

    var phaseColor: Color {
        switch controller.phase {
        case .idle, .connecting: return .white.opacity(0.45)
        case .running: return accent
        case .exited: return .white.opacity(0.5)
        case .failed: return .red
        }
    }

    private var phaseLabel: String {
        switch controller.phase {
        case .idle: return "idle"
        case .connecting: return "connecting"
        case .running: return selectedBackend?.runningLabel ?? "running"
        case .exited(let code): return "exited \(code)"
        case .failed: return "failed"
        }
    }
}

#if DEBUG
#Preview("Terminal") {
    NavigationStack {
        TerminalScreen(cwd: "/root")
    }
}
#endif
