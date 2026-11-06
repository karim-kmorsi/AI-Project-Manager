# AI Project Manager — current contributor instructions

Reconstructed on 2026-11-06 (Asia/Kathmandu), with owner authorization.
This is a newly authored current document, **not a recovered historical original**.
No earlier AGENTS.md was available. Task-specific owner instructions take precedence.

## Sources and scope

- Read `docs/PROJECT_STATUS.md`, `docs/ROADMAP.md`, `docs/RELEASE_GATE_V0_2.md` and
  `docs/JSON_SCHEMA_AND_MIGRATION_POLICY.md` before changing relevant behavior.
- The release gate defines reduced v0.2 scope. `docs/V0_2_APPROVED_PLAN.md` preserves broader
  historical requirements; early MVP/NEXT_STEPS documents are not proof of current functionality.
- Keep **NOT Ready for Release** until the owner accepts the outstanding release gates.
- Use the owner-specified development branch. Verify its expected HEAD and working tree before
  edits; report unexpected changes. Do not modify/merge main or push without explicit authorization.

## Implementation and data safety

- Inspect existing implementation/tests before adding features. Reuse Compose components,
  `AppTheme`, paragraph/input direction helpers, navigation guards and persistence APIs.
- Preserve project/task IDs, ordering, existing statuses, freeform text and unknown legacy JSON
  fields. Keep the unversioned JSON array; no schema changes or migrations without separate approval.
- Preserve atomic/stale-write safeguards, pending input, confirmed reload/discard and draft recovery.
  A stored VERIFIED status is not independent evidence of verification.
- Backup preview is strictly read-only. Do not expose dormant restore/import/merge engines.
- Do not change signing, Application ID, release configuration, dependencies or approved visual
  identity as incidental cleanup. Do not delete user data or work around signing by uninstalling.

## Verification and reporting

- Add focused regressions for changed behavior using existing JUnit/Compose infrastructure and
  isolated synthetic files. Diagnose failures; retain assertions and distinguish harness failures.
- Batch final Debug assembly, host tests, Android test APK assembly and Lint. The current workstation
  uses JDK 17, Android SDK `/home/karim/Android/Sdk` and Gradle 8.13; locate installed tools rather
  than assuming a wrapper or downloading dependencies. Typical tasks:
  `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug`.
- Run only authorized Android tests against an explicitly verified disposable emulator serial.
  Check package/signature compatibility before safe in-place updates; preserve existing data.
  No physical-device work, emulator reset/wipe, heavy PD/DU acceptance or actual process-death
  execution is implied by a normal development batch.
- Review the full diff, record actual results and deferred checks in project status, and make
  clear local commits within the approved scope. Never describe compiled-only tests as passed.

These instructions summarize the current owner-approved workflow and repository safety policy;
they do not invent previous instructions or grant release, device or scope-expansion approval.
