# Package 01B — Dashboard visual refinement

Date: 2026-11-12 (Asia/Kathmandu). Ready for owner visual review and subsequent separately authorized REDMAGIC acceptance. **NOT Ready for Release.**

## Repository and preservation of Package 01

Repository `karim-kmorsi/AI-Project-Manager`, checkout `/home/karim/Projects/AI-Project-Manager`, branch `feature/github-snapshot-preview`.
Starting and ending HEAD: `f77c66d1ed6681920ad1747b1327dc5e17dba782`.
The initial working tree contained exactly the expected 29 Package 01 files, intentionally uncommitted. They were inspected and their bytes/hashes captured outside the repository before refinement. No unexpected unrelated edits were found. No reset, discard, branch change, commit, push or main changes occurred. The ending tree retains Package 01 plus the incremental changes listed below; nothing is staged. The original Package 01 report and evidence remain unchanged.

## Diagnosis and exact refinements

The header rectangle was caused by drawing the separate opaque launcher background (#06152D) in a 40 dp square over the Dashboard background (#0B101D, or the light background). Foreground corner pixels were already transparent. This was a Compose layering issue, not an opaque black border in the foreground, an asset crop or launcher masking.

The header now displays the existing transparent foreground PNG intact, without the separate launcher background layer. No logo file or derivative was created, no cropping/tint/filter applied, and all geometry/checkmark/sparkles/ribbon pixels remain unchanged. The supplied full original branding image remains untouched; it was not cropped to create an asset. All 14 launcher files match HEAD byte-for-byte. Screenshot-based tests verify that the logo corner now matches the header background in both appearance modes. Thus the rectangle could be removed through presentation alone without modifying artwork.

Incremental visual changes:

- Header vertical padding reduced from 8 to 4 dp; Settings/overflow retain 48 dp targets and application name remains fully available.
- Dashboard top padding reduced from 16 to 8 dp and inter-item gaps from 12 to 8 dp. Actual project heading/count retained.
- Search uses the existing Material outlined decoration over BasicTextField, with a 48 dp minimum rather than the prior 56 dp Material field minimum. It grows naturally for enlarged text, has localized accessibility labeling, content-aware RTL/LTR alignment and the same Search IME action. No extra search icon/control was added. The underlying name/description filter function is unchanged.
- Project-card horizontal padding reduced from 16 to 12 dp, vertical padding from 16 to 8 dp and internal gaps from 8 to 4 dp. Defaults of the reusable card remain unchanged for other consumers. Project titles explicitly use onSurface for stronger hierarchy, descriptions remain at two visible lines and manual-verification count copy is shorter.
- Existing ratio calculation is unchanged: manually VERIFIED task count divided by all tasks; zero tasks give a zero bar. No percentages or project-completion/status inference.
- Create remains an extended FAB, now docked inside Scaffold's measured bottomBar with system-navigation and IME padding. The scrolling viewport ends above this reserved area at every scroll position; protection is no longer limited to padding after the last row. List bottom padding is now 16 dp. Insets are applied once in the bottom area, avoiding duplicate keyboard consumption in the list. No fixed card heights.

The approved navy palette, subtle borders, typography family and functional capabilities are retained. No filters, project icons, dates, statuses or mockup sample projects were introduced. No other screen was redesigned.

## Reference comparison and remaining differences

The updated genuine screenshots are visibly denser than Package 01: smaller gaps, shorter cards, compact search, stronger titles, a transparent header logo and an unobstructed reserved Create area. The result follows the approved navy/card hierarchy while omitting unsupported filters, category icons and per-project menu actions. No claim of pixel-identical reproduction is made.

Long titles wrap naturally and 200% text necessarily produces taller cards; clipping them to reference-image heights would sacrifice readability. Some rows may be partially visible at the viewport edge, but they are clipped by the scrolling viewport above the docked button and can scroll fully into view. In Light mode, unchanged white logo details have less contrast against the light surface; no recolored variant or replacement artwork was invented. Owner visual review of that brand presentation remains appropriate.

## Tests and commands

Final local build/JVM/Lint command (JDK 17, installed Gradle 8.13/API 35, offline):

```bash
/home/karim/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle --offline -PgithubAppClientId=Iv23li4aYV0QXiSu8uWf -PrequireGitHubAppClientId=true :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

Result: **BUILD SUCCESSFUL**, 6 seconds. Debug and Android-test APKs assembled. **101 JVM tests passed, 0 failures/errors/skips.** Lint: **0 errors, 13 warnings**, unchanged in count from Package 01: ApplySharedPref (1), OldTargetApi (1), AndroidGradlePluginVersion (1), GradleDependency (1), ObsoleteSdkInt (1), IconLauncherShape (5), UseKtx (3). No warnings were suppressed.

The initial compile attempt failed because TextFieldColors was passed positionally to an API expecting Modifier; this was corrected to named arguments. Subsequent build batches succeeded. A development UI run exposed duplicate IME handling, corrected in the list. Obsolete runs were stopped on the disposable emulator only. The existing theme/scroll regression now submits Search before measuring scroll preservation; keyboard-open behavior is asserted separately in the focused Dashboard test. No assertions were removed or weakened.

The owner explicitly clarified the no-install restriction: **“Allow disposable emulator only.”** The REDMAGIC phone remained untouched. The retained disposable API 35 AVD was started without resetting/wiping it:

```bash
ANDROID_AVD_HOME=/tmp/ai-pm-vnext-emulator/avd /home/karim/Android/Sdk/emulator/emulator -avd AI_PM_VNext_Disposable -port 5582 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect
/home/karim/Android/Sdk/platform-tools/adb -P 5038 start-server
/home/karim/Android/Sdk/platform-tools/adb -P 5038 -s emulator-5582 emu avd name
/home/karim/Android/Sdk/platform-tools/adb -P 5038 -s emulator-5582 shell getprop ro.build.version.sdk
/home/karim/Android/Sdk/platform-tools/adb -P 5038 -s emulator-5582 install -r -t app/build/outputs/apk/debug/app-debug.apk
/home/karim/Android/Sdk/platform-tools/adb -P 5038 -s emulator-5582 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Verified AVD AI_PM_VNext_Disposable, serial emulator-5582, API 35, 1080x1920 at 480 dpi (360x640 dp), matching signing certificate. In-place emulator installs succeeded; no uninstall or data clear occurred. Test fixtures inject locales/font scale without changing system settings.

Final focused Dashboard command:

```bash
/home/karim/Android/Sdk/platform-tools/adb -P 5038 -s emulator-5582 shell am instrument -w -r -e class com.karim.aiprojectmanager.DashboardVNextUiTest com.karim.aiprojectmanager.test/androidx.test.runner.AndroidJUnitRunner
```

**6 passed, 0 failed/skipped**, 26.393 seconds. Tests cover actual counts, unknown statuses, zero tasks, two-line descriptions, query trimming/no matches, selection, Create validation/Cancel, settings/overflow callbacks, read failure/retry, byte preservation, transparent logo corners, 48–52 dp normal-font search, keyboard-open controls and nonoverlapping list/Create geometry. Cases: Arabic Dark, English Dark, Arabic Light, Arabic Dark 200%, English Light 200%, corrupt/empty state.

Final existing-regression command:

```bash
/home/karim/Android/Sdk/platform-tools/adb -P 5038 -s emulator-5582 shell am instrument -w -r -e class com.karim.aiprojectmanager.ProjectWorkflowUiTest,com.karim.aiprojectmanager.ProjectCreationAccessibilityUiTest,com.karim.aiprojectmanager.SettingsUiTest,com.karim.aiprojectmanager.ThemeUiTest,com.karim.aiprojectmanager.AccessibilityLayoutUiTest,com.karim.aiprojectmanager.StorageFailureUiTest,com.karim.aiprojectmanager.ManualSaveNavigationUiTest,com.karim.aiprojectmanager.GitHubRepositoriesUiTest,com.karim.aiprojectmanager.GitHubRepositoryApiTest,com.karim.aiprojectmanager.GitHubRepositoryPreviewApiTest,com.karim.aiprojectmanager.GitHubSignInUiTest,com.karim.aiprojectmanager.GitHubAuthHttpTest,com.karim.aiprojectmanager.BackupPreviewAccessibilityUiTest,com.karim.aiprojectmanager.ReleaseWorkflowRegressionTest,com.karim.aiprojectmanager.DocumentResultUiTest,com.karim.aiprojectmanager.JsonExportTest,com.karim.aiprojectmanager.LegacyStorageTest com.karim.aiprojectmanager.test/androidx.test.runner.AndroidJUnitRunner
```

**85 passed, 0 failed/skipped**, 76.398 seconds. This includes appearance persistence/Follow System, editor save/guard checks, project creation, storage compatibility, GitHub previews/authentication fixtures and backup/export provider callbacks. Combined final Android scope: **91 passed, 0 failed/skipped** across two nonoverlapping runs. This is not the complete Android suite or physical-device verification.

Inspection/verification commands also included git status/rev-parse/diff/diff --check, cat/rg/sed, Python/Pillow hashes and PNG validation, reference-image viewing, and apksigner verify --print-certs. Source storage/model/editor/creation-callback sections were compared against the captured Package 01 baseline and are unchanged. Screenshots were obtained with serial-targeted `adb exec-out run-as ... cat cache/vnext01b-<locale>-<dark>-<scale>.png` after real Compose UI tests; they were not fabricated or edited.

## Preservation and remaining risks

Original logo input unchanged; all launcher resources unchanged. JSON models/storage/compatibility and stored task statuses unchanged. No drafts, autosave, GitHub, import, export/backup, signing, package, version or dependency changes. No real project data was used. Synthetic read-only navigation preserves original project bytes, including unknown extensions. EmbCo and unrelated repositories were not accessed or modified. All Package 01 changes not listed below remain byte-identical to the captured baseline. Existing reports/screenshots were preserved.

Functional project-status filters remain deferred. Non-Dashboard localization and other-screen redesign remain outside this package. TalkBack spoken traversal, OEM/system-bar rendering, actual process death, release signing and physical-device data continuity remain unverified. No production-readiness claim.

## Deliverables and REDMAGIC acceptance

APK: `/home/karim/Projects/AI-Project-Manager/app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `4f7109e644baa9fe1615aeb1e5892ac6603e8eafba035778ce23ebc620bd3f14`.
Certificate SHA-256: `301a9e4f477d0e53116caaa7bb8856afce8fd721aa0e4897d4b8ce7139e6e65f`.
No phone install was performed or is authorized by this report. Subsequent device testing requires separate owner approval and a matching installed signing certificate. Never uninstall or clear data to bypass incompatibility.

Owner checklist: compare normal Arabic/English Dark with the reference; review unchanged-logo visibility in Light; verify long titles/two-line descriptions/counts; open keyboard and ensure search/Create remain reachable; scroll the last card fully clear of Create; test narrow/large-font layout and Dark/Light/Follow System; select/back, create/cancel, settings/overflow; verify existing data after restart and export/preview cancellation. Existing editor safeguards should remain intact.

Screenshot paths (synthetic fixture data only):

- `docs/audit-evidence/ui-vnext-package-01b/dashboard-ar-dark-2x.png`
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-ar-dark.png`
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-ar-light.png`
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-en-dark.png`
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-en-light-2x.png`

## Files changed by 01B relative to uncommitted Package 01

- `app/src/androidTest/java/com/karim/aiprojectmanager/DashboardVNextUiTest.kt` — Add logo/compact-search/zero-count/keyboard/nonoverlap assertions and fresh screenshots.
- `app/src/androidTest/java/com/karim/aiprojectmanager/ThemeUiTest.kt` — Submit Search before checking theme/scroll preservation; retain all assertions.
- `app/src/main/java/com/karim/aiprojectmanager/DashboardScreen.kt` — Header layering/spacing, compact accessible search, card density, docked Create area and single IME handling.
- `app/src/main/java/com/karim/aiprojectmanager/MainActivity.kt` — Use Scaffold bottomBar instead of floatingActionButton for Dashboard Create; retain callback/state.
- `app/src/main/java/com/karim/aiprojectmanager/ProjectDesignSystem.kt` — Optional card padding/spacing parameters; existing defaults preserved.
- `app/src/main/res/values-ar/dashboard.xml` — Shorter localized manual-verification label; calculation unchanged.
- `app/src/main/res/values/dashboard.xml` — Shorter localized manual-verification label; calculation unchanged.
- `docs/PROJECT_STATUS.md` — append the 01B implementation/evidence handoff.
- `docs/UI_UX_VNEXT_PACKAGE_01B_REPORT.md` — this report.
- `docs/audit-evidence/ui-vnext-package-01b/build.log` — Final validation output or asset-integrity metadata.
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-ar-dark-2x.png` — Genuine synthetic-data screenshot.
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-ar-dark.png` — Genuine synthetic-data screenshot.
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-ar-light.png` — Genuine synthetic-data screenshot.
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-en-dark.png` — Genuine synthetic-data screenshot.
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-en-light-2x.png` — Genuine synthetic-data screenshot.
- `docs/audit-evidence/ui-vnext-package-01b/dashboard-tests.log` — Final validation output or asset-integrity metadata.
- `docs/audit-evidence/ui-vnext-package-01b/regression-tests.log` — Final validation output or asset-integrity metadata.
- `docs/audit-evidence/ui-vnext-package-01b/verification.json` — Final validation output or asset-integrity metadata.

Generated ignored build outputs are not additional source changes. Ready for owner visual review and subsequent separately authorized REDMAGIC testing.
