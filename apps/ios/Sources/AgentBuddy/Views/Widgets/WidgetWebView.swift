import SwiftUI
import WebKit

/// Container UIView that hosts a WKWebView but suppresses intrinsicContentSize
/// invalidations from propagating to SwiftUI's layout system.
class WidgetWebViewContainer: UIView {
    let webView: WKWebView

    init(webView: WKWebView) {
        self.webView = webView
        super.init(frame: .zero)
        addSubview(webView)
        clipsToBounds = true
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layoutSubviews() {
        super.layoutSubviews()
        webView.frame = bounds
    }

    override var intrinsicContentSize: CGSize { .zero }
    override func invalidateIntrinsicContentSize() { /* suppress */ }
}

struct WidgetWebView: UIViewRepresentable {
    let widgetHTML: String
    let isFinalized: Bool
    var allowsScrollAndZoom: Bool = false
    var isMinigame: Bool = false
    var onMessage: ((Any) -> Void)?
    /// Typed hook for `window.structuredResponse(...)` calls from app-mode
    /// widgets. The coordinator passes a `respond` closure that wraps
    /// `evaluateJavaScript` into `window.__resolveStructuredResponse(...)` /
    /// `__rejectStructuredResponse(...)` so the host can resolve/reject the
    /// widget's Promise once the Rust call returns. Only fires in app mode.
    var onStructuredRequest: ((
        _ requestId: String,
        _ prompt: String,
        _ responseFormatJSON: String,
        _ respond: @escaping (String /* requestId */, String? /* resolveJSON */, String? /* rejectMessage */) -> Void
    ) -> Void)?
    var heightBinding: Binding<CGFloat>?
    var appMode: Bool = false
    var initialAppState: String? = nil
    var schemaVersion: Int = 1

    func makeCoordinator() -> Coordinator {
        Coordinator(
            onMessage: onMessage,
            onStructuredRequest: onStructuredRequest,
            heightBinding: heightBinding
        )
    }

    func makeUIView(context: Context) -> WidgetWebViewContainer {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "widget")

        if isMinigame {
            // Stub out bridge globals so a minigame cannot inject text into the
            // user's conversation or persist state, even if it tries to.
            let stub = """
            window.sendPrompt = function(){};
            window.saveAppState = function(){};
            window.loadAppState = function(){ return null; };
            window.structuredResponse = function(){ return Promise.reject(new Error('disabled in minigame mode')); };
            """
            let script = WKUserScript(source: stub, injectionTime: .atDocumentStart, forMainFrameOnly: true)
            config.userContentController.addUserScript(script)
        }

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = allowsScrollAndZoom
        webView.scrollView.bounces = allowsScrollAndZoom
        if !allowsScrollAndZoom {
            webView.scrollView.pinchGestureRecognizer?.isEnabled = false
        }
        if isMinigame {
            // Disable double-tap-to-zoom in addition to pinch. Setting min/max
            // zoom scales pegs the underlying UIScrollView, and a no-op
            // double-tap recogniser (configured to require failure of any
            // existing double-tap) absorbs the gesture before WebKit's own
            // zoom heuristic kicks in.
            webView.scrollView.minimumZoomScale = 1
            webView.scrollView.maximumZoomScale = 1
            webView.scrollView.bouncesZoom = false
            let absorber = UITapGestureRecognizer(target: context.coordinator,
                                                  action: #selector(Coordinator.absorbDoubleTap(_:)))
            absorber.numberOfTapsRequired = 2
            absorber.cancelsTouchesInView = true
            absorber.delaysTouchesBegan = false
            absorber.delegate = context.coordinator
            webView.addGestureRecognizer(absorber)
        }
        webView.navigationDelegate = context.coordinator

        #if DEBUG
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        #endif

        context.coordinator.webView = webView
        let shell = appMode
            ? Self.buildAppModeShellHTML(
                initialAppState: initialAppState,
                schemaVersion: schemaVersion
            )
            : Self.shellHTML
        webView.loadHTMLString(shell, baseURL: nil)
        return WidgetWebViewContainer(webView: webView)
    }

    static func dismantleUIView(_ container: WidgetWebViewContainer, coordinator: Coordinator) {
        coordinator.teardown()
        container.webView.navigationDelegate = nil
        container.webView.configuration.userContentController.removeScriptMessageHandler(forName: "widget")
    }

    func updateUIView(_ container: WidgetWebViewContainer, context: Context) {
        guard !widgetHTML.isEmpty else { return }
        let coordinator = context.coordinator
        coordinator.onMessage = onMessage
        coordinator.onStructuredRequest = onStructuredRequest
        coordinator.heightBinding = heightBinding
        let escaped = Self.escapeJS(widgetHTML)
        guard escaped != coordinator.lastEscapedHTML || (isFinalized && !coordinator.hasFinalized) else { return }
        coordinator.lastEscapedHTML = escaped

        if isFinalized && !coordinator.hasFinalized {
            coordinator.cancelScheduledUpdate()
            coordinator.hasFinalized = true
            coordinator.sendContent(escaped, runScripts: true)
        } else if !isFinalized {
            coordinator.scheduleUpdate(html: escaped)
        }
    }
}

// MARK: - Height notification

extension Notification.Name {
    static let widgetHeightChanged = Notification.Name("widgetHeightChanged")
}
