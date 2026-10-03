# macOS Tauri Distribution Checklist

The macOS application lives in `apps/desktop`, uses Tauri v2, and has bundle ID
`com.akashark.agentbuddy.host`. The iOS Catalyst target and release lanes have
been retired. Do not use the old Catalyst App Store screenshots or builds for
this application.

## Build and distribute

- Install Node 22 and run `npm ci` in `apps/desktop`.
- Use `make desktop-dev` for development and `make desktop-build` for local app/DMG builds.
- Use `make desktop-dist` with Developer ID and notarization credentials for distribution.
- Tag `desktop-vX.Y.Z` or run `.github/workflows/desktop-release.yml` manually to build arm64 and x86_64 DMGs.
- Verify notarization is Accepted and stapler validation succeeds before publishing the release draft.
- Follow `apps/desktop/README.md` for credentials and recovery of pending notarization jobs, and `apps/desktop/docs/qa.md` for functional QA.

## App Store Connect

As of 2026-10-03, macOS distribution is Developer ID-signed, notarized DMG only.
Do not add a Mac App Store replacement build or restore the retired Catalyst /
Mac TestFlight lanes. `Desktop release` remains enabled for both DMG architectures.

The existing iOS/Catalyst record uses `com.akashark.agentbuddy`, which differs
from the Tauri identifier. Manage only its macOS submission when retiring the
old store release; retain the shared app record and the independent iOS submission.

On 2026-10-03, macOS 1.5.0 (build `202609251324`), submission
`745f6765-533e-4670-a182-21dc5f91b8e4`, was canceled in App Store Connect and
verified as **Removed**. The iOS 1.5.0 submission remained **Waiting for Review**.

The unused repository Actions secrets `MAC_APP_STORE_PROFILE_B64`,
`MAC_DIST_CERT_P12_B64`, and `MAC_DIST_CERT_PASSWORD` were removed on 2026-10-03
after verifying no current workflow referenced them. Keep `MAC_DEVELOPER_ID_CERT_*`
for DMG signing and shared `ASC_*` credentials for notarization and iOS releases.
Removing these CI secrets does not revoke Apple certificates or delete local keys.
