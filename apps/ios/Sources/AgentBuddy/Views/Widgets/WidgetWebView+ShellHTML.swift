import SwiftUI

extension WidgetWebView {
    // MARK: - Shell HTML

    private static var _cachedHTML: String?
    private static var _cachedSlug: String?

    static var shellHTML: String {
        let theme = ThemeStore.shared.dark
        if let cached = _cachedHTML, _cachedSlug == theme.slug { return cached }
        let html = buildShellHTML(theme: theme)
        _cachedHTML = html
        _cachedSlug = theme.slug
        return html
    }

    private static func hexToRGBA(_ hex: String, _ alpha: Double) -> String {
        let h = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        guard h.count >= 6,
              let r = UInt8(h.prefix(2), radix: 16),
              let g = UInt8(h.dropFirst(2).prefix(2), radix: 16),
              let b = UInt8(h.dropFirst(4).prefix(2), radix: 16) else {
            return "rgba(128,128,128,\(alpha))"
        }
        return "rgba(\(r),\(g),\(b),\(alpha))"
    }

    private static func buildShellHTML(theme: ResolvedTheme) -> String {
        """
    <!DOCTYPE html><html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1.0">
    <style>
    :root {
        --color-background-primary: \(theme.background);
        --color-background-secondary: \(theme.surface);
        --color-background-tertiary: \(theme.surfaceLight);
        --color-background-info: #0d253a;
        --color-background-danger: #3a1414;
        --color-background-success: #0d2a14;
        --color-background-warning: #3a2a0d;
        --color-text-primary: \(theme.textPrimary);
        --color-text-secondary: \(theme.textSecondary);
        --color-text-tertiary: \(theme.textMuted);
        --color-text-info: \(theme.accent);
        --color-text-danger: \(theme.danger);
        --color-text-success: \(theme.success);
        --color-text-warning: \(theme.warning);
        --color-border-tertiary: \(hexToRGBA(theme.border, 0.15));
        --color-border-secondary: \(hexToRGBA(theme.border, 0.3));
        --color-border-primary: \(hexToRGBA(theme.border, 0.4));
        --color-border-info: \(hexToRGBA(theme.accent, 0.4));
        --color-border-danger: \(hexToRGBA(theme.danger, 0.4));
        --color-border-success: \(hexToRGBA(theme.success, 0.4));
        --color-border-warning: \(hexToRGBA(theme.warning, 0.4));
        --font-sans: -apple-system, system-ui, sans-serif;
        --font-serif: Georgia, 'Times New Roman', serif;
        --font-mono: 'SF Mono', SFMono-Regular, ui-monospace, monospace;
        --border-radius-md: 8px;
        --border-radius-lg: 12px;
        --border-radius-xl: 16px;
        color-scheme: dark;
    }
    * { box-sizing: border-box; }
    body {
        margin: 0;
        padding: 6px;
        font-family: var(--font-sans);
        background: transparent;
        color: var(--color-text-primary);
        font-size: 14px;
        line-height: 1.5;
        -webkit-text-size-adjust: none;
    }
    @keyframes _fadeIn {
        from { opacity: 0; transform: translateY(4px); }
        to { opacity: 1; transform: none; }
    }
    svg { max-width: 100%; height: auto; }
    .t { font-family: var(--font-sans); font-size: 14px; font-weight: 400; fill: var(--color-text-primary); }
    .ts { font-family: var(--font-sans); font-size: 12px; font-weight: 400; fill: var(--color-text-secondary); }
    .th { font-family: var(--font-sans); font-size: 14px; font-weight: 500; fill: var(--color-text-primary); }
    .box { fill: var(--color-background-secondary); stroke: var(--color-border-tertiary); stroke-width: 0.5; }
    .arr { stroke: var(--color-text-tertiary); stroke-width: 1.5; fill: none; }
    .leader { stroke: var(--color-border-tertiary); stroke-width: 0.5; stroke-dasharray: 4 3; fill: none; }
    .node { cursor: pointer; }
    .node:hover { opacity: 0.85; }
    .c-blue > rect, .c-blue > circle, .c-blue > ellipse { fill: #1e3a5f; stroke: rgba(96,165,250,0.4); }
    .c-blue > .t, .c-blue > .th { fill: #93c5fd; }
    .c-blue > .ts { fill: #60a5fa; }
    .c-teal > rect, .c-teal > circle, .c-teal > ellipse { fill: #134e4a; stroke: rgba(45,212,191,0.4); }
    .c-teal > .t, .c-teal > .th { fill: #5eead4; }
    .c-teal > .ts { fill: #2dd4bf; }
    .c-amber > rect, .c-amber > circle, .c-amber > ellipse { fill: #451a03; stroke: rgba(251,191,36,0.4); }
    .c-amber > .t, .c-amber > .th { fill: #fcd34d; }
    .c-amber > .ts { fill: #fbbf24; }
    .c-green > rect, .c-green > circle, .c-green > ellipse { fill: #14532d; stroke: rgba(74,222,128,0.4); }
    .c-green > .t, .c-green > .th { fill: #86efac; }
    .c-green > .ts { fill: #4ade80; }
    .c-red > rect, .c-red > circle, .c-red > ellipse { fill: #450a0a; stroke: rgba(248,113,113,0.4); }
    .c-red > .t, .c-red > .th { fill: #fca5a5; }
    .c-red > .ts { fill: #f87171; }
    .c-purple > rect, .c-purple > circle, .c-purple > ellipse { fill: #2e1065; stroke: rgba(168,85,247,0.4); }
    .c-purple > .t, .c-purple > .th { fill: #c4b5fd; }
    .c-purple > .ts { fill: #a78bfa; }
    .c-coral > rect, .c-coral > circle, .c-coral > ellipse { fill: #431407; stroke: rgba(251,146,60,0.4); }
    .c-coral > .t, .c-coral > .th { fill: #fdba74; }
    .c-coral > .ts { fill: #fb923c; }
    .c-pink > rect, .c-pink > circle, .c-pink > ellipse { fill: #500724; stroke: rgba(244,114,182,0.4); }
    .c-pink > .t, .c-pink > .th { fill: #f9a8d4; }
    .c-pink > .ts { fill: #f472b6; }
    .c-gray > rect, .c-gray > circle, .c-gray > ellipse { fill: var(--color-background-tertiary); stroke: var(--color-border-secondary); }
    .c-gray > .t, .c-gray > .th { fill: var(--color-text-primary); }
    .c-gray > .ts { fill: var(--color-text-secondary); }
    </style>
    </head><body><div id="root"></div>
    <script>
    window._morphReady = false;
    window._pending = null;
    window._lastHeight = 0;
    window._heightObserver = null;
    window._reportHeight = function() {
        var r = document.getElementById('root');
        if (!r) return;
        var next = Math.ceil(Math.max(r.offsetHeight, r.scrollHeight)) + 12;
        if (!next || Math.abs(next - window._lastHeight) < 1) return;
        window._lastHeight = next;
        window.webkit.messageHandlers.widget.postMessage({_type:'height', value: next});
    };
    window._attachHeightObserver = function() {
        var r = document.getElementById('root');
        if (!r || window._heightObserver) return;
        window._heightObserver = new ResizeObserver(function() {
            window._reportHeight();
        });
        window._heightObserver.observe(r);
    };
    window._setContent = function(html) {
        if (!window._morphReady) { window._pending = html; return; }
        var root = document.getElementById('root');
        var target = document.createElement('div');
        target.id = 'root';
        // Tolerate mid-stream HTML: an unclosed tag or half-parsed
        // attribute must not blow up morphdom. The parser is
        // forgiving enough on innerHTML; morphdom occasionally trips
        // on transient shapes, so fall back to innerHTML replacement.
        try {
            target.innerHTML = html;
            morphdom(root, target, {
                onBeforeElUpdated: function(from, to) {
                    if (from.isEqualNode(to)) return false;
                    return true;
                },
                onNodeAdded: function(node) {
                    if (node.nodeType === 1 && node.tagName !== 'STYLE' && node.tagName !== 'SCRIPT') {
                        node.style.animation = '_fadeIn 0.3s ease both';
                    }
                    return node;
                }
            });
        } catch (e) {
            try { root.innerHTML = html; } catch (_) {}
        }
        window._attachHeightObserver();
        setTimeout(function() {
            window._reportHeight();
        }, 60);
    };
    window._runScripts = function() {
        document.querySelectorAll('#root script').forEach(function(old) {
            var s = document.createElement('script');
            if (old.src) { s.src = old.src; } else { s.textContent = old.textContent; }
            old.parentNode.replaceChild(s, old);
        });
        window._attachHeightObserver();
        setTimeout(function() {
            window._reportHeight();
        }, 250);
    };
    window.sendPrompt = function(text) {
        window.webkit.messageHandlers.widget.postMessage({_type:'sendPrompt', text: text});
    };
    window.openLink = function(url) {
        window.webkit.messageHandlers.widget.postMessage({_type:'openLink', url: url});
    };
    </script>
    <script>
    \(morphdomSource)
    </script>
    <script>window._morphReady=true;if(window._pending){window._setContent(window._pending);window._pending=null;}</script>
    </body></html>
    """
    }

