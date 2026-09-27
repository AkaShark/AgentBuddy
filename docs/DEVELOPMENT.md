# Development Guide

## Prerequisites

- **Xcode.app** (full install, not only Command Line Tools):

  ```bash
  sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
  ```

- **Rust via rustup** with iOS targets. If Homebrew's `rust` formula is installed, its `cargo`/`rustc` will shadow rustup and break cross-compilation. Either `brew uninstall rust` or ensure `~/.cargo/bin` appears before `/opt/homebrew/bin` in your `PATH`.

  ```bash
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
  rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios
  ```

- **meson** + **ninja** (required by `webrtc-audio-processing-sys`):

  ```bash
  brew install meson
  ```

- **xcodegen** (for regenerating `AgentBuddy.xcodeproj`):

  ```bash
  brew install xcodegen
  ```

## Connect Your Mac to Litter Over SSH

Use this flow to make Codex sessions from your Mac visible in the iOS/Android app.

1. Enable SSH on the Mac.

   - UI: `System Settings` -> `General` -> `Sharing` -> enable `Remote Login`.
   - CLI:
     ```bash
     sudo systemsetup -setremotelogin on
     ```
   - If you get a Full Disk Access error, grant it to your terminal app in `System Settings` -> `Privacy & Security` -> `Full Disk Access`, then restart terminal and retry.

2. Verify SSH and Codex binaries from a non-interactive SSH shell.

   ```bash
   ssh <mac-user>@<mac-host-or-ip> 'echo ok'
   ssh <mac-user>@<mac-host-or-ip> 'command -v codex || command -v codex-app-server'
   ```

   If the second command prints nothing, install Codex and/or fix shell PATH startup files.

3. Connect from the Litter app.

   - Keep phone and Mac on the same LAN (or same Tailnet).
   - In Discovery: tap a host showing `codex running` to connect directly, or tap an `SSH` host and enter credentials.

4. Fallback: run app-server manually bound to loopback and forward the port over SSH.

   On the Mac:

   ```bash
   codex app-server --listen ws://127.0.0.1:8390
   ```

   Then connect the phone via the `SSH` flow in Discovery — Litter opens the SSH connection, port-forwards `127.0.0.1:8390`, and connects through the tunnel. Do not bind `0.0.0.0` unless you fully understand the exposure; the SSH flow is the supported path.

5. Thread/session listing is `cwd`-scoped. If expected sessions are missing, choose the same working directory used when those sessions were created.

## Maintained Codex and Ghostty forks

AgentBuddy pins exact commits from its own forks:

