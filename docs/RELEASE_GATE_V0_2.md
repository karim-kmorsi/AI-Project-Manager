# AI Project Manager — v0.2 release gate

Updated: 2026-11-04
Branch: `codex/baseline-reconciliation-v0-2`
Status: In development; not ready for release.

## Current local reconciliation baseline — 2026-11-04

Application/code baseline: `8824a110afbd2e936e6a14ee5464d0e929b59976`.
Baseline Reconciliation is implemented and locally tested; **NOT Ready for Release**.
The historical checkpoints below retain their original commit-specific findings and CI results.

Six reference features are integrated: project-name/description search, offline Next-Step Guidance,
editor/task Contextual Tips, project-creation guidance, current task-status summaries, and pending-task
titles/statuses. These read saved data without inferring independent verification. Unknown task
statuses and task order remain preserved. Original local backup-preview cancellation and
lifecycle/provider protections remain intact.

Overview now scrolls within the available area below the fixed project tabs. Existing content,
16dp padding, 12dp spacing and navigation guards are preserved; scroll state is scoped to the
selected project. A 40-task regression checks end-content access, navigation, project switching
and unchanged stored bytes. The list still renders eagerly.

Verification on this code, recorded before the local code commit:

- Complete local JVM suite: **22 passed, 0 failures/errors/skips**.
- Actual Android instrumentation: **107 passed, 0 failures/errors/skips**, 52.824 seconds,
  on `emulator-5554`, `REDMAGIC_10_Pro_Test`, Android 15/API 35, 320 × 640 at 160 dpi.
- Debug and Android test APK assembly: **previously reported successful**; no standalone build
  transcript is preserved in this documentation update.
- Lint: **0 errors, 12 existing warnings**; this does not imply warning-free validation.
- Local work only: no GitHub push or main-branch changes were performed during reconciliation;
  no cloud CI validation is claimed for this baseline.

