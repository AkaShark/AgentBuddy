# CI

There are no helper scripts in this directory yet; CI lives entirely in
`.github/workflows/`. Current workflow files:

| Workflow | Trigger | What it does |
|---|---|---|
| `.github/workflows/mobile-ci.yml` | PRs, push to `main` | Shared prep (codex sync + UniFFI bindings), Android `:app:assembleDebug`, iOS simulator build |
| `.github/workflows/mobile-release.yml` | push to `main` (mobile paths) | Change detection, then Play upload, iOS/Mac TestFlight, and Mac direct distribution |
| `.github/workflows/android-apk-release.yml` | `v*` tags, manual | Signed Android APK release |
| `.github/workflows/android-play-release.yml` | manual | Android Google Play release |
| `.github/workflows/ios-testflight.yml` | manual | iOS TestFlight release |
| `.github/workflows/ios-app-store-release.yml` | manual | iOS App Store release |
| `.github/workflows/mac-testflight.yml` | manual | Mac Catalyst TestFlight release |
| `.github/workflows/mac-direct-dist.yml` | manual | Mac Catalyst direct (notarized) distribution |
| `.github/workflows/desktop-release.yml` | `desktop-v*` tags, manual | Tauri desktop host app release |

Remote sccache is optional: jobs map the `SCCACHE_R2_ENDPOINT` /
`SCCACHE_R2_ACCESS_KEY_ID` / `SCCACHE_R2_SECRET_ACCESS_KEY` secrets onto
`SCCACHE_ENDPOINT` / `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`; without them
builds fall back to a local cache (see `tools/scripts/load-sccache-aws-creds.sh`).
