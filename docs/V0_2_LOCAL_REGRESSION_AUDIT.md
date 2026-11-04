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

## Baseline Reconciliation — 2026-11-04

Date: 2026-11-04 (Asia/Kathmandu). Branch: `codex/baseline-reconciliation-v0-2`.
Application/code baseline: **`8824a110afbd2e936e6a14ee5464d0e929b59976`**.
Status: reconciliation implemented and locally tested; **NOT Ready for Release**.
This appended checkpoint supersedes earlier current-status counts without rewriting historical findings.

### Exact provenance and scope

Original local safety commit: `c8623f3cd4cd65aaf1b2bcaf5b11f4c774fabb4b`.
Reference: `feature/project-list-search` at `07809a11cc92e514d7f5c271b9a24de501498dd9`.
Their merge base was `fec49d59ac8ad05f1bfc387992a295cf9508460a`; the original local side
had one unique commit and the reference had six. Integration was performed as scoped local
changes on a separate branch, not a branch merge.

| Feature/correction | Local commit |
| --- | --- |
| Project-list search by name/description | `7a82da5793e39c4fa8b28528f5e3663de7529d62` |
| Next-Step Guidance | `c1d437a778c483c3b7694e63a50f0bdeb45558e2` |
| Contextual Tips for editors/tasks | `b18f29376ffc1103f774dfbcbe74e011be9aa017` |
| Project-creation guidance | `a66c6677040d02ef9c157f9a283455c53e2be3d0` |
| Task Status Summaries | `5e1c9d23f60f02fb984ce9779164177a46c6bde0` |
| Pending-Task Details | `a6adb9721ac4fdb157dc7f3a06b043aaff25431a` |
| Overview scrolling fix and focused UI regression | `8824a110afbd2e936e6a14ee5464d0e929b59976` |

The five feature helpers match the reference implementation; project-creation guidance shares
ContextualTips. All reference feature-test bodies/assertions are represented in five JVM test
classes, with Android runner imports/annotations removed. JUnit 4.13.2 was added for local JVM tests.
Search retains project order and matches name/description without case sensitivity or surrounding
query spaces. Guidance/tips are offline and informational. Status summaries and pending-task
labels use saved manual statuses, preserve unknown values, and do not change task order/data or
claim independent verification.

Original local backup-preview cancellation, overridable lifecycle/provider test seams, debug
fixtures, existing instrumentation tests, editor draft/save/navigation guards, and JSON persistence
were preserved. No schema, storage implementation, signing, main-branch, GitHub or physical-phone
changes were made. Reconciliation was not pushed and has no claimed cloud CI validation.

Overview previously had no vertical scrolling path for long content. The correction bounds only
Overview to the space below the fixed tabs and adds vertical scrolling keyed to project identity.
All text, task order, 16dp padding, 12dp spacing, app bar, tab handlers and Back/dirty-editor guards
are retained. `OverviewScrollingUiTest` reaches the footer and last pending task in a 40-task
fixture, checks visible navigation and tab switching, opens another project at its own initial
scroll position, and asserts unchanged stored bytes. It does not establish large-list performance.

### Verification results and evidence provenance

These are earlier executed results on the final code, inspected during documentation work;
**no builds, tests, lint or device operations were rerun for this documentation update**.

| JVM class | Passed | Failures/errors/skips |
| --- | ---: | --- |
| ProjectListSearchTest | 3 | 0/0/0 |
| NextStepGuidanceTest | 8 | 0/0/0 |
| ContextualTipsTest (including creation guidance) | 5 | 0/0/0 |
| TaskStatusSummaryTest | 3 | 0/0/0 |
| PendingTaskDetailsTest | 3 | 0/0/0 |
| **Complete local JVM suite** | **22** | **0/0/0** |

- Actual complete Android instrumentation run: **107 passed, 0 failures, 0 errors, 0 skips**,
  **52.824 seconds**; the prior 106 tests plus the Overview regression in one run. This is actual
  instrumentation execution, not merely compilation. Environment previously verified:
  `emulator-5554`, AVD `REDMAGIC_10_Pro_Test`, Android 15/API 35, 320 × 640 at 160 dpi.
- Debug and Android test APK assembly: **previously reported successful**. No standalone build
  transcript was available for preservation; existing XML/log files do not independently prove
  assembly. No build was rerun to manufacture evidence.
