# AI Project Manager — current project status

Updated: 2026-11-06 (Asia/Kathmandu).
Branch: `codex/baseline-reconciliation-v0-2`.
Starting HEAD for the previous security/state maintenance batch: `d8fb04f86567e7cf2d42c71b5424430df991a2a3`.
Release status: **NOT Ready for Release**.

## Phase 2 Batch 1 — Core Application Settings — 2026-11-06

Owner authorization explicitly permits this bounded **development-only** increment, starting at
`b723b3b6698ad323e159c21c3cdd53847bb83b33`. Implementation/tests commit:
`09a0604dd16a9e1bb1090afeb6b2b69bde0ae380`. This does **not** close Gate 0, declare Phase 1
fully accepted, expand the reduced v0.2 release scope, or approve release/distribution.
**NOT Ready for Release**.
The historical phase ordering and requirements remain recorded; this is a specific authorization
to develop the settings foundation while their acceptance conditions remain open.

Implemented locally:

- Project-list Settings entry and a Material3 informational destination. Toolbar/system Back
  returns to the list. Search query and list scroll state are hoisted outside the destination
  and use saved state; filtered results are derived from the retained query. Settings is not
  accessible from active project editors; their existing dirty-navigation guards and
  save/reload/discard behavior are unchanged.
- Storage disclosure states that projects live in private local app storage, manual JSON export
  creates a user-controlled external copy, and preview does not import, merge or replace data.
  It explains configured automatic cloud/device-transfer exclusions, unverified manufacturer/
  transfer behavior, and loss risk after uninstall/private-data loss without an external export.
  It promises neither automatic external backup nor guaranteed recovery. Settings reuses the
  existing export and read-only preview callbacks; no import/reset/delete action was added.
- A small `AppPreferences` interface and private Android `SharedPreferences` adapter, separate
  from project JSON and without new dependencies. Missing, incorrectly typed or unreadable
  strings use the caller's default; rejected/exceptional writes return failure. Synchronous
  commits require a background dispatcher when used. No preference keys, stored values, language
  choices or theme choices were introduced. The informational screen does not instantiate/open
  the store because there is no legitimate preference value to save in this batch.

Verification actually executed:

- Focused production Kotlin compilation: **passed**.
- Focused host tests: **10 passed** (7 new `AppPreferencesTest`, 3 existing `BackupPolicyTest`).
  Preference tests use a fake platform interface: defaults without writes, Unicode round-trip,
  wrong-type fallback without mutation, read failure, rejected commit, write exception and blank
  key rejection. They do not establish real Android filesystem durability.
- Android test source compilation: **passed**. Four new `SettingsUiTest` cases cover toolbar Back,
  system Back after saved-state restoration, retained search/results and unchanged project bytes,
  disclosure/existing callback routing, and continued dirty-document navigation protection.
  **These tests have not been executed**; compilation is not runtime acceptance. Fixtures are
  isolated and include legacy unknown fields, IDs and VERIFIED status.
