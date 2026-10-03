# HarmonyOS parity QA

Device: HUAWEI Pura X, HarmonyOS 6.1.1 / API 24, USB HDC.
Date: 2026-10-02. Reference implementation: Android.

Actionable follow-up work is tracked in the [Harmony task checklist](tasks.md).
This matrix records verification evidence; the checklist records remaining work.

| Workflow | Result | Evidence / limit |
| --- | --- | --- |
| OHOS Rust compilation | Passed | `cargo check` and `harmony-dev` shared-library build |
| ArkTS/N-API + signed HAP | Passed | Hvigor `BUILD SUCCESSFUL`; shared Rust/N-API plus native Markdown dependencies |
| Install/start | Passed | HDC install and Ability launch; visible Mint home |
| Paste pairing, enumerate agents | Passed | Real Mac host; Codex selected |
| Create task and streaming reply | Passed | Phone sent a no-tool test and displayed `HARMONY_OK` |
| Saved credentials and cold restart | Passed | Reinstall/relaunch restored host without entering pairing again |
| Encrypted storage beyond 1024 bytes | Passed in unit tests | Chunked Unicode/large-secret round-trip, replacement cleanup, failed chunk/index rollback, legacy migration and incomplete-record rejection |
| Native mDNS + Rust discovery | Passed | Temporary `AgentBuddy-Harmony-Fixture` service appeared with SSH port 2222; loopback-only fixture correctly marked unreachable from LAN |
| Discovery cancellation/restart | Passed | Repeated stop/restart still completed and rediscovered the fixture |
| SSH password / agent probe | Passed against isolated fixture | HDC reverse forwarding; correct password accepted, four unavailable CLIs shown disabled; no remote shell commands executed |
| SSH host trust | Passed | First-use fingerprint matched fixture; new key after restart/reinstall rejected as changed; explicit trust retried; per-host removal restored first-use prompt |
| SSH failed password / retry | Passed against isolated fixture | Rejected credentials shown using typed Rust detail; correcting the password succeeded without repeating fingerprint trust |
| SSH task-service bootstrap / private key / stored credential reconnect | Implemented, not fully exercised | Uses generated Rust APIs; existing Alleycat reconnect exercises the cross-thread credential callback; no production SSH server enabled for QA |
| Task list/search/cursor pages | Passed | 37 real host summaries loaded beyond the host's default 25-item page; search found an older task outside the recent-ten view |
| Pin/unpin, hide/restore | Passed | Own smoke task fixed to home, hidden, cold-started and restored; local preferences survived reinstall/relaunch |
| Host/project filter | Passed | Saved Mac/work-directory selection restored after reinstall; project filter showed the two owned test tasks |
| Home task menus and live card updates | Passed | Fork inherited the original smoke history; home rename and confirmed archive succeeded; response changed the pinned card to `HARMONY_HOME_LIVE_OK` without refresh; archive removed its pin |
| Sleep history compatibility | Passed on phone and shared unit tests | Same full-history fork previously failed on `type: sleep`; typed shared protocol plus note hydration fixed it; 873 shared-client tests passed (5 existing ignored) |
| Sleep live events / cold reopening | Passed | Requested-wait note showed 2000 ms and reply `HARMONY_SLEEP_OK`; after explicitly requesting full pages, a fresh process restored the wait note and reply from host history |
| Message boundary fork | Passed on phone and shared tests | Fork of second image prompt retained turns one/two and removed turn three; host read confirmed source still had all three turns |
| Message edit / image draft | Passed on phone | Explicit confirmation removed the selected prompt; prior wait/reply remained and original text plus image returned to composer. Resending returned `HARMONY_EDIT_IMAGE_OK red blue green`; cold restart restored the edited picture and reply |
| Edit first message / empty history | Passed on phone and shared tests | Empty transcript replaced old history; restored draft could be replaced and sent. Host confirmed exactly one new turn with `HARMONY_EMPTY_REVERT_OK` |
| History hydration | Passed | Reopened test showed its original user and assistant messages |
| Continued conversation/code blocks | Passed | Same conversation: `HARMONY_RESUME_OK`, then `HARMONY_STREAM_OK` and Python `print(42)` |
| Streaming node invalidation | Passed | Fixed stable-id ArkUI row reuse; three UI identity regression tests pass |
| Model / effort / sandbox changes | Passed | Existing test task executed with `gpt-6-luna`, `low`, `read-only`; verified host turn context, not only picker labels |
| New-task settings | Passed | Unique connected host/runtime selected; first turn used `gpt-6-luna`, `medium`, `untrusted`, `read-only`, and returned `HARMONY_NEW_SETTINGS_OK` |
| Rename / fork / archive | Passed | Original `Harmony HARMONY_OK smoke` remained; fork retained history, was renamed `Harmony fork smoke`, then archived and removed from phone list |
| Remote account / usage | Passed | Real paired Mac returned ChatGPT login/subscription, remaining quota, reset timestamp and credit balance; no credentials written or account switched |
| Collaboration mode / execute plan | Passed | Host turn context reported `plan`; native proposed plan offered execution; confirmation produced `HARMONY_PLAN_OK` in `default` mode |
| Goal / budget / completion / clear | Passed for inactive lifecycle | Created paused test goal, changed budget 10000 → 20000, reopened and reread host state, marked complete, confirmed clear; test goal removed |
| Goal activation / resume | Implemented, not exercised | No continuously running goal was started for QA; positive i64 budget validation and exact large integers covered in unit tests |
| Custom code review | Passed | Actual `review/start`, typed Entered/ExitedReviewMode events and zero findings; inline no-change example returned `HARMONY_REVIEW_OK` |
| Branch / commit / uncommitted review targets | Compiled, not exercised | Typed Rust requests; QA did not review or change unrelated working files |
| Message queue / delete / steer / auto-dispatch | Passed | Delete marker absent from host history; steer arrived in same turn and returned `HARMONY_STEER_OK`; queued follow-up automatically returned `HARMONY_AUTOQUEUE_OK` after previous reply |
| Follow latest / read older | Passed | 20-line response ended visibly at `HARMONY_END`; manual scroll exposed return-to-latest and that button restored the end |
| Conversation draft navigation | Passed | `DRAFT_RETAIN_TEST` remained after returning to the list and reopening; cleared afterward without sending |
| Native Markdown | Passed | Phone rendered heading/bold/inline code, list, table, highlighted Python, inline/display math and `HARMONY_MARKDOWN_OK` |
| Image attachment from file picker | Passed | Generated red/blue/green fixture selected with the system picker; preview appeared, host recognized all three colors and returned `HARMONY_IMAGE_OK`; sent image restored after reinstall |
| Photo picker / multi-image / limits | Compiled, not fully exercised | No user's photos used; file-picker single-image path exercised; 4-image/32 MB/2048 px/10 MB limits implemented |
| Host image-view message | Passed live and after cold restart | Shared Rust resolved the generated host fixture; native image, preceding commentary and `HARMONY_NATIVE_IMAGE_OK` restored from the host in a fresh app process |
| Image large preview | Passed for controls | Open/close, zoom button, drag and double-tap reset exercised; top system-bar overlap fixed; pinch gesture compiled but not injected by HDC |
| Historical tool/commentary items | Passed for wait/image fixtures | Explicit `itemsView: full` restored wait notes and the host `ImageView` with preceding commentary after cold restart. Broader long-commentary cases remain untested |
| First-turn failure draft recovery | Implemented, not fault-injected | Created thread is shown with its unsent draft; retry uses that thread |
| QR scanner | Compiled, not physically exercised | Scan Kit system scanner |
| Older-turn pagination | Passed | Loaded beyond the first five turns back to `HARMONY_NEW_SETTINGS_OK` and Markdown test; previously visible image message stayed anchored after refresh-wait fix |
| Command approval allow/decline | Passed on phone and host | Decline returned `HARMONY_APPROVAL_DENIED` with no fixture created; allow-once executed the disposable fixture command and returned `HARMONY_APPROVAL_ACCEPTED`. Reason, command and cwd were visible |
| Multi-question answers | Passed on phone and host | Submit remained disabled with only one of two answers; completing both sent Blue/Square and returned `HARMONY_ANSWERS_OK Blue Square`. Secret input masking is implemented but not device-tested |
| Stop active reply | Passed | Stopped own synthetic waiting task; active indicator cleared, subsequent message worked |
| Offline/reconnect race | Not yet exercised | Uses shared Rust lifecycle; normal cold-start restoration tested separately |
| Dark theme/folded display | Not yet exercised | Shared semantic resource colors; no separate layout assertions |
| iOS/Android regression device runs | Not run | Swift/Kotlin edits only update a helper-path comment; shared Sleep/history/message-boundary fixes covered by host Rust tests and Harmony device. iPhone available; no Android device/emulator connected during this pass |

