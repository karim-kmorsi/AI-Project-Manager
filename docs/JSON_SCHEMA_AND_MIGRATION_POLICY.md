# Local JSON storage format and safe migration policy

Status: **documented policy only; not a schema migration or release approval**.
Applies to `ProjectStorage` and `projects.json` in AI Project Manager.

## Current on-disk format: legacy v0.1 (implicit schema 1)

The root is a JSON **array**, not an object or envelope. There is **no stored schemaVersion field**. Each project contains `id` and `name` as required strings, with optional `description`, `blueprint`, `roadmap` (default empty strings), and `tasks` (default empty array). Each task requires `id` and `title`; `status` defaults to `NOT_STARTED` if omitted.

Preserve IDs, task order, all status strings (especially `VERIFIED`), and user-written Arabic/English text verbatim. Do not infer verification or rewrite original Blueprint/Roadmap content.

The reader materializes supported `ProjectItem`/`TaskItem` fields. As of the 2026-11-04 stabilization audit, saves merge those supported fields into the validated stored objects by project/task ID, preserving unknown keys on retained records. The legacy array schema is unchanged. Malformed known fields, non-string IDs, blank/duplicate project IDs and duplicate task IDs are rejected before writing. This preserves extensions; it does not authorize interpreting a future schema.

## Versioning decision for a future change

- Treat unversioned root arrays as **legacy schema 1**. This is a *logical label* only; it does not change v0.1 files.
- A *future* schema 2 may use a root object with `schemaVersion: 2` and `projects: [...]`, **only** after implementation, fixture-based tests, backup/restore, and explicit review.
- Until such work is approved and tested, keep writing the legacy array; do not introduce `schemaVersion` into existing files or silently upgrade users.
- Reject unsupported or ambiguous versions rather than guessing, dropping fields, or replacing the original file.
- Any new structured project/task fields need intentional preservation rules and migration tests before rollout; consider lossless extension handling.

## Current restore transaction and future schema migration

The explicit restore flow accepts only the current legacy JSON array; it does
not migrate or introduce another schema.

1. The selected file is staged in app-private cache, limited to 8 MiB, and fully
   validated before preview. The final replace-all action requires a separate
   user confirmation.
2. Before commit, the candidate hash and the current storage fingerprint must
   still match the values captured for review. The app rejects stale or changed
   inputs and pending `AtomicFile` sidecars.
3. If current storage exists, an exact byte-for-byte recovery snapshot is
   atomically written and verified before the live write. This snapshot is raw
   bytes, so an explicitly confirmed restore can recover from a corrupt current
   file without interpreting or discarding those bytes. A first-run restore
   with no live file has no prior bytes to snapshot.
4. The validated candidate is committed through `AtomicFile` while holding the
   shared project-write mutex, then verified byte-for-byte and reloaded through
   normal storage validation. Unknown compatible JSON fields, IDs, ordering,
   statuses, and freeform text in the backup are preserved by restoring its
   original array bytes.
5. A failed pre-commit operation does not intentionally modify the live file.
   A post-commit verification failure is reported and the recovery snapshot is
   retained; the app does not silently overwrite the committed file again.
   Recovery snapshots remain in app-private storage and are not currently
   exposed for export through the UI.
6. This restore implementation does not add schema versioning, migration,
   merging, or support for a future root-object format. Such work still
   requires a separate approved design and compatibility tests.

## 2026-11-04 stabilization safeguards

Project reads and writes share the process-local write mutex. UI writes compare the current stored models with the displayed snapshot before committing, preventing a stale activity from silently overwriting a newer save. Atomic project/draft writes explicitly sync and verify publication before reporting success. An orphan initial `projects.json.new` blocks fresh writes and remains available for recovery; there is no automatic repair or migration.

Export preserves the validated source bytes in an app-private cache snapshot while the external picker is open. Saved activity state contains only its random token. Cache eviction can still make export fail explicitly; it cannot alter the live file. Preview and provider export run off the UI thread. End-to-end provider recreation remains a separate verification gap.

## Existing coverage and remaining gate

- CI build #77 ran 11 Android instrumentation tests successfully, including representative v0.1 load/save/reload tests.
- Current `ProjectStorage` uses `AtomicFile` and blocks ordinary writes to files it cannot read. A user-confirmed legacy-array restore can replace such a file only after retaining an exact recovery snapshot. Full schema migration, version rejection, merge/restore of other formats, and user-managed export of recovery snapshots are not implemented. Validated JSON export and restore preview are implemented; restore is replace-all only.
- VNext 3 adds isolated Android transaction regression sources, including successful replacement, missing storage, corrupt-original preservation, stale inputs, invalid candidates, and write failures. These tests were compiled but not executed for the current release cycle; restore safety remains an open release gate.
- Tests use isolated synthetic files, **not a copy of data from the user's installed phone**.
- APK signing/fingerprint compatibility and in-place update remain separate pending Gate 0 checks. Do not uninstall, clear app storage or deploy a differently signed APK to the existing installation.

Gate 0 stays **open** until the remaining conditions are tested and reviewed.
