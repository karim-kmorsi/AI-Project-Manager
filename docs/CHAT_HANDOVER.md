# AI Project Manager — Project Handover

**Prepared:** 2026-11-07
**Status:** Historical handover snapshot; supplemented by docs/DISCUSSED_IDEAS.md
**Release:** NOT Ready for Release

## Current publication update — 2026-11-07

The development branch was pushed and verified at `86f2a4082711e3b3f647371762509a31fff4586c` with a clean local working tree. The additional discussed-ideas record was published subsequently in `docs/DISCUSSED_IDEAS.md`. References below to eight unpublished commits, untracked documents, and pending push are historical snapshots and **no longer describe the current GitHub state**. Reverify the branch HEAD before new work. Codex Desktop was unavailable at handover; execution may move to another approved tool. Release remains NOT Ready for Release.

## 1. Project identity

- Application: AI Project Manager
- Platform: Android
- Technology: Kotlin + Jetpack Compose
- Repository: `karim-kmorsi/AI-Project-Manager`
- Branch: `codex/baseline-reconciliation-v0-2`
- Local repository: `/home/karim/Documents/ChatGPT/AI Project Manager App`
- Last verified local HEAD: `f5bee4f6012ac199b2d23aaf89f729655652da91`
- Last checked GitHub HEAD: `94757c60744a66d0c5a514090f304f287851738b`
- Eight local commits ahead of the last checked GitHub HEAD.
- Working tree was clean at the last verification.
- No push has been performed.

These are snapshots. Verify current state before taking action.

## 2. Product purpose

AI Project Manager is a personal Android application for managing
development projects, tasks, project information, decisions, progress,
guidance and supporting evidence.

The product is more than a generic task list.

Distinguish the broader product vision from the functionality actually
implemented. A stored `VERIFIED` status is not independent proof of
execution or verification.

## 3. Authoritative documents

Read these before continuing:

1. `AGENTS.md`
2. `docs/PROJECT_STATUS.md`
3. `docs/ROADMAP.md`
4. `docs/RELEASE_GATE_V0_2.md`
5. `docs/V0_2_APPROVED_PLAN.md`
6. `docs/JSON_SCHEMA_AND_MIGRATION_POLICY.md`
7. `README.md`

`AGENTS.md` and `docs/ROADMAP.md` were reconstructed on 2026-11-06.
They are current reconstructed documents, not recovered historical
originals.

Historical status entries may be outdated. Check the latest evidence.

## 4. Implemented functionality

According to the repository documentation and subsequent development
reports, the application includes:

- Project creation, listing, selection and basic editing.
- Project name/description search.
- Blueprint and Roadmap editors.
- Save, drafts, recovery and navigation safeguards.
- Task creation and task-title editing.
- Existing manual task statuses.
- Task-title search and status filtering.
- Offline next-step guidance and contextual tips.
- Project/task counts and pending-task information.
- JSON export and validated read-only backup preview.
- Local persistence with compatibility safeguards.
- Settings and storage disclosure.
- System, Light and Dark theme preference.
- RTL/LTR-aware input behavior.
- Approved visual identity and application icon.
- Accessibility and constrained-layout improvements.

Verify each feature against the actual implementation before changing it.
Implementation alone does not establish release acceptance.

## 5. Eight unpublished local commits

Base GitHub HEAD at last check: `94757c6`.

| Commit | Scope |
|---|---|
| `81cbf8e` | Adapt forms, editors and toolbar to constrained space |
| `f588fe5` | Keep editor Save reachable without window panning |
| `1e792af` | Preserve readable editor space with the keyboard |
| `75b8de7` | Add rendered editor caret-visibility tests |
| `e3d9d72` | Compact conflict baselines and retain task context |
| `49ada6e` | Document saved-state measurements and validation |
| `08dccaa` | Synchronize payload setup and document Android verification |
| `f5bee4f` | Add phase checkpoints and record transport blocker |

Git reported 14 changed files, 1,667 added lines and 102 deleted lines.

Changes include production Compose code, AndroidManifest.xml, JVM tests,
Android instrumentation tests and docs/PROJECT_STATUS.md.

No GitHub push has been performed.

## 6. Saved-state reliability

The local implementation introduced compact conflict-baseline
representations using canonical SHA-256 fingerprints rather than
duplicated full document baselines.

Codex reported compatibility with previously restored state and
fail-closed handling of malformed tokens.

Task query/filter/scroll context is preserved per project across
supported restoration and navigation paths.

### Measurements

- Synthetic JVM serialization: 797,567 → 143 bytes.
- Android Parcel large baseline slice: 1,208,160 → 408 bytes.
- Compact baseline with 131,072-unit pending input: 262,552 bytes.
- Whole-Activity small metadata: 4,304 bytes.
- Whole-Activity large pending metadata: 266,384 bytes.
- Whole-Activity large task title: 5,248 bytes.

These are different fixtures and scopes. A Parcel slice is not the
whole Activity Bundle.

Large pending-document and accumulated multi-project Bundle measurements
remain unfinished.

## 7. Verification evidence

### Host verification reported by Codex

- 91 JVM tests passed.
- Debug APK assembly passed.
- Android test APK assembly passed.
- Lint: zero errors, 12 existing warnings.

These results apply to the reported development batch and must not be
represented as a complete retest of all later changes.

### Six Android instrumentation tests passed