The port is usable for the verified remote task flow. Unverified rows are not
release certification. Feature gaps are listed in the Harmony README.
The 2026-10-02 controls pass rebuilt/signed and reinstalled the HAP; artifacts,
`task-controls-build.log` and the settings screenshot live in the configured
Harmony work directory. These ArkUI changes do not change Swift/Kotlin or shared
Rust runtime behavior. Model metadata can lag on the connected host version;
the host turn context was inspected to verify the actual submitted settings.

The media pass additionally verified Markdown, file-picker images, real-time host
images and large previews. Screenshots `harmony-markdown.png`,
`harmony-image-send.png`, `harmony-remote-image.png` and
`harmony-image-preview.png`, the signed HAP/hash and `media-preview-build.log` are
under the configured work directory. The generated fixture and transient UI dumps
were removed from the phone. Markdown adds native formula/highlighting libraries;
only arm64 is a supported build target. This pass changes Harmony UI/platform
projection only; shared Rust runtime, Android and iOS sources are unchanged.

The discovery/SSH pass additionally fixed the pinned ArkTS generator's worker-thread
callback rejection, which initially caused a native abort at startup. Rebuilt
N-API adapter plus signed HAP cold-started with the saved Mac host and task list.
Six regression tests pass. Evidence: `harmony-discovery.png`,
`harmony-ssh-probe.png`, `harmony-ssh-key-change.png`, `ssh-callback-build.log`,
`discovery-ssh-build.log` and the current package hash in `artifacts/verification.json`.
Final cold-start message regression on the real paired Mac returned
`HARMONY_SSH_REGRESSION_OK` (`harmony-ssh-regression.png`). The loopback SSH
fixture and mDNS advertisement were stopped, the USB reverse mapping and phone
test trust entry were removed, and approximately 742 MiB of disposable build/test
outputs were cleaned. Signing, the final HAP and reusable build dependencies remain
under the user-selected work directory.
These changes are confined to Harmony platform code and its generated adapter fix;
the shared Rust runtime and iOS/Android application code were not changed in this pass.

