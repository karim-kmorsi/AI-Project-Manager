# VNext Phase 1B — visual alignment and manual language

Date: 2026-11-12  
Repository: `karim-kmorsi/AI-Project-Manager`  
Branch: `feature/github-snapshot-preview`  
Baseline / current HEAD: `6e8c14b10414c49acc7651a34391dfc0c03fb025`  
Device: REDMAGIC 10 Pro (`FY2433810B9D`)  
APK package: `com.karim.aiprojectmanager.vnext`

## Project state

Branch and HEAD matched the requested baseline at the start. The worktree was clean. The worktree now contains only the uncommitted Phase 1B source, test, documentation, and screenshot evidence listed below. No commit or push was made.

## Changes

- Added a Settings → Appearance → Language selector with exactly two choices: العربية and English. There is no system/default/automatic option.
- Added a small language preference stored separately from project data. A missing or unknown value resolves to English. The Activity creates a locale-specific context with the selected text direction, saves a changed choice locally, and recreates to apply it immediately. Device locale is unchanged.
- Reorganized the Tasks creation input and Add action into one horizontal row on normal-width screens. Narrow layouts and font scale above 1.3 retain the stacked form. Existing task creation/update callbacks and data format are unchanged.
- Kept the approved logo artwork, compact section navigation, project/task content, theme selection, and authentication implementation intact.

### Files

- `app/src/main/java/com/karim/aiprojectmanager/AppLanguage.kt` — two-language model, preference storage, and locale context.
- `app/src/main/java/com/karim/aiprojectmanager/AppearanceSettings.kt` — language choice UI within Appearance.
- `app/src/main/java/com/karim/aiprojectmanager/MainActivity.kt` — apply and persist selection; responsive Tasks form layout.
- `app/src/main/res/values/ui.xml`, `app/src/main/res/values-ar/ui.xml` — localized language selector labels and save error.
- `app/src/test/java/com/karim/aiprojectmanager/AppLanguageTest.kt` — default, unknown-value, and language-tag tests.
- `docs/UI_UX_VNEXT_PHASE1B_REPORT_2026-11-12.md`, `docs/UI_UX_VNEXT_CONTINUATION.md`, `docs/PROJECT_STATUS.md` — phase record and current status.
- Screenshots: `docs/audit-evidence/ui-vnext-correction-2026-11-12/`.

## Error detection and repair

- **FIXED — VERIFIED:** The Appearance section had no manual language selector. Root cause: app strings followed the attached Android locale without an app-level preference. Added only the two requested choices, a persisted preference, and a localized Activity context; device language remains independent. Verified English → Arabic → English switching, immediate RTL/LTR update, and preference restoration after force-stop/relaunch and APK update on the device. Host tests cover the English default and both language tags.
- **FIXED — VERIFIED:** The Tasks title input and Add action occupied separate vertical rows on this normal-width device. Joined them for regular widths while retaining the stacked layout for narrow screens or larger text. Verified visually in English and Arabic on the REDMAGIC. Existing project JSON SHA-256 remained `5ab03d0ffa3ae65a39539c9825e73144954ff6d5d13dd7b1c376d339bd5a7816` throughout.
- No other confirmed error was found within the tested scope.
- A compile warning remains for the existing deprecated Compose `LocalClipboardManager` use in the GitHub copy-code UI. The copy action worked in the prior Phase 1 device run; migrating that API was outside this focused correction and is not a build failure.

## Build and tests

- `:app:testVnextUnitTest :app:assembleVnext`: **passed**, 103 tests, 0 failures, 0 errors, 0 skipped.
- APK metadata verified before installation: package `com.karim.aiprojectmanager.vnext`, version `0.1.0-vnext`.
- `git diff --check`: passed.
- No Android instrumentation suite was run. Device acceptance used direct manual interaction and screenshots.

## Physical device checks

Used only REDMAGIC 10 Pro serial `FY2433810B9D`. Installed/updated only `com.karim.aiprojectmanager.vnext`. The phone system locale remained `en-US`; Android's per-app locale override remained empty. English and Arabic UI selection each applied immediately; after force-stop/relaunch and after the APK update, the selected language was restored. Project/task content was unchanged by language switching. The `.vnext` preference ended on English, matching the starting effective UI.

