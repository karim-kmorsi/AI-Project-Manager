# Local JSON storage format and safe migration policy

Status: **documented policy only; not a schema migration or release approval**.
Applies to `ProjectStorage` and `projects.json` in AI Project Manager.

## Current on-disk format: legacy v0.1 (implicit schema 1)

The root is a JSON **array**, not an object or envelope. There is **no stored schemaVersion field**. Each project contains `id` and `name` as required strings, with optional `description`, `blueprint`, `roadmap` (default empty strings), and `tasks` (default empty array). Each task requires `id` and `title`; `status` defaults to `NOT_STARTED` if omitted.

Preserve IDs, task order, all status strings (especially `VERIFIED`), and user-written Arabic/English text verbatim. Do not infer verification or rewrite original Blueprint/Roadmap content.

Known limitation: the current reader materializes only fields represented by `ProjectItem` and `TaskItem`. Extra unknown JSON keys are currently **not preserved** by a load/save round trip. Do not claim arbitrary newer/third-party JSON fields round-trip safely.

## Versioning decision for a future change

- Treat unversioned root arrays as **legacy schema 1**. This is a *logical label* only; it does not change v0.1 files.
- A *future* schema 2 may use a root object with `schemaVersion: 2` and `projects: [...]`, **only** after implementation, fixture-based tests, backup/restore, and explicit review.
- Until such work is approved and tested, keep writing the legacy array; do not introduce `schemaVersion` into existing files or silently upgrade users.
- Reject unsupported or ambiguous versions rather than guessing, dropping fields, or replacing the original file.
- Any new structured project/task fields need intentional preservation rules and migration tests before rollout; consider lossless extension handling.

## Safe migration and restore transaction (future work, not yet implemented)

1. Read the existing file with Android `AtomicFile` and validate the *entire* document before any write. Distinguish missing file from corrupt/unsupported file.
2. Make a separate recoverable snapshot of the exact original bytes, verify it can be read back, and never reuse the live file as the sole backup.
3. Parse the source format into a candidate representation, retaining required/optional fields, identifiers, statuses, ordering, and original freeform text. Reject duplicate identifiers and invalid structures according to a documented validation policy; do not silently normalize user content.
4. Serialize the candidate to a separate temporary destination, then parse it again and compare required semantic fields and record counts before committing.
5. Atomically commit only after validation; preserve the prior snapshot until restore safety is confirmed. On any exception, leave original data recoverable and show an actionable error; never silently substitute an empty project list.
6. On application restart, re-read and verify the committed data. Add regression cases for failed/corrupt writes, missing optional fields, stale drafts, and unsupported future format.

## Existing coverage and remaining gate

- CI build #77 ran 11 Android instrumentation tests successfully, including representative v0.1 load/save/reload tests.
- Current `ProjectStorage` uses `AtomicFile` and blocks overwriting files it cannot read, but **full schema migration, version rejection, recovery snapshots, and backup export/import are not implemented**.
- Tests use isolated synthetic files, **not a copy of data from the user's installed phone**.
- APK signing/fingerprint compatibility and in-place update remain separate pending Gate 0 checks. Do not uninstall, clear app storage or deploy a differently signed APK to the existing installation.

Gate 0 stays **open** until the remaining conditions are tested and reviewed.