The account/task-actions pass adds only Harmony UI and thin generated Rust API
calls. Eight regression tests pass, including i64 goal-budget precision and
invalid/overflow rejection. `task-actions-build.log`, `harmony-task-goal.jpeg`,
`harmony-task-queue.jpeg`, `harmony-task-actions.jpeg` and the package hash are in
the configured work directory.
Host records confirmed collaboration-mode transitions, structured review output,
same-turn steering and absence of the deleted queue marker. The synthetic goal
was cleared after verification; no active goal was left on the test task.
Cold-start message regression returned `HARMONY_TASK_ACTIONS_OK`, and reopening
the goal editor reread an empty goal from the host. Approximately 432 MiB of
disposable outputs were removed after this pass; the final signed HAP, signing
material and reusable toolchain remain (about 5.31 GiB total work directory).

The home/history pass adds Android-style local pin/hide preferences, host/project
filters and cursor-complete task search. Twelve Harmony regression tests pass.
Three temporary fork tasks were archived after verification; search, filters and
test pins were cleared. `harmony-home-live.jpeg`, `harmony-sleep-compat.jpeg` and
`harmony-full-history.jpeg` capture the verified paths.

This pass also changes shared Rust: the Codex fork accepts typed `sleep` items,
hydration projects them as notes, and both direct and store-managed turn-list
requests explicitly select `itemsView: full`. On host 0.160.0, omitting that field
returned `summary` and omitted tool/commentary items. The previously failing
full-history fork and cold-open wait note now work on the physical phone.

