package com.akashark.agentbuddy.android.ui.conversation

import android.webkit.WebView

/**
 * Push a body-HTML payload into a loaded widget shell via
 * `window._setContent(...)`. When [runScripts] is true (finalized widgets),
 * also invokes `window._runScripts()` so user `<script>` tags inside the
 * widget execute. Mirrors iOS's `coordinator.sendContent(..., runScripts:)`.
 */
internal fun pushWidgetContent(
    webView: WebView,
    html: String,
    runScripts: Boolean,
) {
    val escaped = escapeJsString(html)
    val js = if (runScripts) {
        "window._setContent('$escaped'); window._runScripts();"
    } else {
        "window._setContent('$escaped');"
    }
    webView.evaluateJavascript(js, null)
}

/**
 * Optional app-mode state injection. When provided, [wrapWidgetHtml] splices a
 * JS block that exposes `window.loadAppState()` / `window.saveAppState(obj)`
 * to the widget script. Leaving it `null` produces the plain timeline shell.
 */
data class AppStateInjection(
    val stateJson: String?,
    val schemaVersion: UInt,
)

/**
 * Timeline widget WebView shell, byte-equivalent (modulo theme tokens and
 * bridge routing) to iOS's [WidgetWebView.buildShellHTML]. The shell is
 * loaded once per WebView; content is pushed in through
 * `window._setContent(html)` / `window._runScripts()` via `evaluateJavascript`
 * — never a full reload. Height reports back through the
 * `__LitterWidgetBridge.height` bridge; `sendPrompt` and `openLink` likewise.
 *
 * When [appState] is provided, a second inline script block is spliced before
 * `window._morphReady = false;` (matching iOS's `buildAppModeShellHTML`
 * splice point) so the saved-app JS bridge (`loadAppState`/`saveAppState`)
 * is available synchronously to user widget scripts on first render.
 */
internal fun wrapWidgetHtml(
    context: android.content.Context,
    widgetHtml: String,
    appState: AppStateInjection? = null,
): String {
    // Note: `widgetHtml` is not spliced into `<body>` anymore. Callers drive
    // content in through `window._setContent(...)` after the shell loads —
    // see `WidgetRow`/`SavedAppScreen`. We keep the parameter for API
    // compatibility and as a one-shot initial payload via `_pending` for
    // callers who prefer the declarative path.
    val body = widgetHtml.trim()
    val initialPending = if (body.isEmpty()) "null" else "'${escapeJsString(body)}'"
    val appInjection = appState?.let { buildAppStateInjection(it) } ?: ""
    // Colours follow the App's light / dark theme (see WidgetShellTheme.kt).
    val themeCss = widgetShellThemeCss().prependIndent("        ").trimStart()
    val shell = """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1.0">
        <style>
        $themeCss
        :root {
            --font-sans: -apple-system, system-ui, Roboto, sans-serif;
            --font-serif: Georgia, 'Times New Roman', serif;
            --font-mono: ui-monospace, SFMono-Regular, Menlo, monospace;
            --border-radius-md: 8px;
            --border-radius-lg: 12px;
            --border-radius-xl: 16px;
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
        .c-gray > rect, .c-gray > circle, .c-gray > ellipse { fill: var(--color-background-tertiary); stroke: var(--color-border-secondary); }
        .c-gray > .t, .c-gray > .th { fill: var(--color-text-primary); }
        .c-gray > .ts { fill: var(--color-text-secondary); }
        </style>
        </head><body><div id="root"></div>
        <script>
        // Shared message router. Defined before any other shell script (and
        // before the optional app-mode injection) so both paths can call it
        // synchronously. Routes {_type,...} payloads through whichever
        // JS-to-native bridge is present:
        //   - __LitterAppBridge: saved-app saveAppState channel.
        //   - __LitterWidgetBridge: height / sendPrompt / openLink / ready.
        //   - webkit.messageHandlers.widget: iOS fallback (no-op on Android).
        function __postWidgetMessage(msg) {
            try {
                if (msg && msg._type === 'saveAppState'
                    && window.__LitterAppBridge
                    && typeof window.__LitterAppBridge.saveAppState === 'function') {
                    window.__LitterAppBridge.saveAppState(msg.value, msg.schema|0);
                    return true;
                }
                if (msg && msg._type === 'structuredResponse'
                    && window.__LitterAppBridge
                    && typeof window.__LitterAppBridge.structuredResponse === 'function') {
                    // Android @JavascriptInterface only accepts primitives —
                    // stringify the schema object before handing it off. iOS
                    // goes through postMessage and can pass the object as-is.
                    var schemaStr;
                    try { schemaStr = JSON.stringify(msg.responseFormat); }
                    catch (_) { schemaStr = 'null'; }
                    window.__LitterAppBridge.structuredResponse(
                        String(msg.requestId || ''),
                        String(msg.prompt || ''),
                        schemaStr
                    );
                    return true;
                }
                if (window.__LitterWidgetBridge) {
                    var b = window.__LitterWidgetBridge;
                    if (msg._type === 'height' && typeof b.height === 'function') {
                        b.height(msg.value|0);
                        return true;
                    }
                    if (msg._type === 'sendPrompt' && typeof b.sendPrompt === 'function') {
                        b.sendPrompt(String(msg.text || ''));
                        return true;
                    }
                    if (msg._type === 'openLink' && typeof b.openLink === 'function') {
                        b.openLink(String(msg.url || ''));
                        return true;
                    }
                    if (msg._type === 'ready' && typeof b.ready === 'function') {
                        b.ready();
                        return true;
                    }
                }
                if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.widget) {
                    window.webkit.messageHandlers.widget.postMessage(msg);
                    return true;
                }
            } catch (_) {}
            return false;
        }
        </script>
        $appInjection
        <script>
        window._morphReady = false;
        window._pending = $initialPending;
        window._lastHeight = 0;
        window._heightObserver = null;
        window._reportHeight = function() {
            var r = document.getElementById('root');
            if (!r) return;
            var next = Math.ceil(Math.max(r.offsetHeight, r.scrollHeight)) + 12;
            if (!next || Math.abs(next - window._lastHeight) < 1) return;
            window._lastHeight = next;
            __postWidgetMessage({_type:'height', value: next});
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
            var root = document.getElementById('root');
            if (!root) return;
            if (!window._morphReady || typeof morphdom !== 'function') {
                try { root.innerHTML = html; } catch (_) {}
                window._attachHeightObserver();
                setTimeout(function() {
                    window._reportHeight();
                }, 60);
                return;
            }
            var target = document.createElement('div');
            target.id = 'root';
            // Tolerate mid-stream HTML: unclosed tags or half-parsed
            // attributes must not blow up morphdom. Fall back to innerHTML
            // replacement; the next delta that closes the tag will
            // re-diff cleanly.
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
            __postWidgetMessage({_type:'sendPrompt', text: text});
        };
        window.openLink = function(url) {
            __postWidgetMessage({_type:'openLink', url: url});
        };
        </script>
        <script>$MORPHDOM_PLACEHOLDER</script>
        <script>window._morphReady=true;if(window._pending){window._setContent(window._pending);window._pending=null;}__postWidgetMessage({_type:'ready'});</script>
        </body></html>
    """.trimIndent()
    // Splice at the last occurrence: the placeholder sits after every
    // caller-supplied fragment, so user content can never be replaced.
    val at = shell.lastIndexOf(MORPHDOM_PLACEHOLDER)
    return shell.replaceRange(at, at + MORPHDOM_PLACEHOLDER.length, morphdomJs(context))
}