    /// morphdom 2.7.4 (MIT), bundled as `morphdom-umd.min.js` and inlined into
    /// the shell so widgets render offline. If the resource is missing,
    /// `_setContent` falls back to plain `innerHTML` replacement.
    private static let morphdomSource: String = {
        guard let url = Bundle.main.url(forResource: "morphdom-umd.min", withExtension: "js"),
              let source = try? String(contentsOf: url, encoding: .utf8) else {
            LLog.warn("widget", "bundled morphdom-umd.min.js missing; using innerHTML fallback")
            return ""
        }
        return source
    }()

    // MARK: - App-mode shell

    /// Builds a per-app shell that mirrors `shellHTML` but also exposes the
    /// `window.loadAppState` / `window.saveAppState` JS bridge. The shell is
    /// *not* cached — `initialAppState` differs per-app, so we build fresh on
    /// every mount.
    static func buildAppModeShellHTML(
        initialAppState: String?,
        schemaVersion: Int
    ) -> String {
        let stateLiteral: String = {
            guard let raw = initialAppState else { return "null" }
            // Escape `</` to prevent a stray `</script>` inside user JSON
            // from closing the inline script, then emit as a JS string
            // literal. The JS side calls `JSON.parse` on it.
            let escaped = raw.replacingOccurrences(of: "</", with: "<\\/")
            return "'\(escapeJS(escaped))'"
        }()
        let injection = """
        window._initialAppState = \(stateLiteral);
        window._appStateSchemaVersion = \(schemaVersion);
        window.loadAppState = function() {
            try { return window._initialAppState == null ? null : JSON.parse(window._initialAppState); }
            catch (_) { return null; }
        };
        window.saveAppState = function(obj) {
            var payload;
            try { payload = JSON.stringify(obj); } catch (_) { return false; }
            window.webkit.messageHandlers.widget.postMessage(
                { _type: 'saveAppState', value: payload, schema: window._appStateSchemaVersion });
            return true;
        };
        (function(){
            var nextId = 1;
            var pending = new Map();
            window.structuredResponse = function(req) {
                var id = 'sr-' + (nextId++);
                return new Promise(function(resolve, reject) {
                    pending.set(id, { resolve: resolve, reject: reject });
                    var fmt = (req && req.responseFormat) || null;
                    window.webkit.messageHandlers.widget.postMessage({
                        _type: 'structuredResponse',
                        requestId: id,
                        prompt: String((req && req.prompt) || ''),
                        responseFormat: fmt,
                    });
                });
            };
            window.__resolveStructuredResponse = function(id, jsonText) {
                var p = pending.get(id); if (!p) return;
                pending.delete(id);
                try { p.resolve(JSON.parse(jsonText)); }
                catch (e) { p.reject(new Error('invalid structured response JSON: ' + (e && e.message))); }
            };
            window.__rejectStructuredResponse = function(id, message) {
                var p = pending.get(id); if (!p) return;
                pending.delete(id);
                p.reject(new Error(message || 'structuredResponse failed'));
            };
        })();
        """
        // Splice the bridge declarations before the base shell's
        // `window._morphReady` so user widget scripts can call the bridge
        // synchronously during first render.
        let base = buildShellHTML(theme: ThemeStore.shared.dark)
        return base.replacingOccurrences(
            of: "window._morphReady = false;",
            with: "\(injection)\n    window._morphReady = false;"
        )
    }

    // MARK: - JS Escape

    static func escapeJS(_ s: String) -> String {
        s.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
            .replacingOccurrences(of: "</script>", with: "<\\/script>")
    }
}
