# v0.3.0 local candidate — final local verification

Date: 2026-11-17 (Asia/Kathmandu). Status: **local work complete with limits**;
**NOT an approved production release**. No phone installation or remote publication.

## Artifact and source

- Runtime baseline: `main` at `d1fba747b74f68bfefa252f8647588f855534fc1`.
- Only build packaging and documentation changed during finalization; no production
  Kotlin/resources/schema or existing tests changed. The finalization commit records them.
- Variant: `localCandidate`, release-derived, **not debuggable**; Debug test hosts,
  deterministic test picker/provider and the UI-test host manifest are absent.
- Package: `com.karim.aiprojectmanager.vnext`, version **0.3.0**, code **7**.
  The original `com.karim.aiprojectmanager` package is not replaced by this identity.
- Code 7 is above the previously inspected local VNext codes 2 and 6. Existing
  Debug/Release and instrumentation VNext configuration remains unchanged.
- Android minimum API 26, target 35; packaged ABIs include `arm64-v8a`, `armeabi-v7a`,
  `x86` and `x86_64`.
- Output folder: `/home/karim/Backups/AI-Project-Manager/v0.3.0-final-candidate-2026-11-17_231801-8eqtzvkn`.
- APK: `AI-Project-Manager-v0.3.0-code7-local-candidate.apk` (8,963,344 bytes).
- APK SHA-256: `af3fd3d15a1084a320cc2fd1b29cb479c3c5470071840806122ab15a85cbf107`.
- Certificate SHA-256: `301a9e4f477d0e53116caaa7bb8856afce8fd721aa0e4897d4b8ce7139e6e65f`.
- Signer: the **existing Android Debug certificate**, unchanged. A non-debuggable
  manifest does not turn this certificate into an approved production signing key.
- The genuine existing GitHub App Client ID was supplied for the build process
  only and checked against the previously verified local/CI input. No Client Secret
  or real OAuth access token was supplied to the build. Known synthetic Client ID,
  signing-key files and embedded access-token patterns were absent from the APK.

## Build and focused verification

One offline Gradle 8.13 / JDK 17 invocation assembled and linted the new variant:
`:app:assembleLocalCandidate :app:lintLocalCandidate`. It succeeded in 1m 23s.
Lint: **0 errors/fatal issues, 22 warnings and 1 hint**. Existing warning-level
findings are deferred; see the retained XML/HTML reports. No cache wipe, dependency
installation, new key, source checkout or existing-APK replacement occurred.

`apksigner verify` passed. `aapt` confirmed the package/version/code/ABIs and absence
of the debuggable flag. The APK contains no Debug test activity/provider declarations.
Old approved APK hashes and the existing keystore hash remained unchanged.

## Acceptance evidence and its scope

| Evidence | Actual result | Scope / limits |
|---|---|---|
| CI run 44120662731, attempt 2, baseline d1fba747 | 232 total: **226 passed, 6 skipped, 0 failures/errors** | Counts independently parsed from original JUnit XML, artifact 14347583782; no CI rerun |
| Local VNext upgrade code 2 to 6 | PASS | Two synthetic projects, five tasks, four statuses, language/theme and extension fields preserved; UI edit persisted after app restart |
| Real DocumentsUI export/restore at code 6 | PASS | Two UI-created projects/four tasks; exact export/read-back, both cancellation paths, exact confirmed restore, matching pre-replace recovery copy, malformed-file rejection, app restart |
| Final candidate update code 6 to 7 | PASS | Same isolated Android user retained first-install time/data inode; existing encrypted GitHub session restored after the update; no uninstall/clear |
| Final candidate cold launch and local disconnect | PASS | MainActivity/Settings worked, local disconnect remained effective after force-stop/relaunch |

Full project-data acceptance at code 2 to 6 is inherited for unchanged production
storage code; it was **not** rerun as a direct code 2 to 7 fixture test. Restart here
means force-stop/relaunch, not Android reboot or process-death/power-loss acceptance.
Recovery ordering is supported by existing transaction code and file timestamps;
no injected mid-write crash was tested. The malformed external document was a
purpose-built MediaStore fixture; valid exports and projects came from the real UI.

Persistent evidence, original reports, hashes and screenshots are retained in the
output folder (`prior-acceptance/`, `final-runtime/`, `apk-verification.json`, build
log and Lint reports). No credential storage was copied into these records.

## Real GitHub acceptance

The owner completed the already-started authorization for `karim-kmorsi`.
Account verification and session persistence after app restart passed. Public
repository discovery and a public repository preview loaded using GET requests;
no import, private repository content read or remote write was performed.
The same existing session survived updating to the final APK. Local disconnect
then passed and stayed disconnected after restart. This **does not revoke** the
GitHub account's server-side app authorization.

`karim-kmorsi/AI-Project-Manager` was not observed in the inspected visible
repository list. Pagination/target-specific access was not exhaustively verified;
this is not proof that access is denied. No permission/installation setting changed.
The exact registration permissions and Device Flow app display name were not
independently inspected. Do not claim access to every user repository.
Reconnection was **not run**: it would require a fresh authorization, which was
not allowed by this finalization. Token refresh is not implemented; expired user
tokens require reauthorization. No promise of indefinite session continuity is made.

## Deferred six CI cases (P2 for this local candidate)

These remain **skipped, not passed**, and were not rerun just to close a checklist:

1. `CaretVisibilityUiTest.roadmapCaretVisibleLtr`.
2. `CaretVisibilityUiTest.roadmapCaretVisibleRtl`.
3. `CaretVisibilityUiTest.blueprintCaretVisibleLtr`.
4. `CaretVisibilityUiTest.blueprintCaretVisibleRtl`.
5. `RealDocumentsUiRecreationTest.productionActivityRecreatesBeforeRealDocumentSelection`.
6. `TaskListControlsUiTest.narrowLargeFontActionsStayWithinCardAndRemainUsable`.

The caret cases need real 2x system fonts and a dedicated environment. The old
DocumentsUI recreation driver requires its exact historical fixture and has stale
UI expectations; the successful current manual round trip does **not** establish
that recreation case. Narrow 2x-font acceptance remains opt-in after a prior idle
hang. This candidate makes no blanket large-font, TalkBack, recreation or OEM claim.

## Findings and installation/update rules

- **P0:** no new confirmed critical/data-loss defect found in the reviewed scope.
- **P1 for local packaging/basic startup:** none remaining after build/verification.
- **Production/phone gates remain open:** actual installed phone package, version
  and signer were not inspected (no phone was connected); REDMAGIC installation
  was not attempted. Production signing is not approved. Do not infer phone
  upgrade safety from the emulator or the candidate's local certificate.
- **P2 / scoped limits:** six skips above, warning-level Lint, broader hardware and
  lifecycle acceptance, genuine GitHub reconnection/expiry and intended-repository
  availability. These are disclosed limits, not newly implemented features.

The APK can be considered for a **fresh install of the isolated `.vnext` app** on
an owner-approved compatible device. If that package already exists, first inspect
its real signer and version: update only on matching certificate and a compatible
code. Never uninstall, clear user data, downgrade or bypass signature checks.
This APK is not an in-place update for the base-package v0.2.0 installation.
Export important data before any separately approved real-device update.

A new production key/change of certificate, real-phone installation, new GitHub
authorization, push/tag/Release each requires the separate approval applicable to
that action. None was performed. APK production, installation readiness within
these limits, production approval and publication are separate outcomes.
