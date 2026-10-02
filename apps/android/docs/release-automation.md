# Android Release Automation

- **Android Play Release** is manually dispatched. By default it builds signed APK/AAB artifacts without uploading to Google Play. Enable its `publish` input only after Play onboarding is complete.
- **Mobile Release** builds Android artifacts on relevant main-branch changes. It uploads to internal testing only when repository variable `ANDROID_PLAY_PUBLISH_ENABLED=true`.
- Automatic uploads use `ANDROID_PLAY_RELEASE_STATUS` (`draft` by default; change to `completed` when ready to distribute to internal testers). The manual workflow's `release_status` input also defaults to `draft`.
- Publishing runs a Google Play API preflight before the Android native build, checking credentials, app and source-track access without committing a release. A successful preflight does not replace Play's upload/review checks.
- Android builds use compile/target API 36 for the current Play submission requirement. Rebuild older API 35 artifacts before first submission.
- Both workflows save APK/AAB artifacts and checksums before attempting a Play upload, so a publishing failure does not hide successful builds.
- **Android APK Release** remains the separate GitHub Release distribution workflow.

See the [Play setup and first-release checklist](../../../docs/releases/android-play-internal-checklist.md) for credentials, first manual AAB upload, permissions, and local commands.
