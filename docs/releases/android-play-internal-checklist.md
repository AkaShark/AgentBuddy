# Android Play setup and first release

Package: `com.akashark.agentbuddy.android`. Release variant: `Release`.

## While developer verification is pending

Signing and builds do not require an approved Play developer account. Run **Android Play Release** in GitHub Actions with `publish=false` (the default). Download `android-play-release-<run number>` from the completed run; it contains a signed APK, AAB, and SHA-256 checksums. Use the APK for direct installation and keep the AAB for the first Play Console upload.

The automatic **Mobile Release** workflow also builds these artifacts. Its Play upload step is disabled unless the repository variable `ANDROID_PLAY_PUBLISH_ENABLED` is exactly `true`. Missing/false means build only. Manual publishing additionally requires `publish=true` and fails early if onboarding is not enabled.

Both Play workflows derive versionCode from seconds since 2020 plus 300,000,000, rather than independent workflow run counters. Rerunning a workflow produces a newer code. The two Play release jobs share a concurrency group so their builds cannot overlap. Do not manually upload an older artifact after a newer one.

## Credentials

Keep local credentials outside the checkout under `~/.agentBuddy/signing/android/`:

- `upload/agentbuddy-upload.jks`, `store.password`, `key.password`, `key-alias.txt`: Android upload signing key and passwords. Back these up securely.
- `play/service-account.json`: Google Play API service-account credential; this is separate from the signing key.
- `firebase/google-services.json`: Firebase app configuration, not a Play publishing credential.
- `play-upload.env`: local shell configuration, mode `600`; never commit it.

GitHub repository secrets used by both workflows:

| Secret | Content |
| --- | --- |
| `ANDROID_UPLOAD_KEYSTORE_B64` | Base64 upload keystore |
| `LITTER_UPLOAD_STORE_PASSWORD` | Keystore password |
| `LITTER_UPLOAD_KEY_ALIAS` | Key alias |
| `LITTER_UPLOAD_KEY_PASSWORD` | Private key password |
| `LITTER_PLAY_SERVICE_ACCOUNT_JSON_B64` | Base64 service-account JSON; needed only when publishing |
| `GOOGLE_SERVICES_JSON_B64` | Optional Base64 Firebase configuration |

The `LITTER_` names remain build-system compatibility identifiers; the published app is AgentBuddy / 搭子.

## After Google approves the developer account

1. Complete any remaining Play Console identity, Android device, and contact verification tasks.
2. Create **AgentBuddy / 搭子** in Play Console. Complete the app declarations and store setup with accurate privacy policy, data safety, content rating, app access, and contact information. Review all screenshots and graphics before publishing; changing text metadata does not update images.
3. Create an internal testing release and manually upload the first signed AAB from CI. Complete the Play App Signing setup using the intended upload certificate. Google Play requires the initial APK/AAB to be uploaded through the Console before API publishing works ([Gradle Play Publisher setup](https://github.com/Triple-T/gradle-play-publisher#initial-setup)).
4. In Play Console → Users and permissions, add `agentbuddy-play-ci@agentbuddy-45403.iam.gserviceaccount.com` with access to this app and permissions to view app information and release to testing tracks. Add store-listing permission only if using metadata publishing. Account administrator and production publishing access are not needed for the internal lane.
5. Verify the first internal release and service-account access. Set repository variable `ANDROID_PLAY_PUBLISH_ENABLED=true`, then run **Android Play Release** with `publish=true`, `track=internal`, `promote_track=none`. For an app still in draft state, select `release_status=draft`.
6. Once enabled, main-branch releases upload to **internal only**. Closed/open/production releases remain deliberate operations through the manual workflow or Console; the first internal release does not establish production eligibility. Follow the testing requirements shown for the developer account.

## Local commands

The script reads `~/.agentBuddy/signing/android/play-upload.env`, with the old `~/.config/litter/play-upload.env` as a fallback. `PLAY_UPLOAD_ENV_FILE` selects an explicit file. CI can use `/dev/null` to avoid local configuration. Required signing values are `LITTER_UPLOAD_STORE_FILE`, `LITTER_UPLOAD_STORE_PASSWORD`, `LITTER_UPLOAD_KEY_ALIAS`, and `LITTER_UPLOAD_KEY_PASSWORD`. Publishing also requires `LITTER_PLAY_SERVICE_ACCOUNT_JSON`.

```bash
# Build the native dependencies first; no Play upload.
make rust-android
UPLOAD=0 ./apps/android/scripts/play-upload.sh

# Only after first manual upload and Play permission setup.
LITTER_PLAY_RELEASE_STATUS=draft ./apps/android/scripts/play-upload.sh
```

The local command generates `apps/android/app/build/outputs/bundle/release/app-release.aab`. Set a new versionCode before a subsequent upload. Default track is `internal`; an empty `LITTER_PLAY_PROMOTE_TRACK` disables promotion. The requested release status and rollout fraction apply to the upload when there is no promotion, otherwise to each promotion destination.

Regression checks (no Android SDK, credentials, or network required):

```bash
python3 -m unittest discover -s apps/android/scripts/tests -v
bash -n apps/android/scripts/play-upload.sh
```
