# AgentBuddy for HarmonyOS

Native ArkUI client, using the Android Tasks / Projects / Hosts layout and Mint
theme. The shared `codex-mobile-client` Rust crate still owns connections, session
state, streaming, approvals, reconciliation and history. The app calls generated
UniFFI ArkTS bindings through a generated N-API adapter; it contains no separate
WebSocket protocol implementation or copied reducer.

This is an initial native remote-client port, **not complete Android feature
parity or an AppGallery release**. Bundle: `com.akashark.agentbuddy.mobile`.

Remaining work is tracked in the [Harmony task checklist](docs/tasks.md), with
priorities, current status and acceptance criteria for each item.

## Build and install

Tested with DevEco Studio 26.0.0, its bundled SDK 26, Rust 1.98.1 and a HUAWEI
Pura X running 6.1.1 / API 24. The current minimum SDK is API 24, arm64 only.

```sh
make harmony-setup       # isolated Rust target and pinned ArkTS generator
make harmony-doctor     # inspect SDK/toolchain/USB device
make harmony            # Rust + generated bindings + HAP
make harmony-hap        # UI-only iteration using existing native artifacts
make harmony-install    # build, install signed HAP, launch on the connected phone
make harmony-test       # UI identity, home filtering, storage, budgets and message selection
```

For the first real-device build, open
`~/.agentBuddy/harmony/build/project` in DevEco. In Project Structure →
Signing Configs, sign in with a Huawei developer account and generate a debug
signature for the connected device. Then run `make harmony-hap`. The staging
script preserves that configuration and privately copies its certificate,
keystore, profile and password-encryption material into `~/.agentBuddy/signing/harmony/`.
Signing files and passwords must never be committed. Keep the USB device unlocked
and accept its development/debugging authorization prompt when prompted.

AGC rejected the original `.harmony` package suffix as a reserved word. The
registered package is now `com.akashark.agentbuddy.mobile` (APP ID
`6917618050179563889`). Existing `.harmony` debug profiles do not match it;
obtain a new profile after Push Kit activation. Keep the old app and signing
backup until pairing migration has been verified.

For store packaging, save a private Hvigor signing configuration to
`~/.agentBuddy/signing/harmony/release-signing-config.json`, then run
`bash apps/harmony/scripts/build-release.sh`. It checks that the public Profile
matches the package, has type `release`, and uses `app_gallery` distribution,
then invokes the SDK's `assembleApp` task in release mode. `HARMONY_SIGNING_CONFIG`
can select another private configuration. The 0.1.0 (1) candidate was built with
a distribution certificate and passed Huawei verify-app signature/digest checks.
Store readiness and real background push delivery remain separate acceptance
items. New outputs stay under `harmony/artifacts/release/`; the submitted candidate
is retained in `harmony/releases/0.1.0-1/`.

`DEVECO_ROOT` overrides the installed DevEco `Contents` directory.
`AGENTBUDDY_HARMONY_HOME` overrides the work directory.
`AGENTBUDDY_HARMONY_SIGNING_HOME` overrides the private signing directory.
`HARMONY_DEVICE_SERIAL` selects a phone when several are attached.
Do not build directly in this source directory: use the staged project so build
products and package-manager state stay outside the repository.

## Work directory and cleanup

Disposable command-line build state lives under
`~/.agentBuddy/harmony`:

| Directory | Purpose |
| --- | --- |
| `artifacts/` | Signed HAP and native libraries needed for UI-only rebuilds |
| `build/generated/` | Generated ArkTS, declarations and N-API Rust adapter |
| `build/project/` | Disposable DevEco project; source of truth is this repository |
| `build/rust/`, `build/bindgen/`, `build/rust-host/`, `build/codex-tests/` | Rebuildable native/host Rust intermediate files |
| `cache/`, `tools/` | Isolated Rust toolchain, Cargo dependencies and pinned generator |
| `releases/`, `store/`, `verification/` | Retained releases, store materials and compact QA evidence |
| `logs/` | Local build/verification results |

