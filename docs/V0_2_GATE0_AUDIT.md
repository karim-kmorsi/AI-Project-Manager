# v0.2 Gate 0 — storage audit and first regression test

Status: **Audit completed; test authored but NOT yet executed on an emulator.**

## Observed v0.1 code facts
- `MainActivity` instantiates `ProjectStorage(File(filesDir, "projects.json"))`.
- Top-level storage format is a JSON array (no version field).
- Each project is stored with `id`, `name`, `description`, `blueprint`, `roadmap` and `tasks`.
- Each task has `id`, `title`, `status`; accepted status strings include `NOT_STARTED`, `IN_PROGRESS`, `IMPLEMENTED_UNVERIFIED`, and any legacy value such as `VERIFIED`.
- `save` writes a temporary JSON file and renames it to `projects.json`. Its fallback is a direct rewrite, which is **not fully atomic**.
- `load` catches all errors and returns an empty list. This **can mask damaged data**; subsequent save may overwrite the original. It is a priority to fix safely.
- There is no backup/export, schema version, signing persistence policy, or automatic migration implementation yet.

## Added now
- Android instrumentation regression test for reading original v0.1 JSON, writing it back and reopening it without losing fields/statuses.
- The test uses a disposable temporary file; it never touches personal user data.
- The test is only *authored*, not demonstrated as passing. It must execute on an Android emulator or connected device and pass before any migration.

## Gate 0 remaining work
1. Implement versioned, non-destructive storage loading and explicit corruption reporting.
2. Make writes recoverable and reliably atomic; preserve an original backup.
3. Validate upgrade with copied user-equivalent data and round-trip tests.
4. Verify stable signing approach before in-place APK update; never advise uninstalling v0.1.
5. Execute instrumentation tests on an emulator/device and inspect actual results.

## Compatibility warning
Do not change package ID, file name, fields, or task statuses without a proven migration. `Room` is *not* a prerequisite for v0.2.