Validation: 873 shared-client tests passed (5 existing ignored); 432 protocol
tests passed, including generated JSON/TypeScript consistency; 70 analytics tests
passed. Public and experimental schema generation also passed after fixing an
existing core/app-server dynamic-tool definition-name collision and matching
TypeScript optional fields to their existing serialization. Existing realtime
test initializers were brought up to date without changing their expectations.
TUI compiled and ran with the repository's 8 MiB test stack: 2536 passed, 1 ignored,
18 failed. Fifteen failures only differ in the fork version (0.132.0 versus the
upstream 0.0.0 snapshots); three assume temporary files are outside the user's
home, while this task deliberately stores them under Downloads. These snapshots
were not accepted. Failure details are retained in `logs/tui-known-failures.json`;
the full Codex workspace suite was not run. No iOS/Android device regression was
performed for these shared changes. This remains an incomplete parity port.
Cleanup removed approximately 19.08 GiB of disposable outputs; the retained work
directory is about 5.66 GiB. The signed HAP, native libraries, generated bindings,
toolchain/dependencies, private signing files and verification evidence remain.

The message-actions pass adds a native selected-message sheet and image-aware
draft restoration. Shared Rust now counts user-message boundaries rather than
rendered tool/assistant rows. It uses stable turn IDs with the current host's
`thread/revert` method, and retries legacy `thread/rollback` only after a definitive
unsupported-method response. Timeout and transport errors never trigger a second
mutation. A failed boundary fork archives only the newly created fork. Reverted
history replaces the store even when empty, then loads the returned history cursor.

On the physical phone, a second-message fork retained the first two turns, while
host reads confirmed the source still had three. Editing that image message
restored its text and image; resending and cold reopening preserved both. Editing
the first message cleared history and allowed a fresh one-turn conversation.
Screenshots `harmony-message-fork.jpeg`, `harmony-message-edit.jpeg` and
`harmony-message-cold.jpeg` and machine-readable evidence are in `artifacts/`.
Validation: 14 Harmony tests, 878 shared-client tests (5 existing ignored),
214 protocol/schema tests and 224 app-server library tests passed. Shared-client
tests ran serially because two existing cloud-sync tests race on a global table
in parallel runs. The earlier TUI failures remain documented above; the full
Codex workspace suite and iOS/Android device regressions were not run.

The same device pass verified real command approvals: decline did not create the
fixture, while allow-once wrote the expected marker. A two-question form blocked
incomplete submission, then delivered both selected answers to the host.
`harmony-approval.jpeg`, `harmony-questions.jpeg` and `approval-qa.json` retain the
evidence. All four persisted test threads created in this pass were archived;
their test pins/filters and the approval fixture were removed. No test turn,
pending approval or unanswered question was left running.

Before the user-requested pause at 2026-10-02 21:35 UTC, cleanup removed 24.73 GiB
of disposable outputs. The retained work directory is approximately 5.66 GiB.
The installed/saved signed HAP has SHA-256
`f9e259f6d125b3683de3a3b1d994fb5146eea0167fd2f8a5f7dbf09eecb50aa0`.
It remains usable without the deleted Rust/Hvigor intermediates. Source changes
are local and uncommitted, including the Codex fork changes; no release was made.

