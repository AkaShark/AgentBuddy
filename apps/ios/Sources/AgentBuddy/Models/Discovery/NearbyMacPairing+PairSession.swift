#if !targetEnvironment(macCatalyst)
import Foundation

extension NearbyMacPairing {
    // MARK: - WS connect + NI exchange

    func connectAndHandshake(host: String, port: UInt16) {
        state = .connecting
        Task { @MainActor [weak self] in
            guard let self, self.isRunning else { return }
            do {
                // Try to spin up NISession so we can ship a discovery token.
                // If this device or the Mac lacks UWB, the token is empty
                // and the Rust pair host knows to skip ranging — BLE is the
                // proximity signal in that case.
                let tokenB64 = self.prepareNISession()

                let client = try await self.appClient.pairFromIphone(
                    host: host,
                    port: port,
                    deviceName: self.deviceName,
                    niDiscoveryTokenB64: tokenB64
                )
                // User may have cancelled while we were awaiting the
                // connection; drop the just-opened client on the floor
                // instead of leaving it orphaned.
                guard self.isRunning else {
                    Task { await client.stop() }
                    return
                }
                self.pairClient = client
                self.state = .handshaking
                self.startEventPoll()
            } catch {
                LLog.error("pair", "pair_from_iphone failed", error: error)
                self.state = .failed
            }
        }
    }

    private func startEventPoll() {
        pollTask?.cancel()
        pollTask = Task { @MainActor [weak self] in
            while let self, self.isRunning, let client = self.pairClient {
                if let event = await client.pollEvent() {
                    self.handle(event: event)
                } else {
                    // No event — sleep 80ms to avoid spinning.
                    try? await Task.sleep(nanoseconds: 80_000_000)
                }
            }
        }
    }

    private func handle(event: PairEvent) {
        switch event {
        case let .clientPeerAccepted(niDiscoveryTokenB64):
            handleMacNIToken(b64: niDiscoveryTokenB64)
        case let .clientPairAccepted(codexWsUrl, lanIp):
            handlePairAccepted(codexWsUrl: codexWsUrl, lanIp: lanIp)
        case .peerRejected:
            state = .rejected
            isRunning = false
        case let .disconnected(reason):
            LLog.info("pair", "disconnected", fields: ["reason": reason])
            if state != .paired, state != .rejected, state != .failed {
                state = .failed
                isRunning = false
            }
        case .hostPeerConnected, .hostPairRequest, .distanceUpdate:
            // Host-side events; iPhone ignores.
            break
        }
    }

    // MARK: - Pair-request trigger

    func triggerPairRequest(reason: String, distance: Float?) {
        guard !debugMode, !didSendPairRequest else { return }
        guard state == .handshaking else { return }
        didSendPairRequest = true
        state = .awaitingConfirm
        LLog.info(
            "pair",
            "proximity threshold reached",
            fields: [
                "reason": reason,
                "distance_m": distance.map { String(format: "%.2f", $0) } ?? "—",
                "rssi": lastRssi.map(String.init) ?? "—"
            ]
        )
        // Some Rust hosts expect a numeric distance; pass the NI reading
        // when we have it, otherwise the BLE-derived estimate so the Mac UI
        // still gets a "~Xm" hint.
        let payload = distance ?? bleEstimatedDistance.map(Float.init) ?? 0
        try? pairClient?.submitPairRequest(distanceM: payload)
    }

    private func handlePairAccepted(codexWsUrl: String, lanIp: String) {
        state = .paired
        LLog.info(
            "pair",
            "pair accepted",
            fields: ["codex_ws_url": codexWsUrl, "lan_ip": lanIp]
        )
        let port = Self.extractPort(fromWebSocketURL: codexWsUrl) ?? 8390
        let nameOut = discoveredMacName ?? lanIp
        let saved = SavedServer(
            id: "mac-pair-\(lanIp)",
            name: nameOut,
            hostname: lanIp,
            port: port,
            codexPorts: [port],
            sshPort: nil,
            source: .bonjour,
            hasCodexServer: true,
            wakeMAC: nil,
            preferredConnectionMode: .directCodex,
            preferredCodexPort: port,
            sshPortForwardingEnabled: nil,
            websocketURL: codexWsUrl,
            rememberedByUser: true
        )
        var list = SavedServerStore.load()
        list.removeAll { $0.id == saved.id || $0.hostname == saved.hostname }
        list.append(saved)
        SavedServerStore.save(list)
        completedServer = saved
        isRunning = false
        teardown()
    }

    private static func extractPort(fromWebSocketURL url: String) -> UInt16? {
        guard let parsed = URL(string: url), let port = parsed.port,
              port > 0, port <= Int(UInt16.max) else {
            return nil
        }
        return UInt16(port)
    }
}
#endif