- Existing lint report inspected: **0 errors and 12 warnings**. The known warning categories remain
  SDK/dependency versions, application icon, data-extraction configuration and debug-fixture KTX use.
- Earlier emulator safety checks reported no existing projects/drafts, matching installed/candidate
  debug certificates, in-place APK updates without uninstall/reset, isolated fixtures, and no
  projects/drafts remaining afterward. These environment/data-safety observations are prior session
  evidence, not newly repeated device checks or proof of physical-device data preservation.

Preserved artifacts under `audit-evidence/baseline-reconciliation-2026-11-04/`:

- [Original instrumentation output, losslessly compressed](audit-evidence/baseline-reconciliation-2026-11-04/instrumentation.log.gz).
- [Instrumentation results XML](audit-evidence/baseline-reconciliation-2026-11-04/instrumentation-results.xml): exact copy of the existing status-event-derived XML; per-test timings were not captured.
- JVM XML: [search](audit-evidence/baseline-reconciliation-2026-11-04/TEST-com.karim.aiprojectmanager.ProjectListSearchTest.xml), [guidance](audit-evidence/baseline-reconciliation-2026-11-04/TEST-com.karim.aiprojectmanager.NextStepGuidanceTest.xml), [tips/creation](audit-evidence/baseline-reconciliation-2026-11-04/TEST-com.karim.aiprojectmanager.ContextualTipsTest.xml), [summaries](audit-evidence/baseline-reconciliation-2026-11-04/TEST-com.karim.aiprojectmanager.TaskStatusSummaryTest.xml), [pending tasks](audit-evidence/baseline-reconciliation-2026-11-04/TEST-com.karim.aiprojectmanager.PendingTaskDetailsTest.xml).
- [Lint report](audit-evidence/baseline-reconciliation-2026-11-04/lint-results-debug.xml).
- [Evidence provenance and SHA-256 checksums](audit-evidence/baseline-reconciliation-2026-11-04/evidence-provenance.json).

JVM reports omit hostname and properties/output sections; test cases, outcomes, timings and timestamps
are retained. Lint workspace paths are made repository-relative. Existing instrumentation artifacts
were copied/compressed without changing results. The provenance file records source and preserved
hashes and transformations. These are preservation operations, not regenerated test runs. Earlier
tracked evidence is retained unchanged; the historical 106-test run is distinct from this 107-test run.

### Remaining risks and local closure

- Visual/RTL and mixed-language layout, large fonts, TalkBack/accessibility and keyboard-visible
  behavior need broader verification. One narrow-emulator regression is not full visual acceptance.
- Overview still eagerly composes all pending tasks. Very large lists/documents, synchronous
  persistence costs and saved-state/Binder limits remain unprofiled.
- Controlled picker/provider fixtures establish callbacks and error handling, not actual system
  DocumentsUI, third-party providers, URI grants or every provider recreation path.
- Actual process-death recovery, physical-device keyboard/background/restart workflows, stable
  release signing/version policy and non-destructive installed-user-data update continuity remain open.
- Matching debug certificates on this emulator do not establish stable release signing. Synthetic
  fixtures do not establish preservation of the user's installed-phone data.

The local engineering reconciliation is complete at the code baseline above. Documentation/evidence
closure does not close the release gate. Further acceptance work requires separately scoped approval;
no release, push/merge, data migration, live restore or V1.5 implementation is authorized here.
The [release gate](RELEASE_GATE_V0_2.md) remains authoritative for current restrictions and next steps.

## DU-01 real DocumentsUI acceptance — 2026-11-04

**Verdict: PASS for DU-01 only. v0.2 remains NOT Ready for Release.**
Tested branch: `codex/baseline-reconciliation-v0-2`.
Tested commit: `fe6df94ae6ade1142c07a4bee5e2f030ad1ba14c`.
This checkpoint covers the preserved successful CreateDocument interaction followed by the
separately authorized final OpenDocument preview/closure/cancellation interaction. It is not a
claim that the earlier attempts passed or that all steps ran in one uninterrupted session.
The local Downloads happy path is now evidenced; broader provider/lifecycle gates remain open.

### Environment, identity and fixture

- Owner-approved disposable AVD: `REDMAGIC_10_Pro_Test`; explicitly targeted serial
  `emulator-5554`; Android 15/API 35. No physical phone was operated on.
