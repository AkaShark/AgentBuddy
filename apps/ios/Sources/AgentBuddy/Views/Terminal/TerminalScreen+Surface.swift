import SwiftUI

extension TerminalScreen {
    func terminalSurface(
        contentLeadingInset: CGFloat,
        contentTrailingInset: CGFloat
    ) -> some View {
        GeometryReader { geometry in
            let leadingInset = max(contentLeadingInset, 0)
            let trailingInset = max(contentTrailingInset, 0)
            let contentWidth = max(1, geometry.size.width - leadingInset - trailingInset)
            let contentSize = CGSize(width: contentWidth, height: geometry.size.height)
            let background = terminalSurfaceBackground

            ZStack(alignment: .topLeading) {
                Rectangle()
                    .fill(background)
                    .frame(
                        width: geometry.size.width,
                        height: geometry.size.height,
                        alignment: .topLeading
                    )

                ZStack(alignment: .topLeading) {
                    GhosttyTerminalView(
                        renderer: ghosttyRenderer,
                        onNativeOutputVisibilityChanged: { visible in
                            nativeRendererHasOutput = visible
                        },
                        onInput: { data in
                            Task { await controller.send(data) }
                        },
                        onClearTapped: {
                            controller.clearOutput()
                            ghosttyRenderer.clearScreen()
                            nativeRendererHasOutput = false
                        },
                        onSendToAssistant: sendOutputToAssistant,
                        onFontSizePinched: { newSize in
                            storedFontSize = newSize
                            applyConfigSettings(
                                fontSize: newSize,
                                themeId: storedThemeId,
                                cursorBlink: storedCursorBlink,
                                regrid: true
                            )
                        },
                        fontSize: storedFontSize
                    )
                    .frame(
                        width: contentWidth,
                        height: geometry.size.height,
                        alignment: .topLeading
                    )
                    .background(background)

                    if shouldShowStatusOverlay {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(displayText)
                                .font(.custom("SFMono-Regular", size: storedFontSize))
                                .foregroundColor(phaseColor)
                                .textSelection(.enabled)
                            if let challenge = controller.sshTrustChallenge {
                                Button {
                                    Task { await controller.trustUnknownSshHostAndRetry() }
                                } label: {
                                    Label("Trust \(challenge.fingerprint)", systemImage: "key.fill")
                                        .buddyText(.code)
                                        .foregroundStyle(Color(hex: ThemeStore.shared.dark.onBrand))
                                        .lineLimit(1)
                                        .truncationMode(.middle)
                                        .padding(.horizontal, BuddySpacing.sm)
                                        .frame(minHeight: BuddySize.minHitTarget)
                                        .background(accent, in: RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                    }
                }
                .frame(
                    width: contentWidth,
                    height: geometry.size.height,
                    alignment: .topLeading
                )
                .offset(x: leadingInset)
            }
            .frame(
                width: geometry.size.width,
                height: geometry.size.height,
                alignment: .topLeading
            )
            .background(background)
            .onAppear {
                updateTerminalContentSize(contentSize)
                DispatchQueue.main.async {
                    applyConfigSettings()
                }
            }
            .onChange(of: contentSize) { _, size in
                updateTerminalContentSize(size)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var terminalSurfaceBackground: Color {
        if storedThemeId == TerminalThemeChoice.agentBuddyDark.rawValue {
            return Color(hex: "#282C34")
        }
        return Color(hex: themePalette(preset: TerminalThemeChoice.preset(forId: storedThemeId)).background)
    }

    private var displayText: String {
        if !controller.output.isEmpty {
            return controller.output
        }
        switch controller.phase {
        case .idle, .connecting:
            return "Connecting...\n"
        case .running:
            return ""
        case .exited(let code):
            return "\n[process exited \(code)]\n"
        case .failed(let message):
            return "\n[terminal failed: \(message)]\n"
        }
    }

    private var shouldShowStatusOverlay: Bool {
        if !controller.output.isEmpty {
            return !nativeRendererHasOutput
        }
        switch controller.phase {
        case .idle, .connecting, .failed, .exited:
            return true
        case .running:
            return false
        }
    }
}