Dashboard, Overview, Blueprint, Roadmap, Tasks, Settings, and the GitHub connection panel were visually opened in English and Arabic using synthetic existing content. The two language choices were visible and selectable. English project navigation read left-to-right; Arabic navigation reversed and text was right-aligned as expected. Dialog checks from Phase 1 screenshots remain available; this phase did not repeat every dialog. Authenticated GitHub use was not tested: this local APK has no configured Client ID, so Connect is disabled. No login was attempted.

Evidence files (all actual REDMAGIC screenshots):

- English settings / selected options: `24-settings-language-en.png`, `28-settings-language-en-selected.png`, `37-settings-en-final.png`.
- Arabic settings / selected options: `25-settings-language-ar.png`.
- Language persistence after restart: `27-dashboard-language-ar-restart.png`; English after restart/update: `38-dashboard-en-restart-final.png`, `39-final-en-after-update.png`.
- English project screens: `29-overview-en-language-pref.png`, `30-blueprint-en-language-pref.png`, `31-roadmap-en-language-pref.png`, `40-tasks-en-compact-add.png`.
- Arabic project screens: `33-overview-ar-language-pref.png`, `34-blueprint-ar-language-pref.png`, `35-roadmap-ar-language-pref.png`, `41-tasks-ar-compact-add.png`.
- Phase 1 dashboard and GitHub/dialog screenshots remain indexed in `UI_UX_VNEXT_CORRECTION_PHASE1_2026-11-12.md`.

## Visual comparison against approved references

The six approved files were inspected, including the original logo. Comparison is against the actual device captures above. The app retains the approved navy/blue family and original logo artwork. Compact horizontal navigation is preserved as explicitly required. All five screens are **PARTIAL MATCH**:

| Screen | Result | Comparison and remaining difference |
|---|---|---|
| Dashboard | PARTIAL MATCH | Larger logo/title hierarchy and search/project cards read clearly in both locales. The reference uses a textured blue backdrop and project-specific artwork/status filters; those filters/artwork are not present as real app data/actions and were not invented. |
| Overview | PARTIAL MATCH | Description, progress, pending work, GitHub state, and quick facts use separated readable panels. The reference uses a denser single-screen overview and project illustration; this implementation uses scrollable panels and a generic project symbol. |
| Blueprint | PARTIAL MATCH | Editor has a large usable text area, save/recovery status, save action, and counts, with Arabic Save at physical right. Reference depicts undo/redo/fullscreen/format controls not present as supported editing actions; they were not added as decoration. |
| Roadmap | PARTIAL MATCH | Shares the readable editor layout and correct localized direction; actual content remains user-controlled. The reference shows a populated roadmap and a richer toolbar. Blank synthetic roadmap naturally leaves more unused editor space. |
| Tasks | PARTIAL MATCH | Status summary, search/filter, editable task title, and status actions remain functional; the creation field/Add action now align horizontally at normal width. Reference includes due dates, per-task menu and additional summary chips not backed by current task data/actions. |

The current palette is dark navy with blue/cyan accents and outlined cards. The supplied references use more gradient texture and brighter card treatment; matching that texture more closely remains a visual difference. No unsupported controls or project content were introduced to close the gaps.

## Risks, blockers, and recommendation

- Authenticated GitHub discovery is blocked by absent local GitHub App Client ID; this task did not retrieve or expose credentials.
- Recovery/conflict/process-death flows and accessibility/font/device variants were not retested in Phase 1B.
- Visual references include richer controls and content not supported by the current implementation; adding those requires a separately scoped feature decision.
- Original package `com.karim.aiprojectmanager` remained installed at the same package path; it was never installed over, uninstalled, or cleared. The VNext synthetic project file hash remained unchanged.
- No signing, storage format, GitHub auth architecture, or main-branch changes. No commit or push.

**Recommendation: NEEDS CORRECTION.** Manual language selection and language persistence passed, and the task creation layout correction was verified. Screen-by-screen visual comparison still finds meaningful differences in the richer approved references; additional visual alignment should be scoped around currently supported actions without adding mock controls. This does not declare the app ready for release.
