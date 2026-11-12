# UI/UX VNext Package 01 — implementation and validation

Date: 2026-11-12 (Asia/Kathmandu). Status: ready for owner visual review and controlled device acceptance after signing compatibility is checked. **NOT Ready for Release.** No phone installation was performed.

## Repository

Checkout `/home/karim/Projects/AI-Project-Manager`; origin `karim-kmorsi/AI-Project-Manager`.
Branch `feature/github-snapshot-preview`. Starting and ending HEAD are both `f77c66d1ed6681920ad1747b1327dc5e17dba782`.
The starting tree was clean. The ending tree contains only the local, uncommitted presentation, test, documentation and synthetic-evidence changes listed below. The existing one-commit lead over the recorded upstream is unchanged. No commits, push, branch switch, reset or main changes.

## Implemented scope

Only Design System and Dashboard were implemented. All six images were inspected; the other four screen mockups supplied future consistency context only. Written requirements took precedence over unsupported mockup content.

- Semantic dark palette: dominant #0B101D background, #172131 surfaces, #111D2D search field, #354258 decorative borders, #64748B interactive outlines, existing blue/cyan actions. Light mode retains its light palette. Follow System and preference behavior are unchanged. No gradients/glow were needed.
- Reusable spacing (4/8/12/16/24/32 dp), system-font typography roles, 12 dp project-card shape, section headings, bordered project cards, feedback blocks and secondary action presentation.
- Compact Dashboard header: application name, existing logo layers, 48 dp Settings/overflow targets and existing export/backup-preview actions.
- Real project count, unchanged name/description substring search, IME Search dismissal, distinct no-project/no-match/read-error states and retry.
- Project cards: actual name, description limited to two visible lines with ellipsis, actual manually VERIFIED/total counts and a thin ratio indicator. Unknown statuses remain untouched. Zero tasks yield a zero bar, without a completion dot or percentage.
- Extended Create Project action, keyboard-aware positioning and trailing list clearance. Existing project-selection and creation callbacks/validation remain unchanged.
- English/Arabic Dashboard and Create-dialog resources, content-driven paragraph direction, preserved LTR numeric isolation, heading and feedback semantics. Progress graphics are decorative to assistive technology; the truthful count text supplies the meaning.

No All/Active/Completed/Archived controls were introduced: functional project-status filtering requires separately approved model/behavior work. No dates, versions, project categories, per-project menus or sample projects were added to production.

## Brand and data preservation

The original supplied logo remains untouched in the input ZIP. Its SHA-256 is `bd02155d91085e214fda5069ef47daef37e0ac6c010f625f192d1cbca480e7ea`.
All 14 existing launcher resources match HEAD byte-for-byte. The header displays the existing foreground/background PNG layers in identical bounds without tint, crop, filters, regeneration or new logo files. It does not use any mockup logo. The full branding image is not cropped to manufacture a header asset.

TaskItem/ProjectItem, ProjectStorage/persistProjectChange, project-creation callback, and EditableDocument/recovery/confirmation implementation sections were compared with HEAD and are unchanged. No project JSON schema, stored statuses, migration, draft timing, export format or GitHub logic changed. Synthetic read-only navigation tests preserve original bytes, including unknown project/task extensions. No actual phone projects were read or modified. Package, signing, version and dependency files remain unchanged. EmbCo and other repositories were not accessed for implementation.

Shared effects outside Dashboard are limited to dark background/surface/outline colors and explicit use of the existing Material typography metrics. The native night-theme background now matches #0B101D. No other screen layout or navigation was redesigned. Existing editor, Settings/appearance, GitHub and export/backup regressions were exercised.

## Validation and exact commands

The local Gradle 8.13/JDK 17 toolchain and installed API 35 SDK were reused offline. Final configured build command:

