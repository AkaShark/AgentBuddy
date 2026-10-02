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

The current desktop configuration distributes an app/DMG and does not implement
a Mac App Store or TestFlight packaging/upload lane. The existing iOS/Catalyst
record uses `com.akashark.agentbuddy`, which differs from the Tauri identifier.
A future App Store release needs its own deliberate bundle identity, sandbox
compatibility and packaging setup; do not reuse the retired Catalyst binary or
claim its metadata/screenshots describe the Tauri application.
