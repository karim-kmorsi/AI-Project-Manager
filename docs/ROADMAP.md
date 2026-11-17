# AI Project Manager — reconstructed current roadmap

Created 2026-11-06 (Asia/Kathmandu), with owner authorization.
This is a newly authored current roadmap, **not a recovered historical original**.
No previous `docs/ROADMAP.md` was available. Application status: **local candidate complete with limits; production approval open**.

## Authority and continuing baseline

Continue on owner-authorized `main`, with acceptance source baseline `d1fba747`.
The [v0.3.0 candidate report](releases/v0.3.0/LOCAL_CANDIDATE.md) is the current
packaging/acceptance record. Earlier branch and v0.2 checkpoints are historical.
The [JSON compatibility policy](JSON_SCHEMA_AND_MIGRATION_POLICY.md) remains in
force. Local packaging does not approve production signing or a phone update.

## Implemented locally

- Project creation/list/selection, existing name/description editing and project-list search.
- Blueprint/Roadmap editors, Save/autosave/recovery and dirty-navigation protections.
- Task creation/title editing, existing manual statuses, title search and status filters.
- Offline next-step guidance, contextual/setup tips, status counts and pending-task details.
- Existing-format JSON export, validated preview and explicitly confirmed replace-all restore; atomic/legacy-safe writes.
- Explicit platform backup/transfer exclusions, immutable saved editor baselines, approved icon.
- Settings/storage disclosure, Light/Dark themes, Arabic/English selection and automatic language fallback.
- Content-aware RTL/LTR paragraphs/inputs and display-only bidi isolation for numeric ratios.

Implementation is not blanket acceptance. Language selection, restore and read-only GitHub
discovery/import are implemented; current evidence and limits are in the candidate report.

## Historical stabilization batch — completed checkpoint

1. **Documentation recovery:** reconstruct this roadmap and AGENTS.md, reconcile current status,
   README and stale release-gate statements while preserving historical requirements/evidence.
2. **Long preview accessibility:** make all displayed entries/disclaimers reachable at narrow widths
   and large fonts, keep Close accessible, retain the 30-entry cap and strictly read-only validation.
3. **Accessible project creation:** name the symbolic + action meaningfully through Compose
   semantics, retaining its appearance, placement and behavior.

All three steps are now implemented locally: documentation `903809d`, preview `85b2c2e`, creation
semantics `b044800`. Final Debug/test APK assembly and Lint passed; 72 host tests and seven focused
Android regressions passed. The preview regressions use composition-injected narrow density and
2x fonts in both directions; accessible-name coverage is automated semantics, not manual TalkBack
acceptance. See project status for exact evidence and limits. No heavy emulator, physical-device,
PD/DU or actual process-death execution was performed. **NOT Ready for Release**.

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

- Broader locale/accessibility/device acceptance beyond the implemented language/theme controls.
- Merge and other backup formats; the legacy-array replace-all restore is already implemented.
- Archive/unarchive, richer task metadata, structured milestones, decisions and event history.
- Broader cross-project/archived/decision search and evidence-based verification workflows.
- Bidirectional GitHub synchronization, AI/remote execution and other separately approved capabilities.

Do not infer a Room conversion, new JSON schema, feature authorization or release approval from
historical plans. Select the next batch with the owner after reviewing current evidence.