- Production application: `com.karim.aiprojectmanager/.MainActivity`; debuggable local APK.
  The real foreground picker was
  `com.google.android.documentsui/com.android.documentsui.picker.PickActivity`.
  No deterministic picker, mocked callback or isolated instrumentation host was substituted.
- Previously verified installed APK SHA-256:
  `14f95b59bc12a99f7194bc54b8f03c76512b9e6324f35cbf562b06447c7e2acd`.
- Previously verified matching debug signing-certificate SHA-256:
  `d0e3ac3b24d6fa7b592072df7ed61e25afe3c9f44d9af93fa656c18bb6699bf6`.
  These earlier identity checks were not repeated with a device during this documentation task.
- Exactly one disposable project, `DU-01-039c63f3f692`, and one task,
  `DU-01 disposable task`, status `NOT_STARTED`.
- Verified new export: `DU-01-filename-54b3msf4.json` in emulator-local Downloads.
  The earlier default-name export was retained and not overwritten or deleted.

### Acceptance evidence and results

| Required observation | Result |
| --- | --- |
| Source fixture and fresh snapshot match the prior task-setup diagnosis | PASS |
| Real CreateDocument picker; unique filename checked before one explicit Save | PASS |
| Export exists, parses as JSON, and contains the expected one-project/one-task fixture | PASS |
| Export is byte-for-byte identical to the private source snapshot | PASS |
| Real OpenDocument picker selects the exact exported filename from local Downloads | PASS |
| Preview displays project name, exactly one project and one task | PASS |
| Preview explicitly states that no data will be replaced or merged | PASS |
| Application `إغلاق` closes preview; MainActivity responds normally | PASS |
| Reopened real picker cancelled with Android Back without selecting a document | PASS |
| No unexpected preview/error immediately or after five seconds; project/task accessible | PASS |
| Final private source bytes equal the initial snapshot; private file inventory unchanged | PASS |
| Downloads retains the two prior exports, with no additional document from the final phase | PASS |
| No relevant app crash/ANR, permission denial or document-read exception observed | PASS |

Private source snapshots were read through authorized debuggable-app `run-as` access without
modifying the source. The actual exported file was retrieved independently. All source-before,
export, and source-after snapshots from the successful export/final phases have exact byte equality
and this SHA-256:

`46db5571af94b15cf4784c230e3ef9cc1fc47d872332e5c7a94f9675a0bdbefd`

The hash comparison is accompanied by actual UI screenshots, foreground-component records and
UI hierarchies; it is not used alone to claim correct preview or cancellation behavior.
No restore, merge or import operation was invoked. Final acceptance created no new project,
task or export. Existing fixture and exports were left intact; no cleanup was authorized.

### Durable evidence and documentation verification

The privacy-reviewed bundle is preserved under
[`audit-evidence/du-01-2026-11-04/`](audit-evidence/du-01-2026-11-04/manifest.json).
Its [provenance/checksum manifest](audit-evidence/du-01-2026-11-04/manifest.json) records the tested
commit and emulator, APK hash and public certificate fingerprint, acceptance-criterion mappings,
relative file paths, byte sizes, full SHA-256 values, source artifact hashes and transformations.
The manifest distinguishes the scoped PASS from the two historical INCONCLUSIVE observations.

- Export: [initial real picker](audit-evidence/du-01-2026-11-04/export/01-picker-initial.png),
  [filename before Save](audit-evidence/du-01-2026-11-04/export/04-before-save.png),
  [full filename hierarchy](audit-evidence/du-01-2026-11-04/export/04-before-save.xml),
  [actions](audit-evidence/du-01-2026-11-04/export/actions.txt) and
  [result](audit-evidence/du-01-2026-11-04/export/result.json).
- Preview/cancellation: [real OpenDocument picker](audit-evidence/du-01-2026-11-04/preview-cancellation/02-open-document.png),
  [read-only preview](audit-evidence/du-01-2026-11-04/preview-cancellation/03-read-only-preview.png),
  [post-cancellation observation](audit-evidence/du-01-2026-11-04/preview-cancellation/07-cancel-observed.png)
  and [final result](audit-evidence/du-01-2026-11-04/preview-cancellation/result.json).
  Additional UI hierarchies, foreground component and action observations are mapped in the manifest.
