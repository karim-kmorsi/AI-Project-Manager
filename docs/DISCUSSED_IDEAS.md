# AI Project Manager — Discussed Ideas and Context Register

Prepared: 2026-11-07
Status: Discussion record; not blanket implementation approval
Repository: karim-kmorsi/AI-Project-Manager
Branch: codex/baseline-reconciliation-v0-2
Release: NOT Ready for Release

## Purpose
Preserve owner discussions that may not be fully captured by the implementation roadmap. A new AI agent must distinguish agreed principles, proposals, and verified code. Read AGENTS.md, PROJECT_STATUS.md, ROADMAP.md, RELEASE_GATE_V0_2.md, CHAT_HANDOVER.md, and DECISIONS.md before acting.

## Product direction — agreed principles
- AI Project Manager is a personal Android project-management and operational-memory application, not merely a generic task list.
- It should help recover goals, decisions, progress, tasks, issues, experiments, file locations, evidence, and the next actionable step after an interruption.
- A status such as VERIFIED is not independent proof of execution or verification; record actual results and supporting evidence.
- Prefer core functionality, stable storage, safe recovery and coherent navigation over cosmetic expansion.
- Maintain approved Kotlin/Jetpack Compose architecture, local JSON compatibility and dark visual identity.
- Repository documentation and Git history should allow an unfamiliar agent to understand the project without relying on one chat's memory.

## Multi-agent workflow — discussed direction, not unrestricted authorization
- The owner explored a human-team-style workflow with a primary AI project manager and specialized AI agents or separate conversations.
- The primary manager defines scope, dependencies, acceptance criteria and integration, and reviews implementation and evidence.
- GitHub is the common technical record. Parallel work may be considered only with explicit branch/file ownership and controlled integration; it is not approval for concurrent edits to the same branch.
- Codex Desktop was an execution tool, not a permanent requirement. Another tool may be used after reviewing its access and safety. Codex was unavailable during the November 7 handover.
- Use bounded batches of related roadmap items, targeted tests during development, and a final full build/test pass rather than repeated heavy acceptance runs.
- Preserve owner approval gates for schema, signing, device, main, destructive changes, and push.

## Proposed product capabilities — not automatically approved for v0.2
- Contextual Assistant & Tips: useful usage hints, contextual next step, activity log and project-specific guidance.
- Operational project memory: structured decisions, issues/risks, experiments, evidence gallery, files map and a next-step engine.
- More expressive task metadata, milestones, archive/unarchive and cross-project search.
- Future GitHub linkage and AI execution assistance, subject to security, privacy and authorization review.
- Backup import/restore/merge and schema migrations remain restricted and are not implied by discussion.
- A separate KARIM OS Manager concept informed some of these ideas. Do not confuse it with the current AI Project Manager implementation or silently transfer its entire specification.

## Visual identity and UX discussions
- Adopted app name: AI Project Manager.
- Approved direction: navy circle, white checkmark, dark charcoal/navy UI with blue actions and light text.
- Later discussions explored checkmark proportions, circle boundary, subtle logo refinement and AI-star elements. Image mockups are references, not proof that corresponding assets or app code were changed.
- Avoid radical redesign; verify the latest owner-approved reference before any future logo adjustment.

## Release and evidence discipline
- Release remains NOT Ready for Release.
- Distinguish proposed, coded, compiled, host-tested, Android-runtime-tested, and release-accepted.
- Emulator/ADB instability interrupted specific Android checks; do not claim they passed.
- Do not repeat previously passed tests without a reason, and do not run heavy physical-device/process-death acceptance without authorization.
- Preserve real user data, IDs, ordering, unknown legacy JSON fields and safe persistence paths.

## Handover and environment transition — November 7, 2026
- Owner plans to reinstall Ubuntu and reinstall programs instead of preserving installed binaries.
- A personal-files tar archive was created on ExternalUSB; 98,691 included personal/project files passed SHA-256 comparison, while live application databases/caches and deliberately excluded directories were not fully validated.
- GitHub development branch was successfully pushed and verified clean at 86f2a4082711e3b3f647371762509a31fff4586c. Earlier handover references to unpublished commits are historical snapshots, not current state.
- Do not treat an external backup as exhaustive without checking exclusions and signing/credential data.

## Agent intake checklist
1. Verify remote branch HEAD and working tree.
2. Read authoritative docs and this discussion record.
3. Inspect actual source and tests before claiming implementation status.
4. Separate approved decisions from proposals.
5. Report contradictions, unknowns and next safe roadmap work before changing files.
6. Do not change main, JSON schema, signing, Application ID, physical devices, or user data without specific approval.
