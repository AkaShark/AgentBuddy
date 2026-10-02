import SwiftUI
import WebKit

extension SettingsView {
    var supportSection: some View {
        Section {
            NavigationLink {
                SupportWebView(url: URL(string: "https://akashark.github.io/AgentBuddy/privacy/")!)
                    .navigationTitle("Privacy & Data Deletion")
                    .navigationBarTitleDisplayMode(.inline)
            } label: {
                SettingsMintRowLabel("Privacy & Data Deletion", systemImage: "hand.raised")
            }
            .settingsMintRow()
            NavigationLink {
                SupportWebView(url: URL(string: "https://agentbuddy-content-reports.aaksharker.workers.dev/report")!)
                    .navigationTitle("Report AI Content")
                    .navigationBarTitleDisplayMode(.inline)
            } label: {
                SettingsMintRowLabel("Report AI Content", systemImage: "flag")
            }
            .settingsMintRow()
        } header: {
            Text("Support & Privacy").settingsMintHeader()
        }
    }
}

/// The hosted form is shared with Android. It never receives conversation state.
private struct SupportWebView: View {
    let url: URL
    @State private var failed = false
    @State private var attempt = 0

    var body: some View {
        ZStack {
            SupportWebContent(url: url, failed: $failed)
                .id(attempt)
            if failed {
                ContentUnavailableView {
                    Label("Unable to Load", systemImage: "wifi.exclamationmark")
                } description: {
                    Text("Check your connection and try again.")
                } actions: {
                    Button("Retry") { failed = false; attempt += 1 }
                        .tint(AgentBuddyTheme.link)
                }
                .background(AgentBuddyTheme.background)
            }
        }
    }
}

private struct SupportWebContent: UIViewRepresentable {
    let url: URL
    @Binding var failed: Bool

    func makeCoordinator() -> Coordinator { Coordinator(failed: $failed) }
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        config.defaultWebpagePreferences.allowsContentJavaScript = false
        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = context.coordinator
        view.allowsBackForwardNavigationGestures = true
        view.load(URLRequest(url: url))
        return view
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.stopLoading()
        uiView.navigationDelegate = nil
    }
    final class Coordinator: NSObject, WKNavigationDelegate {
        @Binding var failed: Bool
        init(failed: Binding<Bool>) { _failed = failed }
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            let url = navigationAction.request.url
            let allowed = url?.scheme == "https" && ["akashark.github.io", "agentbuddy-content-reports.aaksharker.workers.dev"].contains(url?.host ?? "")
            decisionHandler(allowed ? .allow : .cancel)
        }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            if (error as NSError).code != NSURLErrorCancelled { failed = true }
        }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            if (error as NSError).code != NSURLErrorCancelled { failed = true }
        }
    }
}