- Final offline validation ran once: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest` — **BUILD SUCCESSFUL**. Complete host suite: **56 passed**,
  12 classes, zero failures/errors/skips. Both Debug and test APK assembly passed.
  Current JUnit evidence: `app/build/test-results/testDebugUnitTest/` (generated, not committed).
- Complete code/test diff and whitespace checks passed. `ProjectStorage`, stale-write comparison,
  document/task editors and their save paths were compared with the starting implementation and
  are unchanged. No JSON schema/migration, project-data mutation path, manifest, icon, backup
  exclusion, SDK/dependency/build configuration, signing, application ID or version changes.
  No emulator, physical-device, PD/DU, Lint, GitHub push or main-branch operations ran.

Remaining limitations: Settings navigation/restoration, disclosure layout on narrow/large-font
screens and existing editor guards require the compiled UI tests plus separately authorized
runtime review. Real preference platform durability remains unverified and no preference feature
is active yet. Actual OEM backup/device-transfer behavior and all existing release gates remain
open. `AGENTS.md` and `docs/ROADMAP.md` are missing; no contents were assumed.
Language and theme selection require separate owner authorization and were not started.

## Current implementation

Existing metadata/title editing, project/task search, task-status filters, responsive status
buttons, overview summaries, guidance and tips remain implemented. Confirmed reload/discard,
pending-input retention after failures, and Blueprint/Roadmap dirty-navigation protection remain.

The preceding security/state maintenance batch added:

- Explicit platform backup exclusions while retaining `allowBackup=false`. API 26–30 uses
  `fullBackupContent`; API 31+ uses `dataExtractionRules` with separate cloud-backup and
  device-transfer sections. Every supported app-data domain is excluded, including credential-
  and device-protected storage and app-specific external files. Project JSON, atomic sidecars,
  recovery drafts and preserved drafts fall under these exclusions. Cache exports are already
  excluded by Android. User-requested document export remains available and unchanged.
- An immutable `EditorSnapshot` value with private, defensively copied fields and no mutation API.
  Both editors use an explicit Compose state Saver. It saves/restores detached lists using the
  previous ArrayList<String> representation, retaining exact field boundaries, IDs, statuses,
  order and the original stale-edit baseline. Only saved UI state uses this representation;
  project JSON is unchanged. Reload still replaces the baseline only after confirmation.
- Host configuration checks for manifest references, legacy exclusions and each modern transport;
  saver tests for round-trip equality, stale-base retention, old representation compatibility,
  mixed-language input and independence from mutable saved/restored lists.

No application storage contents were changed. There is no new backup/import/restore mechanism,
JSON migration, signing/identity/version change, SDK change or UI redesign. Existing persistence
and stale-write comparison paths are retained.

## Icon inspection

**Approved Android launcher icon integration: complete locally.** Integration commit:
`4583d33c7ed762b873558f9263c74551c104dd6e` (`feat(android): integrate approved launcher icon`),
2026-11-05. Documentation-only closure: 2026-11-06.

The integration added fourteen supplied icon resources (two adaptive layers, two adaptive XML
resources and ten legacy density PNGs) and updated only `android:icon` and `android:roundIcon` in
AndroidManifest.xml. The approved package is `AI_Project_Manager_New_Logo_Android_Icons.zip`,
SHA-256 `c8211813c907ffb2618b4f8f7065d1cd1714608cfabf2f787a0cc5ef8c2e17a6`.
All fourteen resource files match the approved ZIP byte-for-byte. All twelve packaged PNGs retain
identical decoded pixels; compiled adaptive XML and manifest references resolve correctly.
No artwork, backup policy, application logic, identity, signing, JSON schema or version was changed.
The earlier absence of an approved asset recorded at the security/state checkpoint is superseded
by this owner-approved integration; no monochrome design was invented.

Verification and evidence reviewed for this closure:

- Debug build and Android resource compilation **passed in the integration execution report**
  (`:app:assembleDebug`). A standalone build transcript was not preserved; the existing APK and
  compiled resources support the packaging result, but the build was not independently rerun.
- Three existing `BackupPolicyTest` configuration tests **passed**. The retained JUnit XML records
  3 tests, zero failures/errors/skips, timestamp `2026-11-05T18:07:40.241Z`.
- Static circle, squircle and rounded-square mask checks **passed**, including 48px previews.
  The saved contact sheet shows the ribbon, checkmark and both sparkles without visible clipping.
  Reinspection of the packaged foreground confirms maximum radial extent **320.765491px**
  around canvas center (540,540), within the **330px** safe region. At alpha >=128 the maximum is
  315.585963px; zero pixels exceed the safe radius at either threshold. Distances use pixel centers
  and the intended 1080px-to-108dp layer mapping. This is static geometry, not launcher animation.
- Pixel Launcher inspection **passed** on a newly created disposable API 35 AVD,
  `AI_PM_Icon_Disposable` / `emulator-5580`, x86_64, 1080×1920 at 420 dpi. The retained screenshot
  and launcher record support correct app-drawer rendering without obvious clipping or an inner
  background tile. This establishes one launcher/configuration only.
- The execution report records that this disposable AVD was stopped and removed after inspection.
  The retained emulator log records shutdown and its isolated `avds/` directory is now absent.
  Existing AVDs and physical devices were not modified according to the execution scope record;
  this closure did not access devices to independently audit that operational claim.
- No GitHub push or main-branch changes were performed according to the integration report.
  This closure performs only local documentation work; no remote or main operations are used.

Local evidence is outside Git at
`/home/karim/.codex/ai-pm-icon-integration-ayi_va9g/`: `packaged-verification.json`,
`launcher-verification.json`, `emulator.log`, `previews/static-mask-contact-sheet.png`, and
`previews/disposable-launcher-app-drawer.png`. The retained APK is
`app/build/outputs/apk/debug/app-debug.apk`, SHA-256
`5dd42657d36a1ee9cf91bb70e32888de1b980b2f834037c5c86e0c07bb2cc0d7`, matching the saved
verification record. Test XML is
`app/build/test-results/testDebugUnitTest/TEST-com.karim.aiprojectmanager.BackupPolicyTest.xml`.
These local/generated files are not committed and may be removed by later cleanup/builds.
No builds, automated tests or emulator/physical-device operations were run for this closure.

## Verification for the previous security/state maintenance batch

- Focused host checks: **8 passed** (5 editor-snapshot tests, 3 backup-policy tests).
- Production and Android test source compilation: **passed**.
- Two existing isolated Compose UI regressions: **2 passed**, zero failures/errors/skips,
  8.115 seconds of instrumentation time. Methods:
  `ProjectMetadataUiTest#conflictReloadRequiresConfirmationAndAllowsEditAgainstNewBase` and
  `TaskTitleUiTest#conflictReloadRetainsInputUntilConfirmedAndPreservesNewStatus`.
  These check restored pending input, stale-save blocking, reload confirmation/cancellation,
  unchanged bytes during reload, and subsequent safe saving with newer data retained.
