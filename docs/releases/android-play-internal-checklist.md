# Android Play setup and first release

Package: `com.akashark.agentbuddy.android`. Release variant: `Release`.

## Before the first Play upload

Signing and builds do not require an approved Play developer account. Run **Android Play Release** in GitHub Actions with `publish=false` (the default). Download `android-play-release-<run number>` from the completed run; it contains a signed APK, AAB, and SHA-256 checksums. Use the APK for direct installation and keep the AAB for the first Play Console upload.

New submissions must target API 36 from August 31, 2026 ([Google Play requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-EN)). The app and CI now compile against API 36 and target API 36. CameraX uses 1.4.2 to replace the older image-processing library with 16 KB LOAD alignment. Rebuild after these changes; older API 35 / CameraX 1.3.4 artifacts are not the first-submission candidate. Verify the final AAB in Play Console, including native-library/page-size checks, before rolling out to testers.

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

Repository variables (Settings → Secrets and variables → Actions → Variables):

| Variable | Initial value | When to change |
| --- | --- | --- |
| `ANDROID_PLAY_PUBLISH_ENABLED` | `false` | Set `true` only after the first manual AAB upload and service-account access work |
| `ANDROID_PLAY_RELEASE_STATUS` | `draft` | Set `completed` after Play allows releases to internal testers |

`Mobile Release` defaults to `draft` if the status variable is absent; it accepts only `draft` or `completed`. A draft upload does not distribute a build to testers. The manual workflow uses its own `release_status` input, also defaulting to `draft`. Automatic uploads always target `internal`.

## After Google approves the developer account

1. Complete any remaining Play Console identity, Android device, and contact verification tasks.
2. Create **AgentBuddy / 搭子** in Play Console. Complete the app declarations and store setup with accurate privacy policy, data safety, content rating, app access, and contact information. Review all screenshots and graphics before publishing; changing text metadata does not update images.
3. Create an internal testing release and manually upload the first signed AAB from CI. Complete the Play App Signing setup using the intended upload certificate. Google Play requires the initial APK/AAB to be uploaded through the Console before API publishing works ([Gradle Play Publisher setup](https://github.com/Triple-T/gradle-play-publisher#initial-setup)).
4. In Google Cloud project `agentbuddy-45403`, enable **Google Play Android Developer API** (`androidpublisher.googleapis.com`). In Play Console → Users and permissions, add `agentbuddy-play-ci@agentbuddy-45403.iam.gserviceaccount.com` with access to this app and permissions to view app information and release to testing tracks. Add store-listing permission only if using metadata publishing. Account administrator and production publishing access are not needed for the internal lane. Cloud IAM permissions alone do not grant Play app access; linking the Cloud project to the developer account is no longer required ([Google API setup](https://developers.google.com/android-publisher/getting_started)).
5. Check API access with the preflight command below. Set repository variable `ANDROID_PLAY_RELEASE_STATUS=draft`, then `ANDROID_PLAY_PUBLISH_ENABLED=true`. Run **Android Play Release** with `publish=true`, `track=internal`, `promote_track=none`, `release_status=draft`. Once the app is ready for tester distribution, use `completed` and set the automatic status variable to `completed` too.
6. Once enabled, main-branch releases upload to **internal only**. Closed/open/production releases remain deliberate operations through the manual workflow or Console; the first internal release does not establish production eligibility. Follow the testing requirements shown for the developer account.

For personal developer accounts created after November 13, 2023, production access requires a **closed** test with at least 12 opted-in testers continuously for 14 days, followed by a production-access application. Internal testing does not satisfy this requirement ([Google testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)).

## 首次配置操作顺序

1. 打开 [Play Console](https://play.google.com/console/)，创建应用。名称使用 AgentBuddy / 搭子；选择默认语言、应用类型和实际收费方式，完成 Console 提示的声明。AAB 的包名固定为 `com.akashark.agentbuddy.android`。
2. 将本次 CI/SDK 修改合入发布分支，运行 **Android Play Release**，保持 `publish=false`。下载本次运行的签名 `app-release.aab`；不要拿旧 API 35 产物当作新版本。
3. 进入「测试和发布 → 测试 → 内部测试」，创建版本、配置 Play App Signing、上传该 AAB。保留现有 upload keystore；它是后续 CI 的上传签名凭据。Play 应用签名证书与上传证书是两种证书。
4. 按上述服务账号邮箱配置该应用的查看和测试轨道发布权限。GitHub 中已有签名和服务账号 Secrets 时，无需重新创建密钥。
5. 在 Console 配置测试人员名单，取得加入测试链接。草稿状态不会给测试人员分发应用；发布内部测试版本后再使用加入链接安装。
6. 完善商品详情（名称、简介、详细介绍、图标、截图、联系邮箱），以及隐私政策、数据安全、广告、内容分级、目标受众和应用访问权限等 Console 要求的内容。内部测试可先于完整商店资料开始；封闭测试/正式上架应完成这些要求。涉及远程主机配对或登录的功能，需要给审核人员可实际使用的访问方式和操作说明。
7. 运行下面的 API 检查，成功后再开启自动上传变量。首次保持 `draft`；内部测试流程可正常分发后再改 `completed`。

不需要为 CI 配置个人 Google 密码或开发者 ID；当前发布插件使用已有服务账号 JSON 和固定应用包名。

## Local commands

The script reads `~/.agentBuddy/signing/android/play-upload.env`, with the old `~/.config/litter/play-upload.env` as a fallback. `PLAY_UPLOAD_ENV_FILE` selects an explicit file. CI can use `/dev/null` to avoid local configuration. Required signing values are `LITTER_UPLOAD_STORE_FILE`, `LITTER_UPLOAD_STORE_PASSWORD`, `LITTER_UPLOAD_KEY_ALIAS`, and `LITTER_UPLOAD_KEY_PASSWORD`. Publishing also requires `LITTER_PLAY_SERVICE_ACCOUNT_JSON`.

```bash
# Check Google authentication, package and source-track access without publishing.
python3 apps/android/scripts/play-preflight.py \
  --service-account "$HOME/.agentBuddy/signing/android/play/service-account.json"

# Build the native dependencies first; no Play upload.
make rust-android
UPLOAD=0 ./apps/android/scripts/play-upload.sh

# Only after first manual upload and Play permission setup.
LITTER_PLAY_RELEASE_STATUS=draft ./apps/android/scripts/play-upload.sh
```

The local command generates `apps/android/app/build/outputs/bundle/release/app-release.aab`. Set a new versionCode before a subsequent upload. Default track is `internal`; an empty `LITTER_PLAY_PROMOTE_TRACK` disables promotion. The requested release status and rollout fraction apply to the upload when there is no promotion, otherwise to each promotion destination.

Both workflows run `play-preflight.py` before the Android native build when publishing is enabled. It uses Python's standard library and openssl via the existing store-triage token helper. It creates a temporary Play edit, lists tracks, then discards that edit without uploading or committing a release. Do not run it concurrently with another publisher using the same service account: Play edits share account/application state. Passing preflight confirms authentication and edit/track access, not every publishing permission, declaration or review requirement.

- HTTP 401: check the service-account key.
- HTTP 403: check API enablement and Play Console app permissions.
- HTTP 404: check application creation, first manual upload, package name and app access.

Regression checks (no Android SDK, credentials, or network required):

```bash
python3 -m unittest discover -s apps/android/scripts/tests -v
bash -n apps/android/scripts/play-upload.sh
```
