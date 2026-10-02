# Android QA Matrix

## Scope

This matrix covers transport reliability and startup-path parity for Android websocket + bridge flows.

## Automated Unit Tests

There is a single app build (no product flavors; `ENABLE_ON_DEVICE_BRIDGE=true`,
`RUNTIME_STARTUP_MODE="hybrid"`). Run the JVM unit tests from `apps/android`:

```bash
./gradlew :app:testDebugUnitTest
```

Current tests (`app/src/test/java/com/akashark/agentbuddy/android/`):

- `RuntimeFlavorConfigTest` — startup mode / transport `BuildConfig` parity
- `SavedServerTransportTest` — saved-server direct vs SSH transport choice and legacy migration
- `HomeDashboardSupportTests`, `SessionsDerivationTests` — home/session workspace labels and cwd normalization
- `auth/ChatGPTOAuthLoopbackServerTest` — ChatGPT OAuth loopback redirect server
- `push/TurnCompletionPushTest` — host completion push payload parsing, notification tag (= Worker collapse key), foreground suppression, push registration gating, unsupported-host hint gating
- `state/AppComposerPayloadTest` — composer payload → `turn/start` params
- `state/RealtimeWebRtcTransportTest`, `state/VoiceDynamicToolSpecsTest` — realtime voice transport and dynamic tool specs
- `state/SnapshotExtensionsTest` — snapshot display helpers (model labels)
- `state/SshHostKeyMismatchTest` — typed SSH host-key prompts (changed key, unreadable saved key) read from Rust errors
- `ui/AgentBuddyAppearanceModeTest`, `ui/ConversationTextSizingTest` — appearance mode and text sizing
- `ui/AgentBuddyResolvedThemeTest` — Mint semantic roles from `agentbuddy.*` keys, iOS-equivalent fallbacks for other themes, `#RRGGBBAA` alpha stripping
- `ui/homeshell/HomeTaskPresentationTest`, `ui/homeshell/HomeShellSummariesTest` — home task state projection, section partitioning, summaries
- `ui/settings/ThemePickerSectionsTest` — theme picker 推荐 / 全部主题 grouping
- `ui/approvals/ApprovalCoordinatorTest`, `ui/approvals/ApprovalPresentationTest` — approval submit bookkeeping (one decision per request, retryable failures, per-thread outcomes, RESOLVED_ELSEWHERE via reconcile), stack paging, approval wording
- `ui/conversation/ComposerStateTest` — composer states (idle / running stop + 排队 / stopping / disconnected / creating), send gate, failed-draft restore
- `ui/conversation/ConversationHeaderModelTest` — header title, connection state and 「搭档 · 主机」 subtitle
- `ui/conversation/BundledMorphdomAssetTest` — bundled morphdom asset for the widget WebView shell
- `ui/conversation/ComposerBarSlashCommandTest` — composer slash commands
- `ui/conversation/MathMarkdownTest` — math Markdown rendering
- `ui/conversation/ResponseSubmissionErrorsTest` — turn submission error messages

## Manual Matrix

### Google Play onboarding / API 36 (2026-10-01)

- Compile SDK and target SDK are 36; `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass locally.
- Play release/preflight script tests and release workflow `actionlint` checks pass.
- Signed API 36 release bundle builds locally, including release lint. CameraX is updated to 1.4.2 because its previous image-processing native library used 4 KB LOAD alignment. All 12 native libraries in the final arm64 AAB have >=16 KB LOAD alignment; Android unit tests pass again after the dependency update.
- Play Console accepted the replacement AAB (1.5.0 / versionCode 12, target SDK 36, arm64-v8a), saved in an internal-test draft. Release preview has no blocking errors; remaining warnings concern testers and optional mapping/native symbols. Version 11 exposed an unused Billing 7 dependency, now removed; the merged release manifest has no billing permission or billing-version metadata. Play App Signing is enabled. Service-account app permissions, CI API publishing and internal tester installation remain pending.
- Before tester rollout, smoke-test Android 16 launch, system back navigation, keyboard/insets, remote pairing, notifications and voice on device. JVM tests do not validate target-SDK behavior changes.
- This is Android distribution configuration; iOS runtime/parity behavior is unchanged.

| Area | Expected |
|---|---|
| App launch | App launches and can start a local bridge-backed session |
| Connect local/on-device | Success (`ServerConfig.local`) |
| Connect remote server | Success |
| SSH-discovered remote server | Prompts for SSH credentials, connects through SSH port forwarding, and never attempts `ws://host:22` directly |
| Local transport drop | Reconnect and one-time reinitialize before next non-initialize RPC |
| Remote transport drop | Reconnect behavior via Rust `AppStore` updates and resumed RPC notifications |
| Thread start/resume fallback sandbox | `workspace-write` with `danger-full-access` fallback when linux sandbox missing |
| Thread turn pagination (v0.125+ remote) | Conversation opens with last 5 turns; "Load earlier messages" button fetches older 5-turn pages via `thread/turns/list` |
| Thread turn pagination fallback (v0.124 remote) | Capability flips off via response inspection; embedded turns load fully; "Load earlier" button hidden |

