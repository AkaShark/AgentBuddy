import SwiftUI
import WebKit

extension WidgetWebView {
    // MARK: - Coordinator

    class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate, UIGestureRecognizerDelegate {
        @objc func absorbDoubleTap(_ recogniser: UITapGestureRecognizer) {
            // No-op. Recogniser is attached only in minigame mode to swallow
            // the double-tap-to-zoom gesture before WebKit reacts to it.
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            // Let our absorber sit alongside any of WebKit's own recognisers.
            return true
        }

        var webView: WKWebView?
        var onMessage: ((Any) -> Void)?
        var onStructuredRequest: ((
            _ requestId: String,
            _ prompt: String,
            _ responseFormatJSON: String,
            _ respond: @escaping (String, String?, String?) -> Void
        ) -> Void)?
        var heightBinding: Binding<CGFloat>?
        var hasFinalized = false
        var lastEscapedHTML: String?
        private var shellReady = false
        private var queuedJS: String?
        private var updateTimer: Timer?
        private var pendingHTML: String?
        private var heightTimer: Timer?
        private var pendingHeight: CGFloat = 0
        private var lastCommittedHeight: CGFloat = 0

        init(
            onMessage: ((Any) -> Void)?,
            onStructuredRequest: ((
                _ requestId: String,
                _ prompt: String,
                _ responseFormatJSON: String,
                _ respond: @escaping (String, String?, String?) -> Void
            ) -> Void)? = nil,
            heightBinding: Binding<CGFloat>? = nil
        ) {
            self.onMessage = onMessage
            self.onStructuredRequest = onStructuredRequest
            self.heightBinding = heightBinding
        }

        func teardown() {
            updateTimer?.invalidate()
            updateTimer = nil
            pendingHTML = nil
            heightTimer?.invalidate()
            heightTimer = nil
            webView = nil
        }

        func cancelScheduledUpdate() {
            updateTimer?.invalidate()
            updateTimer = nil
            pendingHTML = nil
        }

        func sendContent(_ escaped: String, runScripts: Bool) {
            let js = runScripts
                ? "window._setContent('\(escaped)'); window._runScripts();"
                : "window._setContent('\(escaped)');"
            if shellReady {
                webView?.evaluateJavaScript(js, completionHandler: nil)
            } else {
                queuedJS = js
            }
        }

        func scheduleUpdate(html: String) {
            pendingHTML = html
            guard updateTimer == nil else { return }
            updateTimer = Timer.scheduledTimer(withTimeInterval: 0.15, repeats: false) { [weak self] _ in
                guard let self, let html = self.pendingHTML else { return }
                self.pendingHTML = nil
                self.updateTimer = nil
                self.sendContent(html, runScripts: false)
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            shellReady = true
            if let js = queuedJS {
                queuedJS = nil
                webView.evaluateJavaScript(js, completionHandler: nil)
            }
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "widget" else { return }
            if let dict = message.body as? [String: Any],
               dict["_type"] as? String == "height",
               let h = dict["value"] as? CGFloat, h > 0 {
                pendingHeight = h
                heightTimer?.invalidate()
                heightTimer = Timer.scheduledTimer(withTimeInterval: 0.15, repeats: false) { [weak self] _ in
                    guard let self else { return }
                    let finalHeight = ceil(self.pendingHeight)
                    guard abs(finalHeight - self.lastCommittedHeight) > 1 else { return }
                    self.lastCommittedHeight = finalHeight
                    DispatchQueue.main.async {
                        self.heightBinding?.wrappedValue = finalHeight
                    }
                }
                return
            }
            // Structured-response calls have a typed handler + reply path.
            if let dict = message.body as? [String: Any],
               dict["_type"] as? String == "structuredResponse",
               let requestId = dict["requestId"] as? String,
               let prompt = dict["prompt"] as? String {
                let schemaJson: String
                if let raw = dict["responseFormat"] {
                    if let s = raw as? String {
                        schemaJson = s
                    } else if let data = try? JSONSerialization.data(withJSONObject: raw, options: []),
                              let s = String(data: data, encoding: .utf8) {
                        schemaJson = s
                    } else {
                        schemaJson = "null"
                    }
                } else {
                    schemaJson = "null"
                }
                let respond: (String, String?, String?) -> Void = { [weak self] reqId, resolveJSON, rejectMessage in
                    guard let webView = self?.webView else { return }
                    let script: String
                    if let resolveJSON {
                        script = "window.__resolveStructuredResponse(\(Self.jsStringLiteral(reqId)), \(Self.jsStringLiteral(resolveJSON)));"
                    } else {
                        script = "window.__rejectStructuredResponse(\(Self.jsStringLiteral(reqId)), \(Self.jsStringLiteral(rejectMessage ?? "structuredResponse failed")));"
                    }
                    DispatchQueue.main.async {
                        webView.evaluateJavaScript(script, completionHandler: nil)
                    }
                }
                if let handler = onStructuredRequest {
                    handler(requestId, prompt, schemaJson, respond)
                } else {
                    respond(requestId, nil, "structuredResponse is not supported in this context")
                }
                return
            }
            // `saveAppState` / `sendPrompt` / `openLink` all flow through the
            // same `onMessage` hook — callers that don't care about app-mode
            // simply never see `saveAppState` messages (timeline widgets use
            // the cached shell that doesn't expose `window.saveAppState`).
            onMessage?(message.body)
        }

        /// Emit a JS single-quoted string literal for safe splicing into
        /// `evaluateJavaScript` replies. Mirrors `WidgetWebView.escapeJS`
        /// — kept local to the Coordinator so reply plumbing stays
        /// independent of the static shell helpers.
        private static func jsStringLiteral(_ s: String) -> String {
            var out = "'"
            for c in s {
                switch c {
                case "\\": out.append("\\\\")
                case "'":  out.append("\\'")
                case "\n": out.append("\\n")
                case "\r": out.append("\\r")
                case "\u{2028}": out.append("\\u2028")
                case "\u{2029}": out.append("\\u2029")
                default: out.append(c)
                }
            }
            out.append("'")
            return out
        }

        // Block navigation to external URLs
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            if navigationAction.navigationType == .other || navigationAction.request.url?.scheme == "about" {
                decisionHandler(.allow)
                return
            }
            // Allow CDN script loads
            if let host = navigationAction.request.url?.host {
                let allowedHosts = ["cdnjs.cloudflare.com", "esm.sh", "cdn.jsdelivr.net", "unpkg.com"]
                if allowedHosts.contains(host) {
                    decisionHandler(.allow)
                    return
                }
            }
            // Open external links in Safari
            if let url = navigationAction.request.url, navigationAction.navigationType == .linkActivated {
                UIApplication.shared.open(url)
            }
            decisionHandler(.cancel)
        }
    }
}