`make harmony-clean` deletes build intermediates, the redundant unsigned HAP and
the disposable SSH test venv/pip cache (stop any fixture server first),
while keeping the signed package, native libraries, generated bindings, SDK
environment and signing files. A subsequent Rust build recreates its intermediates.
To reclaim the remaining environment, the whole Harmony work directory can be
removed after preserving `releases/`, `store/` and needed verification summaries.
Signing is independent under `~/.agentBuddy/signing/harmony/`; keep its keystores,
profiles, certificates and adjacent password-encryption material for future updates.
After a full cleanup, run `make harmony-setup` and `make harmony` to rebuild.

DevEco itself keeps its account/settings and original auto-signing material in
its standard IDE directories; these are not build caches and are not removed by
the cleanup command. The installed SDK is reused, not copied or deleted.

## Current functionality

- QR or pasted Alleycat pairing, available-agent selection, direct WebSocket host
  connection, host removal/reconnect, encrypted saved pairing and device identity.
- Native mDNS discovery with Wi-Fi IPv4 context; shared Rust owns probes, merging
  and ranking. Discovered hosts offer direct task-service or SSH connections.
  Saved host rows show the Rust connection health.
- SSH password/private-key forms, optional encrypted credentials, explicit
  first-use/changed-key trust confirmation, agent probing and typed Rust SSH
  connection paths. Login and fingerprint removal is available per host/port.
  Password authentication and key-change rejection are device-tested against an
  isolated fixture; actual SSH task-service startup and private-key login still
  need end-to-end QA. The verified normal task flow uses the paired Mac host.
- Task list refresh/search across Rust cursor pages, recent-ten or pinned home,
  pin/unpin, hide/restore, host/project filtering and virtualized rows. Pin order,
  hidden tasks and host/project selection use the same Rust preferences API as
  Android and survive cold starts. New tasks are pinned automatically; archiving
  removes their local pin/hidden preference. Project grouping, history reopening and
  Rust-owned turn pagination (five turns per request, matching Android).
- Runtime-specific model/reasoning selection and approval/sandbox settings for
  new and existing tasks. Permission options follow Rust runtime capabilities;
  choices are submitted through typed start/resume/turn requests.
- Rename, fork and archive tasks through `AppClient`; archive asks for confirmation.
- Message actions call the shared Rust store: editing removes the selected prompt
  and later turns, restoring text/images as a draft after explicit confirmation;
  a boundary fork keeps the selected prompt and its reply in a new task.
  Active replies/goals, queued messages and ambiguous steered/autonomous history
  are rejected by Rust before mutation. Image recovery completes before rollback.
- Remote-host account status, subscription and per-runtime usage windows, with
  remaining quota, reset time and credit balance. Account changes remain on the
  computer, matching Android's remote-host account flow.
- Codex collaboration presets, proposed-plan confirmation/dismissal and plan
  progress. Goal creation, objective/budget updates, pause/resume/completion and
  clearing call the existing Rust APIs; clearing asks for confirmation. Token
  budgets preserve signed 64-bit integer precision, and a blank budget leaves
  an existing budget unchanged.
- Review forms for uncommitted changes, a base branch, a commit or custom
  instructions; review output appears in the conversation. Pending messages can
  be deleted or inserted into the active reply; Rust owns automatic queue dispatch.
- Text messages, streaming updates, stop action, reasoning/tool/file change
  display, native Markdown lists/tables, highlighted code, inline/display math,
  selection/copy and HTTP(S) links.
- Newer-host `sleep` items are accepted by the shared typed protocol and render
  as requested-wait notes. This also fixes full-history forks containing those
  items; no Harmony-only wire parser or fallback history cache is used.
- Images from the system photo/file picker, up to four per message, with preview,
  removal and process-local drafts. Decode/re-encode in memory (32 MB input cap,
  longest edge 2048 px, 10 MB encoded cap per image), then submit typed Rust image
  inputs. No broad storage permission or image files/cache are created by the app.