## Turn Completion Notifications (host push, design `docs/superpowers/specs/2026-09-24-host-push-notifications-design.md`)

Rust `PushManager` subscribes on `TurnStarted` for alleycat hosts advertising `push.v1`; Android only supplies the FCM token and displays / routes the notification.

| Area | Expected (Android) |
|---|---|
| Registration | FCM token goes to `AppClient.setPushRegistration` only when notifications are enabled (POST_NOTIFICATIONS on 33+, app notifications on, `turn_complete` channel not blocked); re-evaluated on resume and after the permission prompt |
| Permission denied | Registration is `null` (Rust revokes); chat and turns work normally; no notification |
| Token rotation | `onNewToken` persists the token and, if the app process is live, hands it to Rust immediately |
| Background completion | System shows the Worker notification on channel `turn_complete` (「任务完成通知」, high importance); title 「任务已完成」/「任务未完成」 |
| Foreground, any screen | No notification or sound, including other threads/hosts and alerts without routing keys; matches iOS |
| Tap (warm or cold start) | MainActivity reads `agentbuddy.notification.serverId` / `threadId` extras, waits up to 20 s for that host to connect, then loads and authoritatively refreshes the thread; nothing is inferred from the push |
| Same threadId on two hosts | Separate notifications and routing (tag and routing include the host) |
| Debug alert (`/debug/push` alert) | Suppressed in the foreground; displayed in the background, tag `agentbuddy-debug` when no routing keys |
| Debug background (`/debug/push` background) | No UI; logcat `AgentBuddyFCM: debug background push received` |
| Legacy host without `push.v1` | No subscription and no silent keepalive fallback; while notifications are enabled the conversation screen shows 「该主机版本不支持完成通知，升级桌面 App 后可用」 above the composer (`AppClient.hostPushSupport` = `UNSUPPORTED_HOST`), dismissible per server (SharedPreferences `agentbuddy_push`) |

## Terminal UX Matrix

The terminal screen renders through Ghostty on both platforms; this section
tracks parity between iOS (UIKit + Metal) and Android (Compose + SurfaceView).

| Area | iOS | Android |
|---|---|---|
| Full-screen surface | No bottom composer; the Ghostty surface fills the body | Same |
| Accessory bar | Esc/Tab/Ctrl/arrows/Paste/Clear/Send-to-AI dock above the keyboard via `inputAccessoryView` | Compose row anchored above the IME using `imePadding` |
| Tap-to-toggle keyboard | Single tap (no selection, no link) toggles the keyboard | Same |
| Long-press selection | Long-press seeds word selection; drag extends; handles paint via `TerminalSelectionOverlayView` | Long-press seeds word selection; drag extends; handles painted by a sibling Compose Canvas |
| Edit menu (Copy / Paste / Select All) | `UIEditMenuInteraction` anchored at the selection union rect | Compose floating action menu above the selection |
| Pinch-to-zoom font | `UIPinchGestureRecognizer` clamps to 10–24 pt and re-grids on settle | `ScaleGestureDetector` updates `TerminalConfigPrefs.fontSize` live and re-grids on scale-end |
| BEL haptic | `UIImpactFeedbackGenerator(.medium)` + system sound, throttled 250 ms | `performHapticFeedback(LONG_PRESS, IGNORE_VIEW_SETTING)`, throttled 250 ms |
| OSC8 hyperlink tap | Detected through Rust `linkAtPoint`; opens via `UIApplication.shared.open` | Detected through Rust `linkAtPoint`; opens via `Intent.ACTION_VIEW` |
| Cell-grid math | Driven by Ghostty `surfaceMetrics`; falls back to font-size-aware estimate on first frame | Same path via `nativeSurfaceSize` |
| Resize on rotation / keyboard show-hide | `layoutSubviews` plus `UIResponder.keyboardWillChangeFrame` triggers | `onSizeChanged` re-fires through Compose's `imePadding` insets |
| Mouse-tracking apps (vim / htop) | Single-finger drag forwards to Ghostty when `mouseCaptured` | Same |
| Alleycat remote host | Discovery toolbar QR button opens `AlleycatAddServerSheet`; CameraX + ML Kit scan parses the Alleycat payload via `AlleycatBridge.parsePairPayload`; debug builds expose paste-JSON path; after token-authenticated pairing the sheet calls `serverBridge.listAlleycatAgents`, lets the user choose Codex/Pi/OpenCode, connects with `serverBridge.connectRemoteOverAlleycat`, and persists the token through `AlleycatCredentialStore`; `SavedServerStore.rememberAlleycat` writes `{node_id, relay?, agent}` records, reconnect attaches the encrypted-store token directly, and legacy Alleycat records require a new QR scan. | Same |