// morphdom 2.7.4 (MIT, see assets/widget/morphdom-LICENSE.txt) is bundled so
// widgets render offline; it is inlined into the shell because the shell is
// loaded under an https base URL that cannot reference file:///android_asset.
private const val MORPHDOM_ASSET_PATH = "widget/morphdom-umd.min.js"
private const val MORPHDOM_PLACEHOLDER = "/*__MORPHDOM_UMD__*/"

@Volatile
private var cachedMorphdomJs: String? = null

private fun morphdomJs(context: android.content.Context): String =
    cachedMorphdomJs ?: context.applicationContext.assets.open(MORPHDOM_ASSET_PATH)
        .bufferedReader()
        .use { it.readText() }
        .also { cachedMorphdomJs = it }

/**
 * JS block providing the `loadAppState` / `saveAppState` bridge consumed by
 * saved-app-mode widgets. Spliced into the shell before
 * `window._morphReady = false;` (mirrors iOS's `buildAppModeShellHTML`) so
 * user widget scripts can call the bridge synchronously during first render.
 *
 * The raw state JSON is sanitized by replacing `</` with `<\/` before being
 * spliced into the inline script to prevent a stray `</script>` sequence in
 * user data from closing our tag.
 */
private fun buildAppStateInjection(appState: AppStateInjection): String {
    val escapedJson = appState.stateJson
        ?.let { org.json.JSONObject.quote(it) }
        ?.replace("</", "<\\/")
        ?: "null"
    val schema = appState.schemaVersion.toLong()
    return """
        <script>
          window._initialAppState = $escapedJson;
          window._appStateSchemaVersion = $schema;
          window.loadAppState = function() {
            try {
              if (window._initialAppState == null) return null;
              return JSON.parse(window._initialAppState);
            } catch (_) { return null; }
          };
          window.saveAppState = function(obj) {
            var payload;
            try { payload = JSON.stringify(obj); } catch (_) { return false; }
            return __postWidgetMessage({
              _type: 'saveAppState',
              value: payload,
              schema: window._appStateSchemaVersion,
            });
          };
          (function(){
            var nextId = 1;
            var pending = new Map();
            window.structuredResponse = function(req) {
              var id = 'sr-' + (nextId++);
              return new Promise(function(resolve, reject) {
                pending.set(id, { resolve: resolve, reject: reject });
                var fmt = (req && req.responseFormat) || null;
                __postWidgetMessage({
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
        </script>
    """.trimIndent()
}

/**
 * Escape a string so it can be embedded as a JS single-quoted literal inside
 * `evaluateJavascript("window._setContent('...'); ...")`. Matches iOS's
 * `WidgetWebView.escapeJS` character-for-character.
 */
internal fun escapeJsString(s: String): String =
    s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("</script>", "<\\/script>")
