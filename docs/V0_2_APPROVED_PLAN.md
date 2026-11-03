# AI Project Manager — v0.2 approved scope and execution order

Status: **Historical broader approved requirements**. Approved 2026-11-02; retained below without removing the original requirements.
Target: Android only; Kotlin + Jetpack Compose. GitHub PR review before merge.

## Current release-scope reconciliation — 2026-11-03

The original phases and acceptance criteria below are a requirements record, not a claim that all features are implemented or included in the reduced v0.2 release. [RELEASE_GATE_V0_2.md](RELEASE_GATE_V0_2.md) defines the current release scope, deferred requirements and outstanding verification; [V0_2_LOCAL_REGRESSION_AUDIT.md](V0_2_LOCAL_REGRESSION_AUDIT.md) records automated coverage and its limits.

The reduced release covers project creation/listing/selection, Blueprint/Roadmap editing and saving, simple task creation/status/progress, existing-format JSON export, and read-only backup preview. Actual restore/merge, project metadata editing/expansion, archival, search/history, multilingual/theme settings, visual redesign and GitHub integration remain deferred. This deferral does not mark the broader requirements complete or waive data preservation, signing, device validation or PR review gates.

## Hard constraints
1. Do **not** change, force-push or merge into `main` without explicit review and approval.
2. Preserve `applicationId = com.karim.aiprojectmanager` and existing signed install's data. Note: GitHub debug signing key may change between builds; installation of an APK signed with a different key may fail. **Never recommend uninstalling the current app** to work around signature mismatches without an independently verified backup and explicit consent. Establish a stable signing/update approach first.
3. Test using a copy of v0.1 project data. No schema change until old JSON is backed up, migrated and round-trip tested. Do not assume Room conversion is required in v0.2.
4. No destructive writes during import. User must preview a validated backup and explicitly confirm replacement or merge. Keep a recoverable prior snapshot.
5. No claim of VERIFIED without independent evidence. Preserve all old task statuses, including VERIFIED.
6. Small incremental feature branches / PRs and green Android CI; no paid service, publishing, secrets or expanded device permissions without approval.

## Priority and dependencies

### Gate 0 — Baseline + data safety (blocks all remaining phases)
- Characterize v0.1 JSON shape and its error behavior; collect representative sample with projects, blueprints, roadmaps, tasks.
- Add tests that read legacy JSON and write it back without losing any fields.
- Define schema versioning, migration and safe writes (atomic operations, corruption handling).
- Document APK signature compatibility before suggesting an update to the existing installation.
**Done when:** seeded v0.1 data survive simulated v0.2 upgrade and restart; no delete/uninstall required.

### Phase 1 — Reliable editing + UX (depends on Gate 0)
- Keyboard-aware editor with always accessible Save and proper scrolling, compact layout and focus behavior.
- Autosave drafts on edits and navigation; debounce/explicit transaction and crash resilience; visually distinguish saved vs pending state.
- Warn before leaving with unsaved changes if persistence failed or is pending; avoid redundant warnings after confirmed autosave.
- Consistent navigation and Arabic RTL plus English LTR typography in mixed-language project content.
**Done when:** typing, changing tabs, rotating, backgrounding and reopening never silently lose edits; keyboard never blocks save.

### Phase 2 — Unified settings + localization/themes (depends on Phase 1 UI structure)
- Arabic / English / follow-device language, switch within app, persist setting.
- Light / Dark / follow-system theme, persist setting; proper contrast across every screen.
- Storage settings with a clear location/backup disclosure.
- Translate all labels, statuses, messages and accessibility strings (user-authored content remains unchanged).
**Done when:** language, direction, theme and choices survive force-stop/relaunch; keyboard and editors work in both languages and themes.

### Phase 3 — Backup/export/import/restore (depends on Gate 0 schema safety)
- JSON export through Android Storage Access Framework, with file version and clear metadata (no secrets).
- Validate structure and size; preview changes before restore; handle duplicate IDs and unsupported versions safely.
- Offer explicit merge or replace only after confirmation, and retain recoverable local copy before replacing.
- Import old v0.1-compatible backups with tests.
**Done when:** export/import preserves project, task, blueprint, roadmap values and IDs; corrupted file safely rejected; cancellation makes no changes.

### Phase 4 — Project management and task structure (depends on prior data model/migration tests)
- Edit project title/description; archive/unarchive (not delete); archived projects remain in storage/export.
- Tasks: editable description, priority, status, project and roadmap milestone link.
- Define structured roadmap milestone IDs without overwriting users' existing freeform roadmap text.
- Enforce transition policy: implementation alone is not VERIFIED; VERIFIED requires evidence workflow in a future version.
**Done when:** old tasks unchanged after upgrade; archive and milestone associations survive restart and backup round-trip.

### Phase 5 — Search, filters and audit log (depends on Phase 4 structured fields)
- Find projects and tasks with sensible filtering across active/archived and task states; search decisions when a structured decision record exists.
- Define structured decision records or explicitly mark decision search as pending until created; do not pretend freeform text has a decision database.
- Append event log recording timestamp, entity, old/new state summary and origin for meaningful changes; never assert actions were verified unless evidence exists.
- Avoid storing tokens/secrets in event log; provide ways to review and filter history.
**Done when:** relevant projects, tasks and decisions are found, with a traceable log of changes that persists, exports and restores.

## Scope exclusions for v0.2
No GitHub Inspector, remote code execution, AI agents, push notification system or iOS client; pursue in separately reviewed versions.

## Release acceptance criteria
- Green CI for Debug APK, plus basic automated migration and serialization tests.
- Human device check: keyboard-safe Save, reboot persistence, Arabic/English language, RTL/LTR, dark/light/system themes, export/import, archived projects and old-data preservation.
- Stable APK signing for in-place update; no instructions to uninstall.
- Review PR first, keep `main` unchanged unless explicitly approved.

## Work sequence
Gate 0 -> Phase 1 -> Phase 2 -> Phase 3 -> Phase 4 -> Phase 5 -> Release validation.
Within Phase 4/5, dependencies may be adjusted after data tests; never bypass Gate 0.

## Approved future capability — Import from GitHub (post-v0.2 foundation)
Approved for the roadmap, **not implemented or included in the v0.2 release gate**.
- A user selects a repository to import after explicitly linking GitHub with least-privilege, read-only access.
- Read only supported source material: README, project documentation (Blueprint/Roadmap when present), issues, PRs, commits and CI results; show provenance and distinguish extracted facts from AI suggestions and absent information.
- Preview the proposed project, milestones and tasks; require confirmation before adding or overwriting anything locally.
- Preserve local edits on refresh. Offer a diff/reconciliation step rather than automatic replacement.
- Do not write to GitHub, initiate code actions, or request broader permissions without a separate opt-in.
- Keep GitHub as source of truth for repository/CI facts; keep independent user notes and decisions in local app storage.
- Implement only after reliable editing, data backup/restore and safe updates are established.