## Plugin `@`-mention parity (follow-up)

iOS ships `@plugin` mentions in the composer: typing `@` now lists installed
Codex plugins above the file results, selecting one inserts an `@<name>`
chip and emits an `AppUserInput.Mention { name, path }` item on
`turn/start`. Path encoding lives in shared Rust (`PluginSummary` UniFFI
record + `list_plugins` on `AppClient`).

Android does not have an `@`-trigger composer popup yet (no inline
`@<file>` autocomplete either), so plugin mentions are a follow-up. The
shared Rust client and the `AppUserInput.Mention` variant are already
wired through the Kotlin bindings, so the platform side just needs a
composer popup, chip row, and send-time append. Track this alongside the
broader composer autocomplete work.

## Suggested Smoke Steps

1. Debug build: connect local default server, start thread, send turn, toggle network off/on, send another turn.
2. Debug build: kill local bridge process (or force stop app), relaunch, confirm initialize and thread list recover.
3. Debug build: connect a remote server and run thread/list + turn/start.
4. Verify account read/login status refresh still updates UI after reconnect.

## Thinking-indicator Minigame (iOS + Android)

Gated by Settings → Experimental → "Thinking minigame" (off by default on
both platforms). The shimmering "Thinking..." indicator becomes tappable
while the assistant is generating; tapping it spins up an ephemeral thread
on `gpt-5.3-codex-spark` (low reasoning, fast tier) and renders the
returned `show_widget` HTML in a bottom-40% overlay that hides the
composer.

| Check | iOS | Android |
|---|---|---|
| Indicator is non-interactive when flag off | "Thinking" shimmer has no tap effect | Shimmer has no clickable ripple |
| Tap while thinking opens overlay immediately | Slides in with skeleton | Slides in with skeleton |
| Skeleton swaps to rendered widget on completion | WidgetWebView (no zoom, theme-injected) | MinigameWebView (no zoom, themed) |
| Composer is hidden while overlay is up | `ConversationBottomChrome` returns `EmptyView` from `safeAreaInset` | Composer `Column` is omitted |
| Last message is not occluded | safeAreaInset reserves overlay height in scroll inset | LazyColumn has trailing spacer / overlay sits over nav bar inset |
| Close (X) restores composer + idles overlay | `dismissMinigame()` cancels in-flight task | `dismissMinigame()` cancels coroutine |
| Repeat tap on a fresh assistant turn generates a new game | New ephemeral thread per request | New ephemeral thread per request |
| Bridge globals stubbed in minigame mode | `WKUserScript` at `.atDocumentStart` no-ops `sendPrompt`/`saveAppState`/`loadAppState`/`structuredResponse`, and the matching `WKScriptMessageHandler` registrations are skipped | `evaluateJavascript` in `onPageStarted` injects stubs; the matching `@JavascriptInterface` registrations are skipped (`WidgetBridge` for openLink/height/ready only) |
| Light + dark mode rendering | Widget uses host CSS variables that adapt automatically | Same |
| Minigame is NOT saved as a regular widget | Waiter intercepts `show_widget` so `auto_upsert_saved_app` is skipped, and `start_minigame` does not call `saved_app_upsert` itself | Same — single shared Rust path |
| Ephemeral thread is torn down | `thread/archive` after waiter resolves or times out | Same |
| Generation timeout (~30s) | Overlay shows failure card with "Try again" | Overlay shows failure card with "Try again" |

## Settings — Server Connection Editor (iOS + Android)

Tapping a saved server row in Settings opens the inline editor. Local servers are
name-only; alleycat-paired servers are name-only; everything else allows
mode + host/port/wake-MAC + URL editing. Save persists, Save & Reconnect
disconnects and re-establishes the chosen transport.

