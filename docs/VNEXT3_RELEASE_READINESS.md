# VNext 3 Release Readiness

> Current status (2026-11-17): see [v0.3.0 local candidate evidence and limits](releases/v0.3.0/LOCAL_CANDIDATE.md).
> The checkpoint below is historical; its old version numbers and unexecuted-test
> statements do not describe the current candidate. Historical requirements are
> retained, and deferred/physical-device/production-signing gates are not waived.

Updated: 2026-11-14 (Asia/Kathmandu)  
Branch: `feature/vnext3`  
Starting commit: `a9afb300b9efe8b3aea86ffd257998e5ee463959`  
Decision: **NO-GO pending owner acceptance of critical data-safety and upgrade gates**

## Delivered in this cycle

- Added confirmed task deletion and a transition back to `NOT_STARTED`. Existing
  status values, IDs, project progress rules, and persistence safeguards remain
  in use.
- Added a deterministic on-device roadmap analyzer. It recognizes common
  Markdown phase headings, checklists, imperative bullets, and numbered
  sections. Users can review, edit, exclude, deduplicate, and explicitly save
  proposals. Ambiguous entries and completed checkboxes are skipped. The UI
  reports skipped entries and caps proposals at 200 (300 characters per title).
  Phase context is shown during review, not written to a new task field.
- Connected the validated legacy-array backup preview to a confirmed
  replace-all restore flow. The selected file is staged privately, limited to
  8 MiB, revalidated before commit, and protected by candidate/live-file
  fingerprints. The restore uses `AtomicFile`; before replacing existing
  storage it writes and verifies an exact app-private recovery snapshot. A
  first-run restore with no project file is supported. Corrupt existing bytes
  can be replaced only after explicit confirmation and are snapshotted as raw
  bytes. This is replacement, not merge.
- Kept the unversioned JSON-array schema and GitHub's read-only API behavior.
  VNext package identity remains `com.karim.aiprojectmanager.vnext`; the
  VNext-only version advances from code 1 to code 2. Signing configuration and
  the existing debug signing key were not changed.
- Added English and Arabic strings and height-bounded, scrollable dialogs for
  the new proposal and restore flows.

## Verification performed

Using JDK 17 and the repository Gradle wrapper:

```text
./gradlew :app:assembleDebug \
  :app:testDebugUnitTest \
  --tests com.karim.aiprojectmanager.RoadmapTaskProposalsTest \
  --tests com.karim.aiprojectmanager.TaskManagementTest \
  :app:assembleVnext \
  :app:assembleVnextAndroidTest \
  :app:lintDebug :app:lintVnext --no-daemon
```

- Focused host tests: **6 passed, 0 failed**.
- Debug and VNext APK assembly: passed.
- VNext Android-test source compilation and test APK assembly: passed. No
  instrumentation test was run.
- Debug and VNext Lint: passed with **0 errors, 27 warnings, 1 hint** per
  variant. Existing dependency, API-target, resource, and icon warnings remain.
- `git diff --check`: passed.
- The SDK emitted a non-fatal warning about SDK XML version 4.

The API 35 emulator was not launched or restarted. No APK was installed, no
application data was read or changed, no instrumentation tests were run, and no
live GitHub requests or remote writes were made. No credential or token was
read or reported.

## APK record

| Property | VNext build |
|---|---|
| Path | `app/build/outputs/apk/vnext/app-vnext.apk` |
| Application ID | `com.karim.aiprojectmanager.vnext` |
| Version | `0.1.0-vnext3` (code 2) |
| Size | 11,405,100 bytes |
| APK SHA-256 | `b187882c86a629b0fd638ba121beec3a9d95f615f66c29cedd6abc452d10cedc` |
| Signing-certificate SHA-256 | `301a9e4f477d0e53116caaa7bb8856afce8fd721aa0e4897d4b8ce7139e6e65f` |
| Android-test APK | `app/build/outputs/apk/androidTest/vnext/app-vnext-androidTest.apk` |

The package and signing-certificate hashes match the previously documented
VNext identity/signature, and code 2 is greater than the previously documented
code 1. The installed package's current version and signer were not inspected,
and no in-place update was attempted. The built APK is debug-derived; this
record does **not** establish production signing or an authorized installation.
The configured GitHub App Client ID was consumed by Gradle but is not included
in this report; no token was accessed.

## Feature inventory and limitations

