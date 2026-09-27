import Foundation
import Network

final class ChatGPTOAuthLoopbackServer: @unchecked Sendable {
    private let bindHost: String
    private let publicHost: String
    private let port: UInt16
    private let path: String
    private let timeout: Duration
    private let queue = DispatchQueue(label: "com.akashark.agentbuddy.chatgpt-oauth")
    private let stateLock = NSLock()

    private var listener: NWListener?
    private var startContinuation: CheckedContinuation<String, Error>?
    private var callbackContinuation: CheckedContinuation<URL, Error>?
    private var pendingCallbackResult: Result<URL, Error>?
    private var timeoutTask: Task<Void, Never>?
    private var didDeliverCallback = false

    init(bindHost: String, publicHost: String, port: UInt16, path: String, timeout: Duration) throws {
        self.bindHost = bindHost
        self.publicHost = publicHost
        self.port = port
        self.path = path
        self.timeout = timeout
    }

    func start() async throws -> String {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            throw ChatGPTOAuthError.invalidCallbackURL
        }
        let listener = try NWListener(using: .tcp, on: nwPort)
        self.listener = listener
        listener.newConnectionHandler = { [weak self] connection in
            self?.handle(connection)
        }

        return try await withCheckedThrowingContinuation { continuation in
            self.startContinuation = continuation
            listener.stateUpdateHandler = { [weak self] (state: NWListener.State) in
                guard let self else { return }
                switch state {
                case .ready:
                    LLog.info("auth", "ChatGPT auth callback listener ready", fields: [
                        "bindHost": self.bindHost,
                        "publicHost": self.publicHost,
                        "port": self.port,
                        "path": self.path
                    ])
                    self.timeoutTask = Task { [weak self] in
                        do {
                            try await Task.sleep(for: self?.timeout ?? .seconds(0))
                        } catch {
                            return
                        }
                        guard !Task.isCancelled else { return }
                        LLog.warn("auth", "ChatGPT auth callback listener timed out", fields: [
                            "port": self?.port ?? 0
                        ])
                        self?.resumeCallback(with: .failure(ChatGPTOAuthError.callbackTimedOut))
                    }
                    self.resumeStart(
                        with: .success("http://\(self.publicHost):\(self.port)\(self.path)")
                    )
                case .failed(let error):
                    LLog.warn("auth", "ChatGPT auth callback listener failed", fields: [
                        "error": error.localizedDescription
                    ])
                    self.resumeStart(with: .failure(error))
                    self.resumeCallback(with: .failure(error))
                default:
                    break
                }
            }
            listener.start(queue: queue)
        }
    }

    func waitForCallback() async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let pendingResult: Result<URL, Error>? = withStateLock {
                if let pendingCallbackResult {
                    self.pendingCallbackResult = nil
                    self.didDeliverCallback = true
                    return pendingCallbackResult
                }
                callbackContinuation = continuation
                return nil
            }

            guard let pendingResult else { return }
            switch pendingResult {
            case .success(let callbackURL):
                continuation.resume(returning: callbackURL)
            case .failure(let error):
                continuation.resume(throwing: error)
            }
        }
    }

    func stop() {
        let state = withStateLock { () -> (Task<Void, Never>?, NWListener?) in
            let state = (timeoutTask, listener)
            timeoutTask = nil
            listener = nil
            startContinuation = nil
            callbackContinuation = nil
            pendingCallbackResult = nil
            didDeliverCallback = true
            return state
        }
        state.0?.cancel()
        state.1?.cancel()
    }

    private func handle(_ connection: NWConnection) {
        LLog.info("auth", "ChatGPT auth callback connection accepted")
        connection.start(queue: queue)
        receiveRequest(on: connection, buffer: Data())
    }

    private func receiveRequest(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { [weak self] data, _, isComplete, error in
            guard let self else {
                connection.cancel()
                return
            }
            if let error {
                self.resumeCallback(with: .failure(error))
                connection.cancel()
                return
            }

            var nextBuffer = buffer
            if let data {
                nextBuffer.append(data)
            }

            let hasHeaders = nextBuffer.range(of: Data("\r\n\r\n".utf8)) != nil
            if hasHeaders || isComplete {
                self.processRequestData(nextBuffer, on: connection)
                return
            }

            self.receiveRequest(on: connection, buffer: nextBuffer)
        }
    }

    private func processRequestData(_ data: Data, on connection: NWConnection) {
        let requestText = String(decoding: data, as: UTF8.self)
        let requestLine = requestText.components(separatedBy: "\r\n").first ?? ""
        LLog.info("auth", "ChatGPT auth callback request received", fields: [
            "requestLine": requestLine
        ])
        let pathWithQuery = requestLine
            .split(separator: " ", omittingEmptySubsequences: true)
            .dropFirst()
            .first
            .map(String.init) ?? ""

        guard !pathWithQuery.isEmpty,
              let callbackURL = URL(string: "http://\(publicHost):\(port)\(pathWithQuery)"),
              let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false),
              components.path == path else {
            LLog.warn("auth", "ChatGPT auth callback rejected", fields: [
                "requestLine": requestLine,
                "pathWithQuery": pathWithQuery
            ])
            sendResponse(
                statusLine: "HTTP/1.1 404 Not Found",
                body: "<html><body><h3>Not found</h3></body></html>",
                on: connection
            )
            return
        }

        sendResponse(
            statusLine: "HTTP/1.1 200 OK",
            body: "<html><body><h3>Login complete</h3><p>You can return to AgentBuddy.</p></body></html>",
            on: connection
        )
        LLog.info("auth", "ChatGPT auth callback accepted", fields: [
            "path": path,
            "hasCode": URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?
                .queryItems?
                .contains(where: { $0.name == "code" }) ?? false,
            "hasError": URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?
                .queryItems?
                .contains(where: { $0.name == "error" }) ?? false
        ])
        resumeCallback(with: .success(callbackURL))
    }

    private func sendResponse(statusLine: String, body: String, on connection: NWConnection) {
        let bodyData = Data(body.utf8)
        let header = [
            statusLine,
            "Content-Type: text/html; charset=UTF-8",
            "Connection: close",
            "Content-Length: \(bodyData.count)",
            "",
            ""
        ].joined(separator: "\r\n")
        var response = Data(header.utf8)
        response.append(bodyData)
        connection.send(content: response, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private func resumeStart(with result: Result<String, Error>) {
        let continuation = withStateLock {
            let continuation = startContinuation
            startContinuation = nil
            return continuation
        }
        guard let continuation else { return }
        switch result {
        case .success(let redirectURI):
            continuation.resume(returning: redirectURI)
        case .failure(let error):
            continuation.resume(throwing: error)
        }
    }

    private func resumeCallback(with result: Result<URL, Error>) {
        let state = withStateLock { () -> (CheckedContinuation<URL, Error>?, Task<Void, Never>?, NWListener?) in
            guard !didDeliverCallback else { return (nil, nil, nil) }
            didDeliverCallback = true
            let continuation = callbackContinuation
            callbackContinuation = nil
            if continuation == nil {
                pendingCallbackResult = result
            }
            let timeoutTask = self.timeoutTask
            self.timeoutTask = nil
            let listener = self.listener
            self.listener = nil
            return (continuation, timeoutTask, listener)
        }
        state.1?.cancel()
        state.2?.cancel()
        guard let continuation = state.0 else { return }
        switch result {
        case .success(let callbackURL):
            continuation.resume(returning: callbackURL)
        case .failure(let error):
            continuation.resume(throwing: error)
        }
    }

    private func withStateLock<T>(_ body: () -> T) -> T {
        stateLock.lock()
        defer { stateLock.unlock() }
        return body()
    }
}