| Check | iOS | Android |
|---|---|---|
| Tap saved server row opens editor | `SettingsServerSheet.edit` opens `SettingsServerConnectionEditor` form sheet | Tap or row-menu "Edit" opens `ServerEditSheet` ModalBottomSheet |
| Local server: only name editable | Editor displays "managed automatically" copy; only Save & Restart action shown | Same — `ServerEditSheet` shows the same copy and uses "Save & Restart" |
| Alleycat-paired server: only name editable | Editor shows paired-pairing-metadata copy; mode picker hidden | Same — mode picker hidden, message visible |
| Switch to Direct Codex + Save & Reconnect | Persists, disconnects, calls `serverBridge.connectRemoteServer` | Same — `reconnectController.reconnectServer` reconnects via persisted record |
| Switch to WebSocket + Save & Reconnect | Persists, disconnects, calls `serverBridge.connectRemoteUrlServer` with `ws://` or `wss://` | Same — record stores `websocketURL`; reconnect dispatches via Rust `ReconnectController` |
| Switch to SSH + Save & Reconnect | Persists, dismisses editor, opens `SSHLoginSheet`; connect uses `serverBridge.startRemoteOverSshConnect` | Persists, dismisses editor, opens shared `SSHLoginDialog`; connect uses `serverBridge.startRemoteOverSshConnect` |
| Validation errors surface inline | Alert "Invalid Server" with localized reason, dismiss returns to editor | Same — `AlertDialog` with reason; dismiss returns to editor |
| Save (no reconnect) | Persists `SavedServerStore` + calls `store.renameServer`, leaves connection intact | Same |
| Remove server | `SavedServerStore.remove` + closes SSH session + disconnects bridge | Same |
| SSH host key changed / saved key unreadable (Discovery, Settings reconnect, Terminal) | `sshHostKeyChangeAlert` offers "Trust New Key" (pins the shown fingerprint) or "Forget Saved Host Key" (unpins), then retries | `SshHostKeyChangedDialog` offers 「信任新密钥」 or 「忘记已保存的主机密钥」, then retries |
| Settings SSH reconnect fails for another reason | "Server Update Failed" alert shows the Rust `terminalMessage` | SSH reconnect error dialog shows the Rust `terminalMessage` |

## Sidebar + Picker Parity Checklist (iOS + Android)

### Session Sidebar

- Sidebar stays unmounted while closed; local UI controls persist when reopened.
- Search + server filter + forks filter produce stable grouping and lineage chips.
- Opening/closing sidebar does not trigger excessive recomposition/signpost churn in idle state.

### Thread List Consistency

- Refresh (`thread/list`) prunes non-authoritative placeholder threads unless they are currently active.
- Notification-only placeholder rows disappear on next refresh once inactive.
- No regressions in thread switching, forking, or session search after placeholder pruning.

### Directory Picker

- Primary action: one-tap `Continue in <last folder>` appears when recents exist.
- Top controls remain visible while list scrolls: connected server chip/status + search.
- Breadcrumb + `Up one level` navigation always reflects current path.
- Bottom CTA is sticky and mirrors path state: `Select <path>` (or disabled helper text).
- Error state exposes both `Retry` and `Change server`.
- `Clear recent directories` requires destructive confirmation.
- Back behavior parity:
  - Android: `Back` navigates up before dismissing sheet.
- iOS: dismiss is blocked while not at root; cancel navigates up first.

### Appearance

- Chat wallpaper can be chosen from the photo library, persists across relaunch, and can be removed from Settings.
- Conversation screen and appearance preview both render the selected wallpaper instead of the fallback theme gradient.

### Conversation Selection

- Settled assistant markdown supports long-press selection/copy.
- Reasoning text supports long-press selection/copy.
- Command output supports selection/copy and still scrolls vertically.
- Error text supports selection/copy.
- Code blocks support selection/copy and still scroll horizontally.
- Markdown links remain tappable after selection support changes.

## Home shell — 任务 / 项目 / 主机 (Mint, iOS parity)

Replaces the old zoomable home dashboard (removed in the Mint rebuild; see
`docs/design/android-mint-ui-migration.md`).

| Area | Expected |
|---|---|
| Bottom navigation | Material 3 `NavigationBar` with 任务 / 项目 / 主机; selected tab survives rotation, process death and a round trip into a conversation. Back from 项目 / 主机 returns to 任务 first. Labels stay within the chrome cap at large text |
| Composer pill | 「有个想法？交给搭子…」 above the bar on 任务 and 项目 (not 主机); tap opens the new-task sheet; voice button only with `realtime_voice` |
| 任务 sections | 「需要你处理」 (approval / input), 「正在进行」 (running + stopping, brand cards), 「接着上次」 (rest, rows); state projection stopping > approval > input > running > completed/idle, MCP elicitations excluded, connection problems never shown as failures |
| Task actions | Visible 「…」 menu and the same long-press menu: 回复, 停止 (running only), 分叉 (disabled while running), 固定/取消固定, 隐藏, 删除 (confirm). Swipe right = quick reply, left = hide, solid light-palette fills with white labels; TalkBack custom actions for both |
| 「正在停止…」 on home | Set on 停止; clears on turn end, turn switch or host disconnect |
| Header | Host filter 「N 台主机在线」 (全部主机 / each host / 管理主机), settings gear, search, 「…」 (全部任务, Saved Apps when present, 终端 with `terminal`) |
| Search | 250 ms debounce, server-side `listThreads`, runtime pills, pull-to-refresh with force repair, fork clusters; tapping a result toggles pin (unchanged) |
| Task details | Settings 「首页显示任务详情」 (`dashboardZoomStep` ≥ 3) adds model chip, latest step, activity summary, fork 「分叉 i/n」 |
| Empty states | No host → 扫码连接 (QR sheet directly) + 其他连接方式; no tasks → 开始任务; search without results → clear search |
| 项目 | Projects from Rust `deriveProjects` (last used time in ms → correct 「N 天前」), task counts keyed by `projectIdFor`; hero card with 「最近任务」 (newest three, tap opens the task; 「查看全部 N 个任务」 opens the task list scoped to that project (titled with its name, no host filter); 「主机离线…」 when its host is down) and 新建任务; other projects rows with host; tapping one makes it the hero and scrolls back up, and the pick survives its host being offline or reconnecting; 「+」 opens the directory picker in project mode |
| 主机 | Card per host with connection pill (text + dot), mode subtitle, runtime chips, 「在这台主机开始任务」, 「…」 (重新连接, 重启服务, 重命名 (remote), 编辑连接, 移除 (confirm), 终端 with flag); 「扫码连接」 and 「+」 add-host entry; "This Device" rendered as 「本机」 |
| New-task sheet | Mint sheet with project / host / model chips, attachments, dictation, expanded editor; progress + double-submit guard; draft kept on failure; opens the new task after creating |
| 全部任务 | Reachable from the 「…」 menu and `/resume`; back returns to the list; rows show the same status as home |
| Home hydration | Pinned threads still auto-resume through `externalResumeThread` so cards update without opening the thread |
| SavedProjectStore | Last-selected host + project persist across restart via Rust `HomeSelection` |

