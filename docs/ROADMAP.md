# AI Project Manager — reconstructed current roadmap

Created 2026-11-06 (Asia/Kathmandu), with owner authorization.
This is a newly authored current roadmap, **not a recovered historical original**.
No previous `docs/ROADMAP.md` was available. Application status: **NOT Ready for Release**.

## Authority and continuing baseline

Continue development on `codex/baseline-reconciliation-v0-2`; this reconstruction starts at
`c70a72f2593a5e9b3394bedd81f121de06ff4b14`. No branch merge/deletion is required.
[Release gate](RELEASE_GATE_V0_2.md) defines reduced v0.2 scope and outstanding acceptance.
[Project status](PROJECT_STATUS.md) records implementation/evidence by commit.
[Broader approved plan](V0_2_APPROVED_PLAN.md) retains historical requirements and phase order;
specific later development authorizations do not close Gate 0 or waive release criteria.
[JSON compatibility policy](JSON_SCHEMA_AND_MIGRATION_POLICY.md) remains binding.

## Implemented locally

- Project creation/list/selection, existing name/description editing and project-list search.
- Blueprint/Roadmap editors, Save/autosave/recovery and dirty-navigation protections.
- Task creation/title editing, existing manual statuses, title search and status filters.
- Offline next-step guidance, contextual/setup tips, status counts and pending-task details.
- Existing-format JSON export and validated read-only backup preview; atomic/legacy-safe writes.
- Explicit platform backup/transfer exclusions, immutable saved editor baselines, approved icon.
- Settings/storage disclosure; private SYSTEM/LIGHT/DARK theme preference and approved palettes.
- Content-aware RTL/LTR paragraphs/inputs and display-only bidi isolation for numeric ratios.

Implementation is not blanket acceptance. In-app language selection/full English translation
remain missing. Current feature evidence and its limits are in project status.

## Owner-approved stabilization batch — next in dependency order

1. **Documentation recovery:** reconstruct this roadmap and AGENTS.md, reconcile current status,
   README and stale release-gate statements while preserving historical requirements/evidence.
2. **Long preview accessibility:** make all displayed entries/disclaimers reachable at narrow widths
   and large fonts, keep Close accessible, retain the 30-entry cap and strictly read-only validation.
3. **Accessible project creation:** name the symbolic + action meaningfully through Compose
   semantics, retaining its appearance, placement and behavior.

Steps 2–3 are pending implementation/verification at this documentation checkpoint. Add focused
regressions, review the diff and record actual final build/test results. No heavy emulator,
physical-device, PD/DU or actual process-death execution is part of this batch.

## Outstanding release acceptance

- Real process death, large saved-state payloads, persistence/UI scale and crash-window assessment.
- Broad IME/cursor/selection, large-font, TalkBack, theme/system-bar and OEM/launcher acceptance.
- Remaining system-provider/lifecycle coverage; retain completed DU evidence without rerunning it
  merely because a historical gap list predates it.
- Real backup transport/device-transfer and manufacturer-specific exclusion behavior.
- Stable release signing/version/update decisions and non-destructive data-continuity verification.
- Separately authorized current-candidate physical-device workflows and final review/CI evidence.

These are open gates, not assertions that implemented features are absent.

## Deferred development — requires separate scope approval

- Arabic/English/follow-device language selection, persisted independently of project JSON, and
  full UI/accessibility translation (broader plan Phase 2). Theme implementation has already landed;
  its original implementation deferral is superseded, while acceptance remains open.
- Real restore/import/merge UI: dormant engines need further crash/concurrency safety work.
- Archive/unarchive, richer task metadata, structured milestones, decisions and event history.
- Broader cross-project/archived/decision search and evidence-based verification workflows.
- GitHub linkage/import/synchronization, AI/remote execution or other post-v0.2 capabilities.

Do not infer a Room conversion, new JSON schema, feature authorization or release approval from
historical plans. Select the next batch with the owner after reviewing current evidence.