```bash
/home/karim/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle --offline -PgithubAppClientId=Iv23li4aYV0QXiSu8uWf -PrequireGitHubAppClientId=true :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

Result: BUILD SUCCESSFUL, 5 seconds; 101 JVM tests passed, 0 failures/errors/skips; Debug APK and Android-test APK assembled. Lint: 0 errors, 13 warnings: ApplySharedPref (1), OldTargetApi (1), AndroidGradlePluginVersion (1), GradleDependency (1), ObsoleteSdkInt (1), IconLauncherShape (5), UseKtx (3). No suppressions or signing/dependency changes were made.

The same task batch ran during development as changes were validated: initially unconfigured, then configured using the existing repository's public APP_CLIENT_ID. All build batches succeeded. The public variable was read with `gh api repos/karim-kmorsi/AI-Project-Manager/actions/variables/APP_CLIENT_ID --jq .value`; no GitHub configuration was changed. The final APK includes that ID; authentication tokens were never requested.

A newly created disposable emulator was used, not an existing AVD: AI_PM_VNext_Disposable, emulator-5582, API 35, 1080x1920, 480 dpi (360x640 dp). Tests injected Arabic/English layout and 100%/200% font scale rather than changing phone settings. Creation/launch commands:

```bash
ANDROID_AVD_HOME=/tmp/ai-pm-vnext-emulator/avd /home/karim/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd -n AI_PM_VNext_Disposable -k 'system-images;android-35;google_apis_playstore;x86_64' -p /tmp/ai-pm-vnext-emulator/avd/AI_PM_VNext_Disposable.avd --device pixel_6
ANDROID_AVD_HOME=/tmp/ai-pm-vnext-emulator/avd /home/karim/Android/Sdk/emulator/emulator -avd AI_PM_VNext_Disposable -port 5582 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect
```

Emulator configuration was confined to its new /tmp AVD. SDK/AVD/API identity and package absence were checked before first install. Only this serial received install commands:

```bash
/home/karim/Android/Sdk/platform-tools/adb -s emulator-5582 install -t app/build/outputs/apk/debug/app-debug.apk
/home/karim/Android/Sdk/platform-tools/adb -s emulator-5582 install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Subsequent emulator-only replacements used `install -r -t` with the same signing certificate. Initial standard-port ADB reporting disconnected; the ongoing 90-test run completed according to its recovered per-process TestRunner log (86 passed, four obsolete Arabic-heading test selectors failed). To isolate transport, a dedicated server was started with `adb -P 5038 start-server` and connected to localhost:5583. The 91-test scoped run command was:

```bash
/home/karim/Android/Sdk/platform-tools/adb -P 5038 -s emulator-5582 shell am instrument -w -r -e class com.karim.aiprojectmanager.DashboardVNextUiTest,com.karim.aiprojectmanager.ProjectWorkflowUiTest,com.karim.aiprojectmanager.ProjectCreationAccessibilityUiTest,com.karim.aiprojectmanager.SettingsUiTest,com.karim.aiprojectmanager.ThemeUiTest,com.karim.aiprojectmanager.AccessibilityLayoutUiTest,com.karim.aiprojectmanager.StorageFailureUiTest,com.karim.aiprojectmanager.ManualSaveNavigationUiTest,com.karim.aiprojectmanager.GitHubRepositoriesUiTest,com.karim.aiprojectmanager.GitHubRepositoryApiTest,com.karim.aiprojectmanager.GitHubRepositoryPreviewApiTest,com.karim.aiprojectmanager.GitHubSignInUiTest,com.karim.aiprojectmanager.GitHubAuthHttpTest,com.karim.aiprojectmanager.BackupPreviewAccessibilityUiTest,com.karim.aiprojectmanager.ReleaseWorkflowRegressionTest,com.karim.aiprojectmanager.DocumentResultUiTest,com.karim.aiprojectmanager.JsonExportTest,com.karim.aiprojectmanager.LegacyStorageTest com.karim.aiprojectmanager.test/androidx.test.runner.AndroidJUnitRunner
```

Result: 86 passed, 5 failed, 0 skipped. All five failures were newly added screenshot setup trying to locate an offscreen lazy-list item. The fixture now scrolls to its item index before querying it and inspects the heading in the unmerged tree. All save, byte-preservation, visibility and accessibility assertions were retained. Production sources were unchanged between this run and the focused rerun.

