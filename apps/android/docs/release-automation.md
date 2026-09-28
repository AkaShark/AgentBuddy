# Android Release Automation

- **Android Play Release** is manually dispatched. By default it builds signed APK/AAB artifacts without uploading to Google Play. Enable its `publish` input only after Play onboarding is complete.
- **Mobile Release** builds Android artifacts on relevant main-branch changes. It uploads to internal testing only when repository variable `ANDROID_PLAY_PUBLISH_ENABLED=true`.
- Both workflows save APK/AAB artifacts and checksums before attempting a Play upload, so a publishing failure does not hide successful builds.
- **Android APK Release** remains the separate GitHub Release distribution workflow.

See the [Play setup and first-release checklist](../../../docs/releases/android-play-internal-checklist.md) for credentials, first manual AAB upload, permissions, and local commands.