- Integrity: [initial source](audit-evidence/du-01-2026-11-04/integrity/source-before.json),
  [export](audit-evidence/du-01-2026-11-04/integrity/exported.json),
  [final source](audit-evidence/du-01-2026-11-04/integrity/source-after.json), private-file inventories
  and final Downloads inventory preserve the synthetic fixture and byte-equality evidence.
- Identity: [APK/environment metadata](audit-evidence/du-01-2026-11-04/identity/apk-environment.json)
  and [public debug certificate](audit-evidence/du-01-2026-11-04/identity/installed-certificate.txt)
  preserve prior checks without retaining an APK binary or private signing material.
- History: [initial setup INCONCLUSIVE](audit-evidence/du-01-2026-11-04/history/initial-setup-inconclusive.json)
  and [filename-entry INCONCLUSIVE](audit-evidence/du-01-2026-11-04/history/filename-entry-inconclusive.json)
  remain separate from the successful acceptance result.
- Diagnostics: [export excerpts](audit-evidence/du-01-2026-11-04/diagnostics/export-diagnostics.txt)
  and [preview/cancellation excerpts](audit-evidence/du-01-2026-11-04/diagnostics/preview-cancellation-diagnostics.txt)
  retain unexplained system messages with unrelated log content and transient identifiers omitted.
  Excerpts are not complete logs or independent proof that errors were absent.

The bundle contains 28 payload files plus the manifest. Screenshots were visually reviewed;
selected text/UI records and synthetic JSON were inspected. Sanitized derivatives replace the
temporary directory reference, normalize the foreground-component record and omit the earlier
attempt's intended filename from identity metadata. Diagnostic extracts omit process/thread/user
numbers and transient handles; timestamps, severity and relevant messages remain. Full source and
preserved checksums and transformations are recorded. No raw logs, clipboard-content overlays,
APK binaries, PID files, broad activity dumps, host paths, private keys or unrelated account data
are included.

All manifest checksums, local documentation links, source/export/final byte equality and original
five-directory inventories were verified during preservation. All original evidence remains outside
the repository, unchanged. No acceptance tests, builds, lint, emulator or GitHub operations were
rerun. Prior absence-of-error and environment observations remain evidence from their original
execution, not new claims inferred from sanitized excerpts.

### Earlier INCONCLUSIVE attempts and unexplained diagnostics

- `DU01-acceptance-kzmk_vn1/`: the initial attempt stopped with the project created but zero tasks
  after a task-entry/Back sequence. The later setup-only diagnosis in
  `DU01-setup-diagnosis-nj7nnoc1/` confirmed one Add action persisted the task and survived reopening.
  Automation sequencing/navigation is supported as the setup issue; the precise earlier IME
  interaction was not established. No application task-save defect was reproduced.
- `DU01-resumed-1etke422/`: filename-entry automation returned from DocumentsUI and produced the
  default-name export instead of the intended unique filename. The attempt stopped before preview.
  The later controlled interaction verified focus, empty field and unique filename before Save.
  The exact trigger in the earlier tap/Ctrl+A/text sequence remains unresolved. This historical
  INCONCLUSIVE result is not relabelled as PASS or recorded as a proven application/system defect.
- During the successful final phase, DocumentsUI logged
  `No package ID ff found for resource ID 0xffffffff.` System PackageConfigPersister and input-channel
  disposal warnings also appeared. No associated functional failure, app crash/ANR, permission
  denial or document-read exception was observed. These diagnostics were not independently
  root-caused and must not be represented as absent or definitively harmless.

### Remaining release gates and next step

DU-01 establishes a real local-Downloads export/preview/cancellation happy path on one disposable
emulator. It does not verify process death or recreation while system pickers/providers are active,
third-party/cloud providers, URI-grant failures, hostile/unavailable providers, large-file behavior,
full visual/RTL/accessibility, large-list performance, stable release signing/version policy, physical
keyboard/background/restart workflows, or non-destructive physical-device update/data continuity.
Existing 12 lint warnings and the unexplained system diagnostics remain recorded. No CI conclusion
is inferred from this local acceptance evidence.

Durable preservation is complete locally; it does not close the remaining gates. Recommended next
acceptance test: separately authorize recreation of production MainActivity while the real
OpenDocument picker is foreground, then selection/preview and source-byte preservation checks on
the disposable emulator. This would test activity recreation, not process death. The
[release gate](RELEASE_GATE_V0_2.md) remains open. This evidence/documentation update authorizes no
push, merge, release, migration or V1.5 implementation.