| Submodule | Fork | Maintenance branch | Upstream base at migration |
| --- | --- | --- | --- |
| `shared/third_party/codex` | [AkaShark/codex](https://github.com/AkaShark/codex) | `codex/agentbuddy` | `13595c36e` (rust-v0.132.0) |
| `shared/third_party/ghostty` | [AkaShark/ghostty](https://github.com/AkaShark/ghostty) | `codex/agentbuddy` | `a968e120d` |

The initial fork commits preserve exactly the previous upstream-plus-patches source
trees, including Codex's two new stub files. The old `patches/codex/` and
`patches/ghostty/` files are archives, not build inputs. Do not regenerate them.
There is no build-time patch application or EXIT-trap rollback. `make patch` is a
compatibility alias for sync; `make unpatch` and `make unpatch-ghostty` fail safely.

### Clone or update a checkout

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

This checks out the parent repository's exact gitlinks, normally in detached HEAD.
The `.gitmodules` branch setting documents the maintenance branch; it does not make
normal submodule updates follow the branch tip. Avoid `update --remote` for builds.

The `sync-codex.sh` and `sync-ghostty.sh` scripts initialize missing submodules and
preserve an existing development checkout by default. Pass `--recorded-gitlink` to
check out the recorded gitlink without forcing away local changes.

### Change a dependency

For Codex (use `ghostty` in the same commands for Ghostty):

```bash
cd shared/third_party/codex
git fetch origin
git switch codex/agentbuddy  # first checkout: git switch --track origin/codex/agentbuddy
git pull --ff-only
# Edit, review, and run the relevant validation.
git add <changed-files>
git commit -m "mobile: describe the change"
git push origin codex/agentbuddy
cd ../../..
git add shared/third_party/codex
git commit -m "deps: update AgentBuddy Codex fork"
```

Push the parent commit through the normal AgentBuddy review workflow after the fork
commit is available remotely. For larger changes, use a topic branch and a PR into
the fork's `codex/agentbuddy` branch. Fork `main` is not the mobile maintenance branch.

### Incorporate upstream updates

Each local submodule uses `origin` for the AkaShark fork and `upstream` for the
official repository. New clones need the upstream remote added once:

```bash
git -C shared/third_party/codex remote add upstream https://github.com/openai/codex.git
git -C shared/third_party/ghostty remote add upstream https://github.com/ghostty-org/ghostty.git
```

Fetch a deliberately chosen upstream tag or commit, merge it on a topic branch
based on `codex/agentbuddy`, resolve conflicts, and verify both mobile platforms.
Deepen shallow history if Git needs older ancestors. Merge through review, push the
fork first, then update the parent gitlink. Do not rebase or force-push the published
maintenance branch. Do not combine a dependency upgrade with routine app edits.

## Build the Rust Bridge

```bash
./apps/ios/scripts/build-rust.sh              # package mode (device + sim + xcframework)
./apps/ios/scripts/build-rust.sh --fast-device # raw device staticlib only
```

## Build and Run iOS

Regenerate project if `apps/ios/project.yml` changed:

```bash
make xcgen
```

Open in Xcode:

```bash
open apps/ios/AgentBuddy.xcodeproj
```

CLI build:

```bash
xcodebuild -project apps/ios/AgentBuddy.xcodeproj -scheme AgentBuddy -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

## Build and Run Android

Prerequisites: Java 17, Android SDK + build tools for API 35, Gradle 8.x.

```bash
open -a "Android Studio" apps/android                                  # open in Android Studio
cd apps/android && ./gradlew :app:testDebugUnitTest                    # run tests
gradle -p apps/android :app:assembleOnDeviceDebug :app:assembleRemoteOnlyDebug  # build flavors
```

## TestFlight (iOS)

1. Authenticate with App Store Connect:

   ```bash
   asc auth login \
     --name "Litter ASC" \
     --key-id "<KEY_ID>" \
     --issuer-id "<ISSUER_ID>" \
     --private-key "$HOME/AppStore.p8" \
     --network
   ```

2. Bootstrap TestFlight defaults:

   ```bash
   APP_BUNDLE_ID=<BUNDLE_ID> ./apps/ios/scripts/testflight-setup.sh
   ```

3. Build and upload:

   ```bash
   APP_BUNDLE_ID=<BUNDLE_ID> \
   APP_STORE_APP_ID=<APP_STORE_CONNECT_APP_ID> \
   TEAM_ID=<APPLE_TEAM_ID> \
   ASC_KEY_ID=<KEY_ID> \
   ASC_ISSUER_ID=<ISSUER_ID> \
   ASC_PRIVATE_KEY_PATH="$HOME/AppStore.p8" \
   ./apps/ios/scripts/testflight-upload.sh
   ```

   - Reads `MARKETING_VERSION` from `apps/ios/project.yml`; auto-bumps patch if the version is already live.
   - Auto-increments build number from the latest App Store Connect build.

## App Store Release (iOS)

```bash
APP_BUNDLE_ID=<BUNDLE_ID> \
APP_STORE_APP_ID=<APP_STORE_CONNECT_APP_ID> \
TEAM_ID=<APPLE_TEAM_ID> \
ASC_KEY_ID=<KEY_ID> \
ASC_ISSUER_ID=<ISSUER_ID> \
ASC_PRIVATE_KEY_PATH="$HOME/AppStore.p8" \
./apps/ios/scripts/app-store-release.sh
```

Metadata is sourced from `apps/ios/fastlane/metadata/en-US/`.
