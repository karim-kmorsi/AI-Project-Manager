# GitHub session Phase 2A report — 2026-11-13

## Project state

- Branch: `feature/github-snapshot-preview`
- Starting and current HEAD: `7c0388fb18d05ae5e986099ff9af2a1c20031235`
- Changes are uncommitted. No commit or push was made.
- The pre-existing `app/build.gradle.kts` modification was preserved and corrected so an empty Client ID blocks VNext tasks without blocking ordinary Debug builds.

## Implementation

- Added `GitHubSessionPersistence.kt`: production sessions are encrypted with AES-256-GCM using an Android Keystore key, a fresh IV for each write, authenticated version data, and `AtomicFile` under `noBackupFilesDir`. Only ciphertext is written. Corrupt ciphertext and missing/invalidated keys are discarded safely; transient Keystore and IO errors retain the saved data for retry. A memory-only persistence implementation supports unit tests.
- Updated `GitHubSignIn.kt`, `GitHubDeviceAuth.kt`, and `GitHubAuthHttp.kt`: successful auth persists the token and account; startup restores only after `/user` verification; HTTP 401 is treated as unauthorized, while network/DNS/timeout/other HTTP errors retain saved credentials. Disconnect cancels active work, drops the live session and deletes saved credentials. It does not revoke authorization remotely.
- Injected encrypted persistence in `MainActivity.kt`; added localized restore, retry and disconnect status/actions in `GitHubConnectionSettings.kt` and the Arabic/English UI resources.
- Android backup and device-transfer exclusions were already present and remain unchanged.
- VNext build guard is task-scoped: empty Client ID allows `assembleDebug` and causes VNext tasks to fail with a clear configuration message. The existing local `githubAppClientId` property and GitHub Actions `APP_CLIENT_ID` variable remain the configuration sources.
- Updated `.github/workflows/android-build.yml` to build and run instrumentation against the isolated VNext package. Added VNext Compose test-host dependency/source-set configuration. Fixed `GitHubSignInUiTest.kt` assertions to use localized resources instead of hardcoded Arabic labels, so the same tests run under English or Arabic locales.

## Results

**FIXED — VERIFIED**

- Host unit tests: `:app:testVnextUnitTest` — 112 passed, 0 failed, 0 errors, 0 skipped.
- Builds: `:app:assembleDebug`, `:app:assembleVnext`, and `:app:assembleVnextAndroidTest` passed with the configured public Client ID.
- Client ID guard: empty ID `:app:assembleDebug` passed; empty ID `:app:assembleVnext` failed with the expected `VNext requires githubAppClientId` message.
- Physical REDMAGIC 10 Pro (`FY2433810B9D`, Android 15), VNext-only focused instrumentation: 12 passed, 0 failed, 0 skipped across encrypted persistence, HTTP error classification and GitHub sign-in UI tests. These use synthetic credentials and do not authenticate a real account.
- VNext APK application ID verified as `com.karim.aiprojectmanager.vnext`. Only that package was installed/updated. Read-only package checks show both VNext and original `com.karim.aiprojectmanager` installed; the original package path was unchanged. No original-app data was cleared or modified.
- Actual VNext home and Settings captures: `docs/audit-evidence/github-session-phase2a-2026-11-13/vnext-home-before-owner-auth.png` and `docs/audit-evidence/github-session-phase2a-2026-11-13/vnext-settings-before-owner-auth.png`. VNext is left open at Settings with the Connect GitHub action visible for owner testing.
- `git diff --check` passed.
- The initial focused UI run exposed two test-harness problems: no Compose host activity in the custom VNext variant and Arabic-only test labels on an English device. Both were corrected; the rerun passed all four UI tests.

## NOT TESTED / owner action required

No real GitHub sign-in was started. The owner must open Settings → GitHub in VNext, start device authorization, enter the displayed code on GitHub, and approve the requested access. Do not share the code in chat. Once the owner confirms authorization is complete, verify the connected account, close/relaunch and terminate/relaunch restoration, authenticated repository access, disconnect, and disconnected state after restart on the isolated VNext app. No live OAuth request, account authorization, process-death sequence, or authenticated repository access has been verified yet.

**UNRESOLVED:** authenticated physical-device persistence and repository access depend on owner authorization. Therefore the final recommendation is **NEEDS CORRECTION / INCOMPLETE VERIFICATION**, not PASS. Keep the project **NOT Ready for Release**.