| Area | Current evidence and remaining work |
|---|---|
| Projects and local persistence | Existing create/edit/navigation and atomic JSON storage were inspected and retained. Restart and failure-recovery behavior were not rerun in this cycle. |
| Tasks | Create, title edit, search, status changes, and manually recorded `VERIFIED` progress pre-existed. Delete and reset-to-`NOT_STARTED` were added. New host tests cover selected deletion and preservation of other task records; Android UI behavior was not run. |
| Roadmap proposals | Deterministic local parser and review UI were added. Host tests cover phase association, checkboxes, conservative skipping, deduplication, and the proposal cap. The UI confirm/cancel, persistence, and restart flow remain unexecuted on Android. |
| GitHub | Existing authenticated discovery/import/refresh and encrypted session design were inspected; this cycle did not change GitHub operations. No live authorization, target-repository access, or refresh acceptance was performed. Prior project-status entries record unresolved repository access. |
| Backup export and restore | Existing export and read-only preview were retained; restore is now exposed only through a second explicit replace-all confirmation. The new restore transaction tests were added to Android-test sources and compiled, but not executed. No export/restore round trip was verified on Android. |
| Offline use | Local-first architecture remains. Offline project/task operations were not manually exercised in this cycle. |
| Languages and accessibility | English/Arabic resources compile. Dialog bodies are bounded and scrollable. No visual, font-scaling, screen-reader, keyboard, or small-screen emulator acceptance was performed. |
| Commit-to-task linking | Deferred to January 2027; it is optional and was not allowed to delay the data-safety work. |

### Backup and restoration instructions

1. Before replacing local data, export a fresh JSON backup from **Settings →
   Data and backup → Export JSON** and save it outside the app.
2. Select **Preview backup**, inspect the project/task counts and listed project
   names, choose **Review restore**, then confirm **Replace projects** only if
   replacing every current project is intended.
3. Only the legacy JSON array is accepted, with an 8 MiB maximum. Invalid or
   changed backups are rejected. Restore does not merge projects.
4. When a current project file exists, an exact recovery snapshot is retained
   inside app-private storage under `files/restore-recovery/`. It is not
   currently exportable through the UI; do not treat it as a substitute for an
   external backup. On first-run restore, there is no prior file to snapshot.

The new Android transaction tests cover successful replacement, missing
storage, corrupt-original preservation, invalid input, stale fingerprints,
write interruption, and post-commit verification failure. They were **compiled,
not executed**. Restoration safety therefore remains an open release gate.

## Release gates

| Gate | Status | Evidence / blocker |
|---|---|---|
| A — Data safety | **BLOCKED** | Existing atomic/stale-write safeguards were inspected. The new Android restore tests and full backup/restore round trip were not executed; no restart/recovery acceptance was performed. |
| B — Core functionality | **PARTIAL** | Six focused host tests passed. Existing project/GitHub flows and the new Android proposal/deletion UI were not reaccepted in this cycle. |
| C — Offline reliability | **UNVERIFIED** | No offline runtime acceptance in this cycle. |
| D — Security | **PARTIAL** | Existing Keystore-backed GitHub session handling and read-only API paths were inspected; no credential was read and no live security/authentication test was performed. |
| E — Upgrade compatibility | **BLOCKED** | Package, version, and certificate metadata are recorded; installed-package metadata and in-place update compatibility were not checked. Installation requires separate owner approval. |
| F — UI usability | **UNVERIFIED** | Localized UI compiles; no emulator, accessibility, RTL visual, or small-screen acceptance was performed. |
| G — Build and verification | **PARTIAL** | Builds, focused host tests, test-source compilation, APK metadata inspection, and Lint passed. Android instrumentation and manual acceptance remain pending. |

There is no verified basis for GO. The unexecuted restore transaction and
unverified installed-app upgrade path are critical blockers, so the current
decision is **NO-GO**.

## Owner acceptance checklist

- [ ] Approve and run isolated Android tests for backup preview and restore.
- [ ] Verify a complete export/restore round trip with distinct task statuses
  and that malformed backups leave existing projects untouched.
- [ ] Verify task deletion, roadmap proposal review/cancel/confirm, Arabic RTL,
  English LTR, and small-screen controls on an explicitly approved disposable
  emulator.
- [ ] Verify offline project/task operation.
- [ ] Confirm the installed VNext package, version, and signer before any
  update; approve installation separately. Do not uninstall, clear data, or
  touch the protected original package.
- [ ] Reconfirm authenticated access to the intended GitHub repositories and
  read-only API behavior.

No commit, push, merge, tag, release, emulator action, or installation was
performed. Release status remains **NOT Ready for Release**.