## Mint visual QA (gallery, dark, large text, reduced motion)

Default dark palette (2026-09-29): neutral graphite surfaces and gray-white text,
muted current-task cards, mint actions, and surface-aware brand chips on both
platforms. Palette and measured contrast pairs: [Mint dark palette](../../../docs/design/mint-dark-palette.md).

| Check | How |
|---|---|
| DEBUG state gallery | `adb shell am force-stop com.akashark.agentbuddy.android && adb shell am start -n com.akashark.agentbuddy.android/.MainActivity --es mint_gallery <page> [--ez mint_dark true]`; 50 fixture pages (`ui/gallery/MintGalleryPages.kt`); never starts the runtime or writes theme prefs |
| Dark mode | Every gallery page in light and dark; semantic roles only |
| Large text | `adb shell settings put system font_scale 2.0`: home, conversation, approvals, composer — bottom bar / button rows / headers capped at 1.3×, body keeps growing, approval buttons stack, approval card ≤ 45% of the screen with inner scroll |
| Minimum sizes | No text below 12sp (also at the smallest app text size); touch targets ≥ 48dp |
| Reduced motion | Developer options → animator duration scale off: status pulses, shimmers, streaming cursor, splash, minigame skeleton and follow-scroll animations stop |
| Theme defaults | Fresh install → Mint + system font; an existing theme / font / text size / wallpaper choice is kept |

## Tool Call Card Parity Matrix (iOS + Android)

Renderer contract for this release:

- default collapsed for tool cards, except `failed` cards (default expanded)
- header order: icon, summary/title, spacer, status chip, optional duration chip, chevron
- section order: metadata KV, payload sections (`Command/Arguments/Result/Output/Action`), auxiliary sections (`Prompt/Targets/Progress`)
- parse miss fallback: legacy markdown rendering unchanged

| Tool kind | Summary rule | Status chip | Expected sections |
|---|---|---|---|
| Command Execution | stripped command + status/duration suffix | `inProgress`/`completed`/`failed`/`unknown` | Metadata, Command, Output (if present), Progress (if present) |
| Command Output | output label fallback (`Command Output`) when no command | usually `unknown` | Output text/code |
| File Change | first basename + `+N files` | normalized from `Status:` | Metadata, repeated `Change N` metadata + diff/text content |
| File Diff | first path basename when available, else `File Diff` | usually `unknown` | Diff panel |
| MCP Tool Call | `Tool:` value + status suffix/check | normalized from `Status:` | Metadata, Arguments/Result, Error/Progress as available |
| MCP Tool Progress | tool/status fallback or title | usually `unknown` unless merged into MCP call | Progress timeline text |
| Web Search | `Query:` value | usually `unknown` | Metadata, Action JSON |
| Collaboration | `Tool:` value fallback | normalized from `Status:` | Metadata, Prompt text, Targets list |
| Image View | basename from `Path:` | usually `unknown` | Metadata (`Path`) |

Status normalization parity:

- `inProgress`, `in progress`, `running`, `pending`, `started` -> in progress (amber)
- `completed`, `complete`, `success`, `ok`, `done` -> completed (green)
- `failed`, `failure`, `error`, `denied`, `cancelled`, `aborted` -> failed (red)
- anything else/missing -> unknown (neutral)

## Saved Apps (Generative UI persistent apps)

Generative UI is permanent (no flag). Local-server threads register `show_widget` / `visualize_read_me`; remote-server threads do not. Saved apps are created automatically whenever a local-server model finalizes a `show_widget` with an `app_id` slug — there is no manual "Save as App" button.