1. `largeTaskTitleActivityBundle`
2. `parcelSlicesCharacterizeBaselineAndPendingInputSeparately`
3. `largeMetadataActivityBundleWithPendingDescription`
4. `smallMetadataActivityBundle`
5. `queryFilterAndScrollSurviveActivityRecreationAndMenuRestoresClosed`
6. `tabRoundTripAndProjectSwitchesRetainIndependentContextsWithoutWrites`

The initial `largePendingDocumentActivityBundle` test failed because
expected Compose content was unavailable when the test clock was frozen.

A test-only readiness correction was added, but has not been confirmed
by a passing runtime execution.

### Six unfinished Android checks

1. `TaskContextRestorationUiTest.accumulatedProjectContextsMeasureActivityBundleAndRestoreIndependently`
2. `SavedStatePayloadUiTest.largePendingDocumentActivityBundle`
3. `ProjectMetadataUiTest.transientFailureKeepsMetadataAndRetrySucceeds`
4. `ProjectMetadataUiTest.conflictReloadRequiresConfirmationAndAllowsEditAgainstNewBase`
5. `TaskTitleUiTest.failedSaveRetainsInputAndAllowsRetryWithoutChangingStatus`
6. `TaskTitleUiTest.conflictReloadRetainsInputUntilConfirmedAndPreservesNewStatus`

The latest attempt executed zero of these six because installation
encountered `adb: device offline`.

Do not mark them as passed.

## 8. Emulator and ADB incidents

An earlier retry suffered an unexpected emulator guest reboot.
Evidence suggested a guest kernel fault, but the exact cause is unknown.

No causal connection to the Compose readiness failure was established.

A later attempt encountered `adb: device offline` during APK installation.
No new guest reboot was established.

### Latest standalone infrastructure check

- Disposable API 35 AVD: `AI_PM_ADB_Stability_6px68qth`
- Serial: `emulator-5584`
- RAM: 2,560 MiB
- Boot completed at 01:01:25 Nepal time.
- Readiness checks: 01:01:31–01:02:31 Nepal time.
- 13/13 checks passed across 60.112 seconds.
- Boot ID unchanged; no interruption observed.
- Disposable AVD cleaned up.
- Existing AVDs reportedly preserved.

This establishes a successful standalone ADB readiness check, not APK
installation or instrumentation stability.

Reported evidence paths:

- `/home/karim/.codex/ai-pm-state-completion-2ktn49hf/REPORT.md`
- `/home/karim/.codex/ai-pm-adb-stability-6px68qth/REPORT.md`

These reports were not independently read for this handover.

## 9. Git review and publication

The owner verified:

- Correct development branch and local HEAD.
- Clean working tree.
- GitHub HEAD is an ancestor of local HEAD.
- Exactly eight additional local commits.
- `git diff --check` showed no whitespace errors.
- No signing-configuration or JSON-schema files in the changed-file list.
- Four `token` keyword matches in EditorSnapshot.kt and PROJECT_STATUS.md.
- Narrow literal-assignment checks returned False for all four.

This is limited screening, not a comprehensive security audit.

No push has been authorized or performed.

## 10. Binding restrictions

Without separate explicit authorization:

- Do not modify or merge `main`.
- Do not push to GitHub.
- Do not change JSON schema or perform migrations.
- Do not delete or overwrite user data.
- Do not change signing or Application ID.
- Do not install, uninstall or modify the app on a physical device.
- Do not activate dormant import, restore or merge engines.
- Do not radically redesign the approved UI.
- Do not introduce dependencies or global system changes incidentally.
- Do not claim tests passed without execution evidence.
- Do not run heavy physical-device, PD/DU or actual process-death
  acceptance tests.

## 11. Working method

ChatGPT is the project manager and reviewer.
Codex Desktop is the local implementation agent.

For each approved development batch:

1. Verify repository, branch, HEAD and working tree.
2. Read authoritative documents.
3. Inspect existing implementation.
4. Select bounded roadmap work prioritizing dependencies.
5. Reuse existing architecture and persistence APIs.
6. Add focused tests.
7. Diagnose failures before repair.
8. Use at most two evidence-based repair attempts per defect.
9. Run required final build and existing automated test suites.
10. Review diff, update status and create clear local commits.

For emulator tests, use a disposable AVD and explicit serial.
Preserve unrelated AVDs and physical devices.

On unexpected reboot, kernel fault or transport loss, preserve evidence
and stop instead of retrying automatically.

## 12. Open risks and release gates

- Six Android checks unfinished.
- Two Bundle measurements unfinished.
- Corrected pending-document test not runtime verified.
- Emulator kernel-fault root cause unknown.
- Actual process-death acceptance pending.
- Broader accessibility, IME/cursor, large-font and OEM acceptance pending.
- Real backup transport/device-transfer behavior unverified.
- Stable signing and safe physical-device update acceptance pending.
- Full Arabic/English in-app language selection not implemented.
- Real import/restore/merge outside the current approved release scope.
- Final release-gate review and owner approval pending.

Application remains NOT Ready for Release.

## 13. Recommended next actions

1. Reverify local and remote Git state.
2. Review and approve this handover and the decision register.
3. Review documentation diff before any local commit.
4. Obtain separate authorization before GitHub push.
5. Run the six unfinished Android tests individually after a sustained
   readiness gate when Codex is available.
6. Keep the release gate open until all required acceptance is complete.

## 14. Instructions for the next conversation

Treat this document as a dated context snapshot, not proof of the
current repository state.

Read the repository's actual documents and verify HEAD before acting.

Distinguish implemented features, passed tests, compiled-only tests,
reported evidence, open risks and unapproved work.

Do not assume GitHub contains unpublished local commits.

Work one verified step at a time.

**Final application status: NOT Ready for Release.**
