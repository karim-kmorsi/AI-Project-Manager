# AI Project Manager — v0.2 release gate

Updated: 2026-11-03
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

## Confirmed v0.2 validation (2026-11-03)
- REDMAGIC 10 Pro device workflow reported successful: created test 02; autosave persisted the project across app restart; a task was marked VERIFIED and progress displayed 1/1; JSON export preview opened; closing the preview left project data unchanged.
- GitHub Actions CI #170 completed successfully: run `41474908726`, on commit `832ddde16215b7cef07f0556f27a0b1803bd3468`. The build, compile, artifact upload, and Android storage regression test steps all succeeded.
- Signing certificate SHA-256 values differ:
  - Previously installed APK: `649d5d7fd2b6925b6a8ec1269801712d332a4ba65001d38f9954de18bcc06241`
  - New GitHub APK: `f3e57a2ad9c3b55a218b5bcea7475f6912d93d714d981a70c890d2dcf2ad7c8c`
  - Current Ubuntu signing key: `d0e3ac3b24d6fa7b592072df7ed61e25afe3c9f44d9af93fa656c18bb6699bf6`
- The old app was removed with user authorization; the new app installed successfully. The certificate mismatch means the old installation could not be updated in place with the new APK; data continuity across uninstall/reinstall was not established by this result.

## Signing and versioning plan (future work; not applied)
- Select and securely back up one stable signing key, then configure release builds to use it consistently. Keep the private key and passwords out of the repository and CI logs; document a controlled recovery process.
- Treat the currently observed Ubuntu key fingerprint as a candidate to verify before adopting it as the long-term release key. Do not assume it matches the GitHub APK certificate.
- Before distributing any future APK, compare its certificate fingerprint with the installed release certificate. A key change requires an explicit migration path because Android will reject an in-place update signed by a different key.
- Establish `versionCode = 1` and `versionName = 0.1.0` for the first formally versioned release, then increase `versionCode` for every subsequent release and update `versionName` to the corresponding human-readable version. This is a proposal only; no version values were changed here.

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