- Emulator only: `emulator-5554`, AVD `REDMAGIC_10_Pro_Test`, Android 15/API 35, x86_64,
  320×640 at 160 dpi. No physical-device operations or full instrumentation/PD/DU suite ran.
- Final offline validation ran once: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL** (8 seconds).
- Complete host JVM suite: **49 passed**, 11 classes, zero failures/errors/skips.
- Merged Debug manifest retains all three backup-policy attributes and correct resource references.
- Lint: **0 errors, 4 warnings**. `DataExtractionRules` and both
  `MutableCollectionMutableState` findings are gone without suppressions.
- Complete diff reviewed and whitespace check passed. No push or cloud CI run.

Remaining Lint warnings at that checkpoint: `OldTargetApi` (targetSdk 35), `GradleDependency` (compileSdk 35),
`MissingApplicationIcon`, and debug-fixture `UseKtx`. SDK values are intentionally unchanged;
icon integration was then pending and is now complete locally as recorded above. KTX convenience
cleanup is deferred. No post-icon-integration Lint run or updated warning count is claimed.

Local reports: `app/build/test-results/testDebugUnitTest/`,
`app/build/outputs/androidTest-results/connected/debug/`, and `app/build/reports/`.
Generated reports are not committed. Platform policy references:
[Android backup configuration](https://developer.android.com/identity/data/autobackup) and
[Android 12 backup/transfer changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore).

## Previous focused UI checkpoint

Before this maintenance batch, seven distinct added UI tests passed across focused runs: initial
5/7 passed, followed by diagnosed test-only lazy-list scrolling fixes and successful retries.
Commit `d8fb04f` records that fix. This is historical evidence, not a rerun of all seven against
this maintenance candidate. Earlier full 107-test/DU evidence remains commit-specific.

## Deferred verification and risks

- Other Android launchers and manufacturer-specific masks, launcher animation behavior, and
  themed monochrome icon support (if required for release) remain pending. Static masks and one
  Pixel Launcher inspection do not constitute broader launcher acceptance.
- Actual cloud-backup/device-transfer transport behavior across Android versions and OEMs is not
  verified by static rules or Lint. No transport was invoked and no real data was transferred.
- Full process death, large saved-state payloads, broad visual/RTL/accessibility, scale/provider
  lifecycle, stable signing and non-destructive physical-device update/data continuity remain open.
- Reload discards pending text only after confirmation; there is no automatic merge or edit history.
  Pending form state uses Android saved state, not a new disk draft.
- `AGENTS.md` and `docs/ROADMAP.md` remain missing; their contents have not been invented.

## Recommended next step

For this development increment, separately authorize focused Settings UI execution using disposable
data before accepting navigation/restoration and visual behavior. Scope language/theme work only
with separate owner approval; do not treat the preferences foundation as those features.

Review the explicit backup opt-out policy and separately scope platform/OEM transport verification
using disposable data. Separately scope other-launcher/mask and animation checks, and decide
whether themed monochrome support is required. Preserve all remaining
[release gates](RELEASE_GATE_V0_2.md).