The final cold-start check also restored the earlier host `ImageView` fixture,
its preceding commentary and reply. `harmony-history-cold.jpeg` was visually
inspected; no new message or host action was needed for this history check.

Resume with the unverified rows above: prioritize iOS/Android regression for the
shared history mutations, broader long-commentary cases, offline/reconnect
and SSH bootstrap/private-key flows before adding the remaining native voice,
notifications, non-image attachments, terminal and pets/widgets features.


## Resumed physical-device regression and Harmony push (2026-10-02)

The devices were explicitly fixed to Pura X `56T0225330000124`, Pixel 9
`45211FDAQ00309`, and 回森 iPhone 16 (CoreDevice
`1B7D496B-0E4A-50B9-9A12-5B189F29796F`, Xcode UDID
`00008140-00112108140A801C`). The connected iPhone 15 Pro Max was not used.

- iPhone 16: current raw Rust staticlib and generated Swift compiled;
  `MobileHistoryDeviceTests` passed (1 test, zero failures). Xcode MCP
  authorization timed out, so the documented command-line device fallback
  built and ran XCTest. No simulator was built or run.
- Pixel 9: current arm64 JNI and generated Kotlin compiled;
  `MobileHistoryDeviceTest` passed (1 test), `NativeContextInitTest` passed
  (2 tests). Both installed apps launched successfully after regression.
- Both history tests use a synthetic WebSocket server with real native bindings.
  Assertions cover full cursor pagination, Sleep hydration, unique item IDs,
  fork source preservation, draft restoration, first-message revert to empty,
  and a modern revert error without legacy rollback. These tests do not imply
  complete visual UI or production-host acceptance. Fixture and reverse port
  forwarding were stopped after testing; local iOS endpoint configuration was
  removed and the project regenerated.
- Pura X: notification permission granted; cold/warm notification Want routing
  opened the specified QA task with Sleep history. Push token acquisition
  returns `1000900010` (illegal application identity). Actual cloud delivery
  remains unverified until AGC identity/signing, Push Kit and credentials are
  configured and compatible host/Worker code is deployed.
- Push implementation tests: Worker 104 passed, shared Rust push 69 passed,
  host fork push 66 passed / 1 ignored, Harmony client 19 passed. Client coverage
  includes token-update races, denied permission, detached ability, route
  validation/deduplication, concurrent notification taps and navigation failure.
- Host fork support and candidate binary remain local, outside the repository;
  the running host and deployed Worker were not replaced.

Final signed HAP SHA-256: `533e1ca76750d54ea0e1523afd667b6824e56068c97524cf80b1d6b7ec2ebd32`.
Installation succeeded after the final navigation fix. Deliverables, native
libraries, test bundles and evidence are retained under
`~/.agentBuddy/device-regression/artifacts` and
`~/.agentBuddy/harmony/artifacts`; diagnostic logs are in their
respective `logs` directories. Disposable build trees are recorded in
`device-regression/artifacts/cleanup.json`.


### Archive update — 2026-10-03

The registered `.mobile` app now obtains a Push Kit token on Pura X. The Worker
configuration is deployed and host support is committed/pinned, but the running
host is not replaced and real background delivery is still unverified. Store
candidate 0.1.0 (1) passed outer `.app` signature verification and is associated
in AGC. Three portrait screenshots were uploaded and saved; no review submitted.

After cleanup, only compact QA summaries remain in
`~/.agentBuddy/harmony/verification/`; the earlier raw logs/screenshots/test
bundles listed above were disposable and removed. Signing lives separately in
`~/.agentBuddy/signing/harmony/`, releases and store materials under
`~/.agentBuddy/harmony/`. Fresh local checks: Harmony 19/19 and Worker 104/104.