See the [reconciliation audit and preserved evidence](V0_2_LOCAL_REGRESSION_AUDIT.md#baseline-reconciliation--2026-11-04)
for provenance, counts and limitations. Matching emulator debug certificates permit the tested
emulator update; they do not close stable release-signing or phone update/data-continuity gates.
Visual/RTL/accessibility, very large lists/documents, actual system document providers and grants,
process death, physical-device workflows, stable signing and non-destructive update preservation
remain outstanding. V1.5 implementation is not included or authorized by this checkpoint.

## DU-01 evidence update — 2026-11-04

[DU-01 real DocumentsUI acceptance](V0_2_LOCAL_REGRESSION_AUDIT.md#du-01-real-documentsui-acceptance--2026-11-04)
passed the scoped local-Downloads export, read-only preview and cancellation checks on tested commit
`fe6df94ae6ade1142c07a4bee5e2f030ad1ba14c`. The
[durable privacy-reviewed evidence manifest](audit-evidence/du-01-2026-11-04/manifest.json)
keeps earlier INCONCLUSIVE attempts separate. Broader provider/lifecycle, visual/accessibility,
scale, signing and physical-device gates remain open; **NOT Ready for Release**.

## Reduced v0.2 release scope
This gate reconciles the current release with the broader historical requirements in [V0_2_APPROVED_PLAN.md](V0_2_APPROVED_PLAN.md); deferred requirements remain on the roadmap. Earlier MVP, Gate 0 and next-step documents are historical snapshots where they conflict with this gate. Inclusion here describes release intent and implemented behavior, not completed acceptance testing.

- Offline project creation with name/description, listing and selection. There is no project name/description edit form.
- Freeform Blueprint/Roadmap editing, explicit Save, autosave drafts and saved/pending/error feedback.
- Simple task creation and manual status changes, including VERIFIED; progress counts tasks with that stored status. The app does not independently verify evidence or code.
- Existing-format JSON export to an external document.
- Read-only inspection of an externally selected JSON backup, with errors, closing and cancellation; no import is applied.
- Safe persistence of the existing unversioned JSON array schema.
- No automatic GitHub synchronization, no actual restore/merge, no new accounts or permissions.

## Confirmed baseline
- GitHub Actions workflow #162 completed successfully on commit `92252a3d0886d6ffce0cf2628886a3f217e9c2be`.
- Unit/instrumentation sources cover storage, export, previews, snapshots, isolated restore/recovery and failure injection.
- The isolated transaction/recovery engines are not wired to user-facing restore actions.
- At this earlier baseline, real-device install/update and signing compatibility were unconfirmed. The later device report below does not establish in-place update compatibility.

## Confirmed v0.2 validation (2026-11-03)
- REDMAGIC 10 Pro device workflow reported successful: created test 02; autosave persisted the project across app restart; a task was marked VERIFIED and progress displayed 1/1; JSON export preview opened; closing the preview left project data unchanged.
- GitHub Actions CI #170 completed successfully: run `41474908726`, on commit `832ddde16215b7cef07f0556f27a0b1803bd3468`. The build, compile, artifact upload, and Android storage regression test steps all succeeded.
- Signing certificate SHA-256 values differ:
  - Previously installed APK: `649d5d7fd2b6925b6a8ec1269801712d332a4ba65001d38f9954de18bcc06241`
  - New GitHub APK: `f3e57a2ad9c3b55a218b5bcea7475f6912d93d714d981a70c890d2dcf2ad7c8c`
  - Current Ubuntu signing key: `d0e3ac3b24d6fa7b592072df7ed61e25afe3c9f44d9af93fa656c18bb6699bf6`
- The old app was removed with user authorization; the new app installed successfully. The certificate mismatch means the old installation could not be updated in place with the new APK; data continuity across uninstall/reinstall was not established by this result.

## Confirmed automated CI fix — 2026-11-03
- Commit `06e9a50c23ea35ec1ca7929e00ddc642a54fcd0d` fixes only `ProjectWorkflowUiTest` tab navigation: scroll tabs into view before clicking on narrow displays. The unchanged test reproduced the missing Add-button semantics failure at CI's 320 × 640 size; all original assertions were preserved. No production behavior changed.
- Local Android 15 emulator: fixed targeted test **1 passed**; full suite **58 passed, 0 failures, 0 errors, 0 skipped**, at the same narrow display size.
- [Push run 41643830854](https://github.com/karim-kmorsi/AI-Project-Manager/actions/runs/41643830854): **completed / success**, 58 instrumentation tests.
- [Pull request run 41643842358](https://github.com/karim-kmorsi/AI-Project-Manager/actions/runs/41643842358): **completed / success**, 58 instrumentation tests.
- Both runs are for that commit and include successful debug compilation and emulator regression steps. They supersede failed runs `41524878527` and `41524890832` on `435adace9779faf92612e2744e4bcd47f7bd31e7`.
- Automated emulator results do not verify the current candidate on a physical device, signing continuity, in-place update/data preservation, or every editor/picker path. See the [coverage gaps](V0_2_LOCAL_REGRESSION_AUDIT.md#remaining-real-gaps-and-limits).

## Signing and versioning plan (future work; not applied)
- Select and securely back up one stable signing key, then configure release builds to use it consistently. Keep the private key and passwords out of the repository and CI logs; document a controlled recovery process.
- Treat the currently observed Ubuntu key fingerprint as a candidate to verify before adopting it as the long-term release key. Do not assume it matches the GitHub APK certificate.
- Before distributing any future APK, compare its certificate fingerprint with the installed release certificate. A key change requires an explicit migration path because Android will reject an in-place update signed by a different key.
- The build currently declares `versionCode = 1` and `versionName = 0.1.0`. A release version/update policy still needs review; increase the code for subsequent releases and select the appropriate release name. No version values were changed in this documentation reconciliation.

## Required checks before any APK update
1. Review active branch and latest CI for release candidate; diagnose failures from logs, do not rerun blindly.
2. Confirm real-device workflow with non-sensitive test projects: create, edit, save, restart, export, select backup, preview, cancel; verify no data changes after cancellation.
3. Compare installed APK signing certificate with candidate APK before updating without uninstalling.
4. Confirm regression test results, and do not label a build alone as Verified.
5. Obtain explicit approval for any installed APK update; no deletion or migration of user data.

The current candidate still needs physical-device checks of keyboard-visible Save, Blueprint and Roadmap saving, navigation, background/restart persistence, export and backup-picker cancellation/error paths. The earlier REDMAGIC report is partial historical evidence, not completion of those checks. Stable release signing and a non-destructive in-place update with old-data preservation remain open. Localization, themes, archival and restore acceptance criteria from the broader plan belong to their deferred deliveries.

## Deferred beyond v0.2
- Real restore/replacement/merge of user data. Isolated engines require deeper crash/concurrency verification before exposure.
- Editing/expanding project metadata, archive/unarchive, richer task fields and structured milestone links.
- Broader task/decision search, active/archived and task-state filters, decision records, audit log and history. Simple project-name/description search is implemented in the current local baseline.
- Multilingual settings and selectable light/dark/system themes.
- GitHub account integration / automatic repository sync.
- Cosmetic redesign or new data format.

## Draft PR #3 review — snapshot before this documentation commit
[PR #3](https://github.com/karim-kmorsi/AI-Project-Manager/pull/3) is open and Draft, with 81 commits and 25 changed files at `06e9a50`. Its head is `feature/v0-2-editor-ux`; its base is `feature/v0-2-data-safety` (`bba63e9ef8875ab59eecaf23d1c8e5a81958b022`), an ancestor of the head. That target remains appropriate for review against the existing data-safety foundation; it is not a request to merge into `main`.

The title, “v0.2: keep Blueprint and Roadmap Save button visible”, and “First UI fix only” description are outdated. The diff includes autosave/drafts, export/preview, task status behavior, isolated restore/recovery engines, tests, CI and documentation. The reduced user-facing release scope is appropriate, but the accumulated PR is broader and needs explicit review of those dormant engines and coverage limits. Recommend refreshing its title/body to that actual scope with validation and deferred features before readiness review. No PR metadata, target or Draft state is changed here; any metadata update requires explicit authorization.

## CI and cost policy
- Batch related tests and changes; do not create a commit for every tiny correction.
- Use one CI run to validate a coherent candidate when possible.
- Do not enable additional paid runners, billing, or overages without explicit approval.
- GitHub billing screenshot showed $12 included compute and $4.64 applied usage at that moment; this is a snapshot, not a current balance guarantee.
- If the remaining included quota becomes insufficient, stop expensive cloud builds rather than incur unapproved charges.

## Next engineering task

Current local next step: review and close the documentation/evidence handoff for the baseline
above. Subsequent system DocumentsUI/provider, process-death, visual/accessibility, scale,
physical-device and signing/update checks need separately scoped authorization. Do not infer
release readiness or V1.5 authorization. The following paragraph records the prior 106-test
checkpoint and its proposed follow-up, rather than the latest test count.

The [2026-11-04 local follow-up](V0_2_LOCAL_REGRESSION_AUDIT.md#autonomous-engineering-follow-up--2026-11-04) extends the existing manual Save/navigation tests with actual Blueprint/Roadmap activity recreation, failed-save recovery and real ActivityResult callbacks using controlled debug picker/provider fixtures. All 106 local instrumentation tests pass; overlapping backup previews now cancel older reads to prevent stale results. These local changes have not been pushed or run in cloud CI.

Next, cover actual system DocumentsUI export/preview success and cancellation on a disposable emulator; fixture callbacks do not establish system picker, third-party provider or URI-grant behavior. Actual process-death, physical-device and signing/update gates remain open. Do not wire real restore or treat this engineering result as release acceptance.

## Release status
Implemented functionality is not the same as a verified release. Ship only after the checks above and explicit user approval.

## Comprehensive stabilization audit — 2026-11-04

See [the audit report](V0_2_STABILIZATION_AUDIT_2026_11_04.md) for confirmed storage/lifecycle fixes, focused regressions, exact local evidence, CI #188–190 diagnosis and remaining limits. Local full regression passed 88 tests; the final 6-test failure/restoration class adds 2 further distinct editor-restoration tests (90 distinct passing tests across those runs). Final candidate cloud results are reported separately in the chat handoff. These engineering checks do not constitute the separately authorized final acceptance test or close the physical-device/signing gates.
