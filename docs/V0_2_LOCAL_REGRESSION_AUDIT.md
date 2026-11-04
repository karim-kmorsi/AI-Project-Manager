# v0.2 local regression audit — 2026-11-03

For the subsequent activity-recreation and real ActivityResult callback coverage, see the
[2026-11-04 engineering follow-up](#autonomous-engineering-follow-up--2026-11-04).
The older gap lists below describe their original checkpoints.

Baseline: `feature/v0-2-editor-ux`, commit `4b0d0f9`. No `AGENTS.md` was found in the repository or its ancestor directories.

## Existing coverage reviewed

- `LegacyStorageTest`, `DraftStorageTest` and `ReleaseWorkflowRegressionTest`: project/document/task round trips, legacy defaults, corruption rejection, draft persistence and VERIFIED status persistence. These are mostly storage-level tests; constructing edited objects does not exercise UI callbacks.
- `RapidTypingUiTest`: Blueprint typing and successful autosave through Compose.
- `JsonExportTest`: exact byte export, URI output, invalid source and failed/partial writes. Its cancellation test implements a local null check, rather than exercising MainActivity's activity-result callback.
- `RestorePreviewTest` and `RestoreCandidateTest`: preview summaries, invalid/oversized/missing selections and null selection without data changes.
- `LocalBackupTest`: isolated snapshot preservation, validation and failure handling. Restore transaction/recovery engines remain outside this user-visible scope and are not activated.

## Minimal added coverage

`ProjectWorkflowUiTest` adds three Compose instrumentation tests:

1. Creation: blank/whitespace validation, cancellation before any storage file exists, clearing the cancelled form, trimming accepted name/description, opening the created project and reloading its fields from a new storage instance.
2. Tasks: blank input rejection, adding a trimmed task, clearing the input, persisting each UI status transition, counting only VERIFIED in the overview, and preserving all other project fields and a second project.
3. Preview: opening a validated summary through the preview callback, rendering counts, closing the summary and an injected error, checking dismissal callbacks and unchanged saved bytes, with no export callback invoked.

All three inject ProjectStorage backed by a unique cache directory and `test-projects.json`, cleaned in `finally`. They do not launch MainActivity, use system document pickers, enter document editors, read user projects or write recovery drafts. UI tests use the repository's existing Compose/JUnit dependencies. No production or build configuration changes are needed.

## Remaining real gaps and limits

- Manual Save, Roadmap editing, dirty-editor Back/tab guards, save-failure UI and activity/process recreation are not covered by these additions. The editor constructs DraftStorage from `context.filesDir` directly; fully isolated editor UI tests need a separate test design or an approved injection seam. Existing autosave coverage does not establish these behaviors.
- MainActivity's actual CreateDocument/OpenDocument cancellation, callback state cleanup, provider failures and success/failure notifications remain untested end to end. Direct resolver tests use file URIs, not a system document provider. The new dialog test injects the error state; it does not prove picker error handling.
- There is no user-visible project name/description edit form in the current implementation. Storage-level field editing coverage must not be described as proof of such a UI feature.
- The new tests reopen storage instances, not the Android process. They do not establish real-device update/data continuity or signing compatibility.

## Local validation outcome

- Local Android debug build and AndroidTest APK compilation succeeded, as confirmed during local validation.
- **58 instrumentation tests passed** on the Android 15 emulator `REDMAGIC_10_Pro_Test`, with zero failures, errors or skipped tests. The local XML report at `app/build/outputs/androidTest-results/connected/debug/TEST-REDMAGIC_10_Pro_Test(AVD) - 15-_app-.xml` confirms the totals and includes all three `ProjectWorkflowUiTest` cases.
- The initial environment-readiness blockers are historical and were superseded by this successful local validation. They are not outstanding test failures.
- Publication review preserves the verified UI tests and only corrects this documentation. Successful local builds/tests are not repeated for documentation-only corrections.

The original publication scope was limited to this audit and `ProjectWorkflowUiTest.kt` on `feature/v0-2-editor-ux`. Only automatically triggered CI runs are observed; no manual CI run or rerun is requested. The user confirmed a $0 Actions spending budget with usage blocking enabled; no billing settings are changed.

## Subsequent CI diagnosis and verification — 2026-11-03

Push run `41524878527` and PR run `41524890832` failed the task workflow test on `435adace9779faf92612e2744e4bcd47f7bd31e7`. The unchanged targeted test reproduced the exact missing `إضافة` semantics-node failure on the Android 15 emulator at CI's 320 × 640 display size and 160 dpi. The Tasks tab was outside the visible portion of `ScrollableTabRow`, so a click without scrolling did not navigate to the task screen.

Commit `06e9a50c23ea35ec1ca7929e00ddc642a54fcd0d` adds `performScrollTo()` before Tasks/Overview tab clicks in that test. All disabled-button, persistence, status, progress and other-project assertions remain. No production or storage-format change was needed. At the same narrow emulator size, the fixed targeted test passed (1 test) and the full suite passed (58 tests, 0 failures/errors/skips).

Both automatic workflows on the fix completed successfully: [push 41643830854](https://github.com/karim-kmorsi/AI-Project-Manager/actions/runs/41643830854) and [PR 41643842358](https://github.com/karim-kmorsi/AI-Project-Manager/actions/runs/41643842358), each running 58 instrumentation tests. This resolves the CI failure, not the coverage gaps listed above or real-device/signing verification. The [release gate](RELEASE_GATE_V0_2.md) defines the reduced release scope; the broader approved requirements remain historical requirements.

Local test success does not establish phone validation, signing compatibility or release readiness. The remaining release gate still applies. No production code, data format, signing, design, live restore or user-data changes are included.

## Autonomous engineering follow-up — 2026-11-04

Started from clean `feature/v0-2-editor-ux` at `fec49d59ac8ad05f1bfc387992a295cf9508460a`.
Work is local on `test/v0-2-lifecycle-provider-reliability`; no push or cloud CI was run.
CI #191/#192 results are prior evidence supplied by the owner, not rerun or independently
reverified in this session. No AGENTS.md was found in the repository or workspace ancestors.

### Coverage added without duplicating existing saved-state simulations

- `ActivityRecreationUiTest` (4 tests): actual activity recreation with pending Blueprint/Roadmap
  drafts, system Back guards, manual Save before autosave, and failed saves with retained drafts.
  Failed-save cases also exercise navigation to the other editor, unchanged saved bytes and
  unrelated projects/tasks, removal of the disposable write blocker, successful retry and a
  second recreation. Existing `ManualSaveNavigationUiTest` and `StorageFailureUiTest` remain.
- `DocumentResultUiTest` (12 tests): actual MainActivity CreateDocument/OpenDocument result
  callbacks, cancellation and subsequent export, staged export across actual recreation while
  the picker is foreground, missing snapshots, denied/unavailable/broken-output providers,
  exact export bytes and cleanup, read-only preview/close, malformed/oversized input, picker
  launch exceptions and overlapping preview reads.
- Debug-only, non-exported activity/provider fixtures isolate every workflow in unique private
  cache roots. An instrumentation monitor redirects document intents to a deterministic local
  picker; Android still delivers the real ActivityResult callback. `MainActivity` and its
  notification method are overridable solely to isolate storage and observe notifications.
  No dependency, release manifest, CI or signing configuration changes were needed.

### Confirmed defect and minimal fix

Two deterministic regressions failed on the original callback behavior: an older slow provider
read reopened a preview after a later cancellation, and replaced a newer selected summary.
[Baseline evidence: 2 failures](audit-evidence/v0.2-lifecycle-provider-2026-11-04/preview-race-before-fix.xml).
MainActivity now cancels the previous lifecycle preview job both when opening a picker and when
receiving its result. Cancellation propagates through the IO dispatch before stale results can
publish. This changes only read-only preview ordering; it does not implement restore/import.

Initial document-test harness execution timed out when finishing a picker before it was resumed
and when using resumed-activity helpers behind the foreground picker. Those were harness
synchronization problems, not accepted production-defect evidence. The fixture now waits for
picker resume and observes actual host recreation independently. The race regressions then
failed on their intended assertions before the production fix; assertions were retained.

### Actual local verification

- JDK 17, Gradle 8.13, existing Android 15/API 35 x86_64 AVD; 320 × 640 at 160 dpi, animations off.
- Initial focused activity recreation: **4 passed**. After the fix, document callback class:
  **10 passed**; two picker-launch-error tests were then added.
- Final debug and instrumentation APK assembly: **passed**.
- Final full suite on final code: **106 passed, 0 failures/errors/skips**, 47.167 seconds.
  [106 distinct test results](audit-evidence/v0.2-lifecycle-provider-2026-11-04/full-106.xml).
  Evidence XML is parsed from actual instrumentation status events, not a Gradle-connected
  report; per-test timings were not captured. The final full run includes all 16 added tests
  and the previous 90 tests in one invocation.
- `:app:lintDebug`: **passed, 0 errors, 12 warnings**. The previous 11 warnings remain
  (SDK/dependency versions, icon, data extraction); one `UseKtx` suggestion is in the debug
  fixture's URI helper. No tool/dependency changes were made to address unrelated lint debt.

Build command (with local Gradle on PATH):

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

All device commands explicitly selected the session's emulator, never the phone:

```sh
adb -s emulator-5560 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell am instrument -w -r com.karim.aiprojectmanager.test/androidx.test.runner.AndroidJUnitRunner
```

`connectedDebugAndroidTest` was deliberately not used because a physical phone may be connected.
No release keys, GnuPG, phone data, GitHub, CI workflows, main, JSON schema or destructive restore
paths were accessed or changed. Only debug APKs were built and installed on the disposable emulator.

### Remaining gaps and next task

These tests exercise real callbacks with controlled provider/picker substitutes, not system
DocumentsUI, third-party providers or URI-grant behavior. Actual process death, Binder-size/large
document limits, physical keyboard/background workflows, accessibility/RTL visual review,
signing continuity and update preservation remain unverified. Cancelling a preview job prevents
stale UI publication; it does not guarantee interruption of a provider's blocking IO call.

Next engineering task: add a focused emulator smoke test using actual system DocumentsUI for
export/preview success and cancellation, with disposable documents and explicit emulator targeting.
No physical-device, signing or release-readiness claim follows from this session.