Final focused command:

```bash
/home/karim/Android/Sdk/platform-tools/adb -P 5038 -s emulator-5582 shell am instrument -w -r -e class com.karim.aiprojectmanager.DashboardVNextUiTest com.karim.aiprojectmanager.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **6 passed, 0 failed/skipped**, 25.173 seconds. These supersede the five screenshot-fixture failures; **91 distinct scoped Android tests have passing results across the broad run and focused rerun**. This is not a claim that the entire Android suite was run.

Earlier development failures were diagnosed rather than hidden: adaptive XML cannot be loaded by painterResource (fixed by reusing the existing two PNGs), animation-dependent extended-FAB semantics (fixed with the static-content extended-FAB overload), injected-locale popup strings (resolved labels before opening native popup/dialog windows), outdated localized test selectors and lazy-list screenshot positioning. Earlier targeted runs returned 5 failures/5, 1/1, 4/5, and 2/5 while these issues were being corrected; the last pre-final Dashboard rerun had one no-match-heading fixture failure out of six. Final results above supersede those development iterations.

Other inspection commands: git status/rev-parse/remote/diff/diff --check; rg/find/cat/sed/nl for source and test inspection; unzip inventory and safe extraction to /tmp; view_image for six references and screenshots; Python/Pillow read-only logo hashes and image validation; apksigner verify --print-certs. No cloud CI or physical-device operations ran.

## Evidence and screenshots

`docs/audit-evidence/ui-vnext-package-01/` contains final build output, the broad and corrected focused instrumentation logs, verification.json and five PNG screenshots. All screenshot project data is synthetic fixture data, not mockup sample projects or real owner data. Screenshots cover normal Arabic/English dark, Arabic dark 200%, Arabic light, English light 200%. Their purpose is visual review, not physical-device certification.

## APK and controlled REDMAGIC acceptance

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `b4a57de1f39152a1a5911e307e1a2d40a38ca722597c5309aabee9f49073580c`.
Signing certificate SHA-256: `301a9e4f477d0e53116caaa7bb8856afce8fd721aa0e4897d4b8ce7139e6e65f`.

No installation is authorized by this report. Before an owner-approved update, compare the installed certificate with this APK and confirm package/version compatibility. If certificates differ, stop; do not uninstall, clear data, change keys, use downgrade flags or attempt a workaround. Preserve an external export where available. If compatibility and separate installation approval are confirmed, use a targeted in-place update (`adb -s <verified-phone-serial> install -r <absolute-apk-path>`), never the test APK. This command was not executed on the phone.

Manual checklist: verify existing project/task counts and content; search by name and description including whitespace/no matches; select a project and return; Settings and both overflow actions; Create validation, Cancel and Save; last-card access; Arabic/English mixed text; keyboard visible/hidden; 200% font and larger display scaling; Dark/Light/Follow System and restart persistence; unchanged editor draft/save guards; export and read-only preview cancellation. TalkBack traversal and the actual REDMAGIC launcher/header/system bars need owner testing.

The disposable emulator was stopped after evidence collection; its isolated temporary AVD was retained.

## Remaining scope and limitations

Project status filters, archive/completion models, richer project metadata, persistent GitHub import/sync and all other screen redesigns are deferred. No fake controls were added. Existing non-Dashboard screens are still partially localized. Actual process death, physical-device behavior, OEM backup behavior and release signing/data-continuity gates remain open. No production readiness is claimed.

## Complete reviewable file inventory

- `app/src/androidTest/java/com/karim/aiprojectmanager/AccessibilityLayoutUiTest.kt` — Update localized Dashboard action/heading selectors; retain existing regression assertions.
- `app/src/androidTest/java/com/karim/aiprojectmanager/DashboardVNextUiTest.kt` — Six isolated Dashboard locale/font/navigation/data-safety regressions and screenshots.
- `app/src/androidTest/java/com/karim/aiprojectmanager/DocumentResultUiTest.kt` — Reach localized existing provider actions through the overflow menu; retain callback/data assertions.
- `app/src/androidTest/java/com/karim/aiprojectmanager/ManualSaveNavigationUiTest.kt` — Update localized Dashboard action/heading selectors; retain existing regression assertions.
- `app/src/androidTest/java/com/karim/aiprojectmanager/ProjectCreationAccessibilityUiTest.kt` — Update localized Dashboard action/heading selectors; retain existing regression assertions.
- `app/src/androidTest/java/com/karim/aiprojectmanager/ProjectWorkflowUiTest.kt` — Update localized Dashboard action/heading selectors; retain existing regression assertions.
- `app/src/androidTest/java/com/karim/aiprojectmanager/SettingsUiTest.kt` — Update localized Dashboard action/heading selectors; retain existing regression assertions.
- `app/src/androidTest/java/com/karim/aiprojectmanager/StorageFailureUiTest.kt` — Update localized Dashboard action/heading selectors; retain existing regression assertions.
- `app/src/androidTest/java/com/karim/aiprojectmanager/ThemeUiTest.kt` — Update localized Dashboard action/heading selectors; retain existing regression assertions.
- `app/src/main/java/com/karim/aiprojectmanager/AccessibleLayouts.kt` — Localize Create-dialog copy without changing layout/validation.
- `app/src/main/java/com/karim/aiprojectmanager/AppTheme.kt` — Shared semantic navy palette and existing typography metrics.
- `app/src/main/java/com/karim/aiprojectmanager/DashboardScreen.kt` — Dashboard header, cards, search, counts, states and extended Create action.
- `app/src/main/java/com/karim/aiprojectmanager/MainActivity.kt` — Delegate Dashboard rendering to presentation components; retain state and callbacks.
- `app/src/main/java/com/karim/aiprojectmanager/ProjectDesignSystem.kt` — Reusable spacing, typography roles, cards, headings and feedback.
- `app/src/main/res/values-ar/dashboard.xml` — Localized Dashboard/Create UI-owned copy.
- `app/src/main/res/values-night/themes.xml` — Match native night window colors to the shared navy background.
- `app/src/main/res/values/dashboard.xml` — Localized Dashboard/Create UI-owned copy.
- `app/src/test/java/com/karim/aiprojectmanager/ThemeColorsTest.kt` — Update expected approved navy tokens; retain contrast assertions.
- `docs/PROJECT_STATUS.md` — Current implementation/evidence handoff.
- `docs/UI_UX_VNEXT_PACKAGE_01_REPORT.md` — Complete implementation and validation report.
- `docs/audit-evidence/ui-vnext-package-01/broad-instrumentation.log` — Preserved validation output and integrity metadata.
- `docs/audit-evidence/ui-vnext-package-01/build.log` — Preserved validation output and integrity metadata.
- `docs/audit-evidence/ui-vnext-package-01/dashboard-ar-dark-2x.png` — Synthetic screenshot evidence.
- `docs/audit-evidence/ui-vnext-package-01/dashboard-ar-dark.png` — Synthetic screenshot evidence.
- `docs/audit-evidence/ui-vnext-package-01/dashboard-ar-light.png` — Synthetic screenshot evidence.
- `docs/audit-evidence/ui-vnext-package-01/dashboard-en-dark.png` — Synthetic screenshot evidence.
- `docs/audit-evidence/ui-vnext-package-01/dashboard-en-light-2x.png` — Synthetic screenshot evidence.
- `docs/audit-evidence/ui-vnext-package-01/dashboard-instrumentation.log` — Preserved validation output and integrity metadata.
- `docs/audit-evidence/ui-vnext-package-01/verification.json` — Preserved validation output and integrity metadata.

Generated Gradle outputs are ignored build artifacts, not additional source edits. The APK path and test/report outputs are identified above. No source or user files were deleted; existing tests clean up their own synthetic fixtures.
