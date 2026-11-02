# AI Project Manager — v0.2 release gate

Updated: 2026-11-02
Branch: `feature/v0-2-editor-ux`
Status: In development; not ready for release.

## Release scope (no new visual redesign)
- Offline creation and editing of projects, blueprint, roadmap, tasks and task verification states.
- Existing JSON export to an external document.
- Read-only inspection of an externally selected JSON backup, with errors and cancellation.
- Safe persistence of the existing unversioned JSON array schema.
- No automatic GitHub synchronization, no actual restore/merge, no new accounts or permissions.

## Confirmed baseline
- GitHub Actions workflow #162 completed successfully on commit `92252a3d0886d6ffce0cf2628886a3f217e9c2be`.
- Unit/instrumentation sources cover storage, export, previews, snapshots, isolated restore/recovery and failure injection.
- The isolated transaction/recovery engines are not wired to user-facing restore actions.
- No real-device install/update or signing-certificate compatibility check has been confirmed.

## Required checks before any APK update
1. Review active branch and latest CI for release candidate; diagnose failures from logs, do not rerun blindly.
2. Confirm real-device workflow with non-sensitive test projects: create, edit, save, restart, export, select backup, preview, cancel; verify no data changes after cancellation.
3. Compare installed APK signing certificate with candidate APK before updating without uninstalling.
4. Confirm regression test results, and do not label a build alone as Verified.
5. Obtain explicit approval for any installed APK update; no deletion or migration of user data.

## Deferred beyond v0.2
- Real restore/replacement/merge of user data. Isolated engines require deeper crash/concurrency verification before exposure.
- GitHub account integration / automatic repository sync.
- Cosmetic redesign or new data format.

## CI and cost policy
- Batch related tests and changes; do not create a commit for every tiny correction.
- Use one CI run to validate a coherent candidate when possible.
- Do not enable additional paid runners, billing, or overages without explicit approval.
- GitHub billing screenshot showed $12 included compute and $4.64 applied usage at that moment; this is a snapshot, not a current balance guarantee.
- If the remaining included quota becomes insufficient, stop expensive cloud builds rather than incur unapproved charges.

## Next engineering task
Audit test coverage for the user-visible create/edit/export/preview/cancel workflows and add only missing regression tests. Prefer a coherent single change and single CI verification. Do not wire real restore.

## Release status
Implemented functionality is not the same as a verified release. Ship only after the checks above and explicit user approval.