- Inline/generated images and host `ImageView` messages, resolved by the existing
  Rust `AppClient.resolveImageView` API. Tap to open a large preview with zoom
  buttons, drag, pinch and double-tap reset. Decoded native images are released
  when their message component disappears.
- Follow the latest reply while streaming; pause following when reading older
  messages, with a return-to-latest button. Keep unsent text when navigating
  and image attachments when navigating between conversations in the same app
  process. Older-page loading keeps the previously visible message anchored.
- Approval accept/decline with command/path/scope details; complete multi-question
  replies with secret input masking. Allow-once/decline and a two-question reply
  are verified against the real paired host; secret input still needs device QA.
- Foreground reconnect and light/dark semantic theme resources generated from
  the same Mint theme JSON used by Android/iOS.

Still to port: non-image file attachments, notifications, native WebRTC voice,
Ghostty/local terminal and pets/widgets. Remote account login/key changes
are managed on the host, as on Android. Goal activation/resume and non-custom
review targets still need end-to-end QA; the tested goal remained paused until
it was marked complete and cleared.
Shared history pagination explicitly requests full items: newer hosts default to
display summaries, which omit tools and commentary. Cold-reopened wait notes,
historical `ImageView` and its preceding commentary are device-verified. Broader
long-commentary cases still need QA. Real-time `ImageView` is also verified.
Search filters the task summaries loaded by Rust after draining the host's cursor
pages; it is not a full-text search inside conversation history.
Some host versions report the original model or omit permissions in `thread/read`;
the settings screen labels host-reported values separately from next-message
choices. Device tests confirmed the chosen model, effort and permissions in the
actual host turn context. Composer choices/drafts are process-local; effective
task settings remain on the host. First-turn failure recovery preserves the draft
in the created task, but its network-failure path still needs fault-injection QA.

## Verification

See [the QA matrix](docs/qa-matrix.md). `scripts/device-ui.py` is a local HDC/uitest
helper for reading the app's own controls, tapping labels and filling inputs.
Its `pair-input` command reads the installed Mac host's pair payload and fills the
phone's pairing form without printing credentials; use only for an authorized host.

Markdown uses [`@luvi/lv-markdown-in`](https://gitee.com/luvi/lv-markdown-in) 3.4.7
(MIT), pinned with its formula/highlighting dependencies in the entry lockfile.
HTML preview/JavaScript and Mermaid are disabled. The native bytecode HAR needs
normalized OHM URLs; the build enables that setting. The package filter excludes
non-arm64 libraries. Markdown's code theme follows the current resource color mode;
live system-theme switching still needs device QA.

The generator is pinned to `ohos-rs/uniffi-bindgen-arkts` commit
`56018734abafc1de7e9d124badb3a33d49243f59` (beta.2). A documented post-generation
fix adapts its exported `f32` parameters to N-API's `f64`; the C ABI remains `f32`.
The same fix dispatches synchronous credential/trust callbacks from Rust worker
threads to their owning ArkTS thread. Only the worker waits; same-thread calls
remain direct, and returned byte buffers are copied before crossing threads.
Without this compatibility fix, registering an SSH credential provider caused a
native abort during foreground reconnect, even for an existing Alleycat host.
The adapter pins UniFFI 0.31.0 to match the shared crate. Generated files stay local.
The OHOS Rust profile preserves the ELF symbol table for metadata extraction;
only packaged copies are stripped. The SDK LLVM tools, including `llvm-ranlib`,
are explicitly selected for vendored OpenSSL.

ASSET limits a secret to 1024 bytes. Large host lists and SSH private keys are
stored in encrypted chunks with a commit-last encrypted index. Legacy single
records remain readable and migrate on write; failed writes preserve the previous
complete value. SHA-256 host/port aliases keep addresses out of asset labels.
The isolated `tests/ssh-fixture.py` serves only synthetic probe results on loopback,
executes no received commands and can be exposed to the phone with temporary HDC
reverse forwarding. Keep its venv, keys and cache in the configured work directory.