| Area | Check |
|---|---|
| Bootstrap | `AppClient.setSavedAppsDirectory(SavedAppsDirectory.path(context))` (`{filesDir}/Apps`) is called once in `AppModel.init`, before any thread starts. Without this, the Rust `show_widget` finalize hook is a silent no-op. |
| Auto-upsert | When the model finalizes a `show_widget` with `app_id = "fitness-tracker"` on a local-server thread, the Rust hook calls `saved_app_upsert(directory, originThreadId, appId, title, html, w, h, schema)` and writes to `{filesDir}/Apps/saved_apps.json` + `html/<uuid>.html` (state in `state/<uuid>.json`). No Kotlin-initiated promote call is needed. |
| Saved-as chip | Finalized `WidgetRow` whose `HydratedWidgetData.appId` is non-null renders a compact "Saved as `<slug>`" chip below the WebView (11sp mono, accent slug). Tap resolves `SavedAppsStore.appForSlug(slug, threadId)` to a UUID and pushes `Route.SavedApp`. Chip is absent when `appId == null` or the widget isn't finalized. |
| Home-row takeover | `HomeDashboardScreen` keeps a `savedAppsByThread` map keyed by `originThreadId`, reloaded via `SavedAppsStore.reload` on every snapshot tick (MVP coarse reactivity; R3 will supply a `SavedAppsChanged` stream). When a session's threadId has entries, its row renders `HomeAppTakeoverRow` (monogram + title + slug subtitle + "+N more" when there are siblings) instead of `SessionCanvasRow`. Tap navigates to `Route.SavedApp(mostRecent.id)`. Swipe-to-hide on the session still works. |
| Apps list entry | Settings sheet "Apps → Saved Apps" row is always visible (no flag gate). Pushes `Route.Apps`. |
| Apps list | `AppsListScreen` renders apps newest-updated-first: monogram tile + title + relative timestamp. Swipe-to-dismiss cascades `savedAppDelete`. Empty state explains that saved apps are created automatically. |
| Detail relaunch | Tapping a row (or a Saved-as chip, or a home-row takeover) pushes `Route.SavedApp(uuid)`. `SavedAppScreen` calls `savedAppGet(dir, uuid)` on enter, hydrates the WebView with `wrapWidgetHtml(html, AppStateInjection(stateJson, schemaVersion))`, and registers `__LitterAppBridge` via `addJavascriptInterface`. |
| State persistence | `window.saveAppState(obj)` flows through the bridge into `SavedAppsStore.saveState`, 250 ms trailing-edge debounced per `appId`, landing in `savedAppSaveState`. `SavedAppException.StateTooLarge` logs + swallows. |
| Structured response | `window.structuredResponse({prompt, responseFormat})` returns a Promise that resolves with parsed JSON. Routes through `__LitterAppBridge.structuredResponse(requestId, prompt, schemaJson)` → `AppClient.structuredResponse` on an ephemeral hidden thread (`ThreadStartParams.ephemeral = true`). First call per view session creates the hidden thread; subsequent calls reuse it via `remember(appId) { mutableStateOf<String?>(null) }`. Reply flows back via `webView.evaluateJavascript("window.__resolveStructuredResponse(...)")`. Navigating away + returning resets the cache so a fresh ephemeral thread is created. The hidden thread never appears in the thread list (ephemeral threads are absent from `thread/list`). |
| Cold-launch persistence | Increment a counter in a saved app, `am force-stop`, relaunch, reopen the app. `loadAppState()` pulls `window._initialAppState` from the seeded state JSON — count survives. |
| Update flow | "Update" in the top bar opens `SavedAppUpdateOverlay`. Submit calls `SavedAppsStore.requestUpdate(...)` → `AppClient.updateSavedApp(...)`. WebView dims + shimmer overlay. On success: dismiss + detail re-fetch (state preserved because `replace_html` doesn't touch `state/<id>.json`). Failure: retry inline + toast. |
| Origin server routing | Update RPC prefers `originThreadId`'s server → active thread's server → any local server → any connected. No connected server → clear error message. |
| View Conversation | Top bar has a chat-bubble icon (`Icons.AutoMirrored.Filled.Chat`) that pushes `Route.Conversation(originThreadKey)`. Only rendered when `originThreadId` still resolves to a `ThreadKey` in the current snapshot — gone otherwise. |
| Rename / delete | Top bar title tap → rename dialog → `savedAppRename`. Overflow "Delete" → destructive confirmation → `savedAppDelete` → pop back to list. |
| Same slug in two threads | Model emitting `app_id = "fitness-tracker"` in two different origin threads creates two independent saved apps (distinct UUIDs, separate state files). The Apps list shows both; home-row takeover on each thread points at its own. |
| Regression: timeline widgets with no slug | A `show_widget` call that omits `app_id` (or is pre-R2) renders with the baseline `wrapWidgetHtml(html)` shell, does not trigger auto-save, and shows no Saved-as chip. |
| Regression: thread delete | Deleting an `originThreadId` thread does not affect saved apps; the `View Conversation` button becomes hidden for those apps but update/state flows still work. |

## Timeline WebView shell (SW-A0)

The timeline widget WebView shell (`wrapWidgetHtml` in `ConversationTimeline.kt`) is kept at parity with iOS's `WidgetWebView.buildShellHTML`. The shell is loaded **once** per WebView; subsequent content changes push through `window._setContent(html)` / `window._runScripts()` via `evaluateJavascript`, never a full page reload. This is the foundation for SW-A1 (partial widget-HTML streaming) and the fix for the prior "widgets pop in" behavior.

| Area | Check |
|---|---|
| Shell parity | `wrapWidgetHtml` emits the full iOS-parity document: theme vars in `:root`, `@keyframes _fadeIn` + `onNodeAdded` fade-in hook, morphdom CDN (`morphdom@2.7.4/dist/morphdom-umd.min.js`), and the `_morphReady` / `_pending` / `_setContent` / `_runScripts` / `_reportHeight` / `_attachHeightObserver` / `sendPrompt` / `openLink` JS. App-mode injection splices before `window._morphReady = false;`. |
| Bridge | JS posts `{_type, ...}` messages through `__postWidgetMessage`, which routes: `saveAppState` → `__LitterAppBridge`, `height`/`sendPrompt`/`openLink`/`ready` → `__LitterWidgetBridge`, with the iOS `webkit.messageHandlers.widget` fallback (no-op on Android). Both bridges coexist on the saved-app WebView. |
| Shell loads once | `AndroidView.factory` calls `loadDataWithBaseURL(..., wrapWidgetHtml(""), ...)` exactly once. `WebViewClient.onPageFinished` flips a per-WebView `widget_webview_shell_ready` tag to `true` and flushes any buffered HTML through `pushWidgetContent`. |
| Content push | Subsequent HTML changes in `AndroidView.update` call `pushWidgetContent(webView, html, runScripts = isFinalized)`, which runs `webView.evaluateJavascript("window._setContent('${escaped}'); window._runScripts();", null)`. `escapeJsString` matches iOS's `escapeJS` (backslash, single-quote, newline, CR, `</script>` → `<\/script>`). |
| Pre-ready queue | HTML changes that arrive before `onPageFinished` are stored on the WebView via the `widget_webview_pending_html` tag; `onPageFinished` flushes them. No content is dropped. |
| Dynamic height | `__LitterWidgetBridge.height(px)` posts the reported height through a main-thread `Handler`, clamped to `[200dp, 720dp]`. Compose's `mutableStateOf<Dp>(initial)` drives the WebView's `.height(widgetHeight)` modifier and animates smoothly as the widget grows/shrinks. Initial seed is the declared `data.height`. |
| sendPrompt | A widget button that calls `window.sendPrompt(text)` routes through `__LitterWidgetBridge.sendPrompt` → `onWidgetPrompt` callback in `WidgetRow` → `ConversationScreen` builds an `AppComposerPayload` and calls `appModel.startTurn(threadKey, payload)` — parity with iOS's `sendWidgetPrompt` which also submits a turn immediately. |
| openLink | Widget call to `window.openLink(url)` routes through the bridge to an `Intent.ACTION_VIEW` in the host Activity, opening the URL in the default browser. |
| Save-as-App bubble | `show_widget` finalize → auto-save (Rust-side) → Saved-as chip appears under the WebView. Shell lifecycle unchanged. |
| Saved app detail | `SavedAppScreen` uses the same shell through `wrapWidgetHtml("", AppStateInjection(...))` loaded once, then pushes `payload.widgetHtml` via `pushWidgetContent`. `loadAppState`/`saveAppState` still work. State persists across cold relaunch. |
| Regression: finalized timeline widget | An existing finalized `show_widget` renders identically to pre-refactor — fade-in animation, tap routing, state persistence of saved-app mode all preserved. |

## Realtime Voice (WebRTC transport)

Replaces the prior WebSocket + base64-PCM audio pump with a platform-native WebRTC peer connection on both iOS and Android. Upstream `thread/realtime/start` receives a client offer SDP via `AppRealtimeStartTransport.Webrtc`; the app-server responds with an answer SDP via `ThreadRealtimeSdpNotification`. All other realtime notifications (transcripts, item-added, handoff, closed, error) continue over the existing RPC WebSocket — only the audio byte path moved to the peer connection.

| Area | iOS | Android |
|---|---|---|
| Start request carries Webrtc transport | `AppStartRealtimeSessionRequest.transport == .webrtc(sdp:)` with a non-empty offer SDP (log at session start) | Same — `AppRealtimeStartTransport.Webrtc(sdp)` |
| Answer SDP applied | `AppStoreUpdateRecord.realtimeSdp` → `RealtimeWebRtcSession.applyAnswer(_:)` → `setRemoteDescription` succeeds | `AppStoreUpdateRecord.RealtimeSdp` → `RealtimeWebRtcSession.applyAnswer` → `setRemoteDescription` succeeds |
| Peer connection reaches connected state | `RTCPeerConnectionState.connected` observed via delegate | `PeerConnection.IceConnectionState.CONNECTED` observed |
| Bidirectional audio | Assistant voice plays back; mic input produces responses | Same |
| Transcript deltas (RPC path) | `ThreadRealtimeTranscriptDelta`/`Done` notifications still render | Same |
| Client-controlled handoff during voice | `HandoffManager` receives `HandoffRequested`, `resolveHandoff` / `finalizeHandoff` round-trip completes | Same |
| Dynamic tool call during voice | Argument deltas stream via RPC `ConversationItemAdded`; tool output returns via `resolveHandoff` | Same |
| Session stop | `RealtimeWebRtcSession.stop()` closes peer + data channel, deactivates `RTCAudioSession` | `stop()` disposes peer, restores audio mode, abandons audio focus |
| Session cycle (start/stop x5) | No leaked peer connections, microphone releases between sessions | No leaked peer, mic indicator clears between sessions |
| Known non-blockers | Per-frame input/output meter animation no longer drives — requires `RTCRtpReceiver.stats` polling to restore (follow-up) | Same flat meter behavior; speaker toggle currently stubbed to a boolean — follow-up to honor runtime routing |
| Regression: custom AEC path | Retired — `codex-ios-audio` crate + `AecBridge.swift` / `VoiceSessionAudioCodec.swift` were deleted; libwebrtc AEC3 handles echo cancellation natively | Retired — `AecBridge.kt` deleted; `JavaAudioDeviceModule` enables the hardware AEC + NS |
| Regression: SSH-tunneled codex server | RPC still flows through SSH; WebRTC peer goes direct to OpenAI edge from device. If client runs in fully air-gapped network, realtime voice will not establish | Same |

## Conversation composer and approvals (Mint, iOS parity)

| Area | Expected (iOS + Android) |
|---|---|
| Idle composer | Send disabled until there is text or an attachment |
| Dictation | Mic left of send (asks for the microphone permission when missing); realtime voice starts only from the home composer pill |
| Partner / model panel | Chip press ripple matches the capsule; models load even when the host's codex reports reasoning efforts the app does not know (e.g. `max`, dropped in Rust); a failed `model/list` is not cached as an empty list, so reopening the panel (after 10 s) retries it |
| Streaming reply | Text grows in place; the paragraph being written does not blink |
| Running turn | Explicit 「停止」; with input the send control reads 「排队」 and the message goes into the Rust follow-up queue |
| Stopping | 「正在停止…」, second stop blocked; resets when the turn ends, a new turn starts or the host disconnects; a refused stop shows an error |
| Disconnected | Persistent banner; send button, full-screen editor and every other send path are blocked without clearing the draft, attachments or a pending question; slash commands still run |
| Creating | Progress on the send control, no double submit; a failed send keeps the draft (restored only into an empty composer) and shows 「重试」 |
| Queue | Count + previews; 「干预」 (messages only) and remove |
| Approval in the open conversation | Card above the composer, one at a time with 「第 N 个，共 M 个」; 「拒绝」/「允许一次」, session grant behind 「本会话都允许…」 + confirmation; submitting / failed (retry) / outcome card; answered elsewhere → 「已在别处处理」 |
| Approval for another conversation | Non-blocking top banner; tap opens that conversation, close only hides the banner (never a denial) |
| User-input request | Only inline above the composer (no duplicate overlay); answers are keyed by request id |

## MyFlicker (mfcli) agent (design `docs/superpowers/specs/2026-09-30-mfcli-acp-agent-design.md`)

| Area | Expected (Android) |
|---|---|
| Agent picker | Paired Mac with `mfcli` on the login-shell PATH shows MyFlicker (beta, letter icon) |
| Model list | 万擎 models before the first message; thinking levels low / medium / high / xhigh |
| New task | Streaming reply; tool calls (command, file edit with diff) render |
| Model / thinking switch | Next turn uses the new value (session file `model` field) |
| History | Task survives leaving the screen and force-stopping the app; history replays on open |
| Idle > 5 min | Next message still works (process respawn + session resume) |
| Terminal sessions | Session started with `mfcli` in a project from `~/.codeflicker/data.json` is listed and can be continued |
| Approvals | Not shown on the phone (auto-approved on the host; phase 2) |

## Store readiness (2026-10-02)

Android and iOS settings expose the public privacy/deletion policy and a hosted
in-app AI content report form without a host connection. No conversation is
attached automatically. Android compile/unit tests/APK and iOS physical-arm64
compile passed. Backend tests cover consent, size, origin, admin authentication,
rate limits, idempotency, retention and storage failure. Physical Android UI
verification remains pending while no device is connected.
