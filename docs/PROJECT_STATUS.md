# AI Project Manager — current project status

Updated: 2026-11-05 (Asia/Kathmandu).
Branch: `codex/baseline-reconciliation-v0-2`.
Starting HEAD for this maintenance batch: `d8fb04f86567e7cf2d42c71b5424430df991a2a3`.
Release status: **NOT Ready for Release**.

## Current implementation

Existing metadata/title editing, project/task search, task-status filters, responsive status
buttons, overview summaries, guidance and tips remain implemented. Confirmed reload/discard,
pending-input retention after failures, and Blueprint/Roadmap dirty-navigation protection remain.

This maintenance batch adds:

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

No usable application icon asset or corresponding approval record was found in the repository.
There are no existing app drawable/mipmap icon resources; evidence screenshots are not approved
icon assets. Icon integration is deferred. Before public release, obtain an approved asset and
integrate appropriate launcher/adaptive icon resources and a manifest icon reference. No design
was invented or substituted.

## Verification for this maintenance batch

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

Remaining Lint warnings: `OldTargetApi` (targetSdk 35), `GradleDependency` (compileSdk 35),
`MissingApplicationIcon`, and debug-fixture `UseKtx`. SDK values are intentionally unchanged;
icon integration awaits an approved asset; KTX convenience cleanup is deferred.

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

- Actual cloud-backup/device-transfer transport behavior across Android versions and OEMs is not
  verified by static rules or Lint. No transport was invoked and no real data was transferred.
- Full process death, large saved-state payloads, broad visual/RTL/accessibility, scale/provider
  lifecycle, stable signing and non-destructive physical-device update/data continuity remain open.
- Reload discards pending text only after confirmation; there is no automatic merge or edit history.
  Pending form state uses Android saved state, not a new disk draft.
- `AGENTS.md` and `docs/ROADMAP.md` remain missing; their contents have not been invented.

## Recommended next step

Review the explicit backup opt-out policy and separately scope platform/OEM transport verification
using disposable data. Obtain an approved application icon before public distribution. Preserve
all remaining [release gates](RELEASE_GATE_V0_2.md).
