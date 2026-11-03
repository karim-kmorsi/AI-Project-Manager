# AI Project Manager — V3 stabilization audit

Audit date: 2026-11-04 (Asia/Kathmandu). Engineering regression only; **final acceptance was not run and v0.2 is not Ready for Release**.

**PROJECT:** AI Project Manager — V3  
**BRANCH:** `feature/v0-2-editor-ux`  
**STARTING COMMIT:** `a1748c450d1a6f5411cd2826489ef89411e29857`  
**FINAL CODE COMMIT:** [`c59e036a10d911c8da0e0115f980760b2b7fce91`](https://github.com/karim-kmorsi/AI-Project-Manager/commit/c59e036a10d911c8da0e0115f980760b2b7fce91). The final documentation commit and post-push CI results are recorded in the chat handoff; this report is written before that push.

## Audit coverage

The supplied workspace was empty. Cloned only the approved development branch, verified the remote, clean tree and current HEAD before edits. No AGENTS.md existed in the repository or its workspace ancestors. Read the release gate, regression audit, approved plan, JSON policy and historical architecture/MVP documents; the release gate controlled scope.

Inspected every production Kotlin file, manifest, build/dependency configuration and CI workflow. Reviewed project creation/listing/selection, task creation/status/progress callbacks, both document editors, explicit Save, debounced autosave, recovery/conflict handling, Back/tab guards, saved Compose state, export and read-only preview boundaries. Inspected dormant snapshot/restore/recovery engines without exposing them in the app. Examined exception handling, process-local locking, AtomicFile staging, resource closure, provider access, size limits, private storage and permissions. Executed the Android regression suite and lint on a narrow emulator.

This is a source and automated emulator audit. Physical keyboard/rotation/process-death, full provider workflows, visual RTL correctness, TalkBack and update preservation are not established by source inspection or saved-state simulation.

## Bugs found and fixed

| Priority | Symptoms, evidence and root cause | Smallest applied fix |
| --- | --- | --- |
| P0 | Malformed known JSON fields were coerced to strings or silently defaulted; duplicate project IDs could also crash keyed lists. A new isolated baseline test failed because malformed input was accepted. | Share strict known-field/identifier validation between local storage and backup validation; reject malformed/duplicate input and invalid outgoing models before writing. Preserve legacy defaults for absent optional fields and arbitrary string task statuses. |
| P0 | Saving a retained project/task dropped unknown JSON keys. Baseline regression failed with `No value for extension`. | Merge supported values into the validated stored object by retained project/task ID. Preserve nested unknown values without changing the root array or introducing fields/migrations. |
| P0 | A stale activity snapshot could replace newer saved content; `persistProjectChange` ignored its `current` argument. Baseline regression reproduced the overwrite. | Compare the stored models under the shared mutex before the UI write; reject stale edits. Serialize reads with writes/export-source reads to avoid AtomicFile read/write interference. |
| P0 | An interrupted initial `.new` file was treated as absent storage and could be overwritten by a fresh save. Substantiated by the original missing-file check. | Block fresh reads/writes when only the initial staging file exists; retain its bytes for recovery. |
| P0 | AtomicFile may log sync/rename failure instead of throwing; project/draft code could report Saved and discard recovery despite unpublished bytes. Confirmed in installed Android SDK AtomicFile source. | Explicitly sync, publish, then read back exact bytes and check that staging was consumed. A simulated silent publication failure now throws and retains original/staged bytes. Shared by project, draft and pending-export writes. |
| P0, dormant code | Snapshot/restore paths could overlap AtomicFile `.new`/`.bak` paths, truncating a source or consuming the safety snapshot. Distinct base filenames were insufficient. | Reject overlapping base/staging/backup paths before any write; reject pending atomic restore inputs. New isolated alias regressions pass. Restore remains disconnected from UI. |
| P1 | Unreadable storage escaped initial composition; project creation/task write errors escaped click callbacks and could crash. Substantiated by uncaught calls. | Explicit startup error with safe retry; block creation while loading fails. Catch creation/task failures, retain form/task input and unchanged displayed models, and permit retry. Editor failures continue propagating to its existing save/draft guard. |
| P1, test defect | CI #188 failed both unsaved-warning assertions. Reference fix rendered the warning but a Roadmap test still hung while scrolling tabs with the clock paused. Locally reproduced on unchanged baseline; later CI stalled at 36/75. | Resume the test clock after asserting forced save failure. Corrupt disposable storage continues blocking autosave. Keep all warning, content, byte-preservation and retry assertions. |
| P2 | Selected tab and task/new-project form input used ordinary remembered state. Rotation could reset the tab, leaving a restored dirty flag detached from its editor. | Save tab and form/task input; derive the parent dirty flag from the mounted editor instead of restoring a detached flag. Blueprint/Roadmap saved-state tests preserve mixed Arabic/English draft text and navigation guards. |
| P2 | Activity recreation during CreateDocument lost an in-memory export payload, silently producing no export. Provider reads/writes ran synchronously in callbacks; picker launch failure could escape. | Stage exact validated bytes in private cache; save only a random validated token in activity state. Consume/clear the token on success/cancel/error, reject missing cache explicitly, prevent duplicate preparation, catch picker-launch errors, and run provider IO off the main thread. Snapshot persistence is tested; actual picker recreation is still unverified. |

Severity reflects potential impact. Dormant-engine issues have no currently exposed restore entry point. Code-substantiated findings are distinguished above from directly reproduced failures.

## Files changed

- `MainActivity.kt`: validated/preserving storage, stale-write guard, startup/write errors, saved UI state and provider lifecycle handling.
- `LocalBackup.kt`, `ProjectWriteLock.kt`, `RestoreTransaction.kt`: shared validation and atomic-path independence.
- `AtomicWrites.kt`, `DraftStorage.kt`: verified atomic publication and draft protection.
- `PendingExport.kt`, `JsonExport.kt`: private durable picker snapshots and serialized source reads.
- `ManualSaveNavigationUiTest.kt`: demonstrated clock synchronization fix, with assertions retained.
- `StorageSafetyAuditTest.kt` (4), `StorageFailureUiTest.kt` (6), `ExportAndAtomicSafetyTest.kt` (5): 15 focused regressions.
- `.gitignore`: ignore generated Kotlin session files. JSON policy and release-gate audit references updated; evidence committed below.

## Test results and build status

Environment: local Android 15 / API 35 x86_64 `REDMAGIC_10_Pro_Test` emulator; 320 × 640, 160 dpi; animations disabled; JDK 17 and Gradle 8.13. All added storage/editor tests use disposable temporary/cache files, including an isolated filesDir for editor drafts. No physical device was connected or modified.

- Baseline targeted execution: four newly added storage regressions failed as expected; three existing manual-save cases completed and the Roadmap failure case hung. Stopped only that disposable instrumented process. This was not a successful suite run.
- Initial fixed targeted run: **12 passed, 0 failed/errors/skipped**.
- Intermediate full regression: **87 passed, 0 failed/errors/skipped**.
- Full regression after atomic-publication fix: **88 passed, 0 failed/errors/skipped**. [JUnit XML](audit-evidence/v0.2-2026-11-04/full-88.xml).
- Two additional editor-restoration tests initially paused before the editor rendered and failed to find its field. Corrected the test setup to assert the editor before pausing; no production workaround or weakened assertion.
- Final focused restoration/failure class on final production source: **6 passed, 0 failed/errors/skipped**, including those two additional cases. [JUnit XML](audit-evidence/v0.2-2026-11-04/restoration-6.xml). Across the 88-test full run and this focused run, **90 distinct tests passed**; this is not a claim of a single local 90-test full-suite run.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: **passed**. Final focused invocation also recompiled/repackaged the final production/test source.
- `:app:lintDebug`: **passed, 0 errors, 11 warnings**: old target/SDK and library versions, missing explicit app icon, and Android 12+ data-extraction configuration. No dependency/platform/signing changes were introduced to silence them.
- No standalone JVM unit-test sources are configured; tests run against Android's actual org.json/AtomicFile semantics.

## CI status at report authoring

- [#188 / 41692434027](https://github.com/karim-kmorsi/AI-Project-Manager/actions/runs/41692434027): full job log inspected; compilation passed, **75 tests, 2 failures**, both missing `تعديلات غير محفوظة` nodes in ManualSaveNavigationUiTest.
- [#189 / 41693990466](https://github.com/karim-kmorsi/AI-Project-Manager/actions/runs/41693990466) and [#190 / 41693999551](https://github.com/karim-kmorsi/AI-Project-Manager/actions/runs/41693999551), reference `a1748c4`: **cancelled during this audit after the local hang reproduction** to stop wasted CI usage. #190's retrieved log shows 36/75 completed with zero failures, then no progress from 18:35:15 UTC until cancellation at 18:40:06. It does not validate the reference fix. The local Roadmap scroll deadlock explains this stall; the cloud log alone does not name the blocked test.
- Final candidate CI is pending this batched push; consult the exact run IDs and conclusions in the chat handoff. No manual rerun, billing change, paid runner or spending increase was requested.

## Data safety

Existing unversioned JSON-array format, supported fields, IDs, task order/statuses and freeform text remain compatible. Missing legacy optional fields retain their defaults. Unknown keys on retained records now survive known-field edits. Duplicate/malformed known fields fail closed. Atomic writes, stale UI writes, failed saves, corrupted drafts, isolated interrupted writes, export-byte equality, other-project preservation and read-only preview boundaries have regression evidence.

There is no storage migration, live restore integration, user-data deletion, signing change, application identity/version change, redesign, new dependency, physical-device install or release publication. `main` is unchanged. Simulated legacy/recovery tests are not real-device update or real-data preservation evidence.

## Remaining issues, risks and release blockers

1. **Release gate remains open:** stable signing, version policy, physical-device keyboard/navigation/background/restart workflows and non-destructive update preservation require separate verification/authorization.
2. **P2 / unverified provider paths:** no end-to-end system CreateDocument/OpenDocument success/cancel/recreation or hostile/unavailable provider test. Private export snapshot tests do not establish ActivityResult callback behavior. Rotation while an already-returned preview is being read can cancel its display; select again. A cache eviction makes export fail explicitly. Orphan preparation snapshots can remain private until cache eviction.
3. **P2 / scale risk:** project and draft persistence still executes synchronously in editor/UI paths. Very large documents/project sets have not been profiled for UI stalls. Unbounded remembered text in saved instance state may exceed Android Binder limits. These are source-level risks, not demonstrated crashes in this audit.
4. **Concurrency limit:** mutex/CAS coordinates this process and supported UI writes, not arbitrary external/multi-process writers. The dormant snapshot/restore engines require further crash/concurrency validation before any future exposure.
5. **Compatibility limit:** fixtures are synthetic representative legacy data, not a safe copy from the installed phone. Unknown fields are preserved only for retained record IDs. Unsupported root formats remain rejected; this is not arbitrary future-schema support.
6. **P3:** missing launcher icon and symbolic `+` accessibility description; full TalkBack, Arabic-locale RTL/mixed-text visual QA, keyboard-visible Save on physical small screens and long preview layout remain unverified. No cosmetic redesign was attempted.

## Recommended next step

ChatGPT and the owner should review the candidate commit, post-push CI and this report, then separately authorize the comprehensive acceptance test with the outstanding physical-device/provider/signing checks explicitly included. **Do not merge, distribute or mark v0.2 Ready for Release from these engineering results alone.**
