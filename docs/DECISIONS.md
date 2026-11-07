# AI Project Manager — Decision Register

**Prepared:** 2026-11-07
**Status:** DRAFT — awaiting owner review
**Purpose:** Preserve approved decisions, rationale and restrictions.

This register distinguishes binding decisions, operational
authorizations, implemented work and proposals awaiting approval.

## A. Approved project decisions

### D-001 — Application identity

**Decision:** Use the name AI Project Manager.

**Status:** Approved.

The approved visual direction uses a dark interface and a
navy-circle/white-checkmark identity.

Check the owner's most recently approved logo reference before
implementing later refinements.

A mockup is not proof of implementation.

### D-002 — Android-first implementation

**Decision:** Continue with Kotlin and Jetpack Compose.

**Status:** Approved existing architecture.

Reuse current Compose components, theme, navigation and persistence.
Do not replace the framework without separate approval.

### D-003 — Git development branch

**Decision:** Continue on `codex/baseline-reconciliation-v0-2`.

**Status:** Approved.

Do not modify or merge `main`.
Do not push without separate approval.

### D-004 — Repository as technical record

**Decision:** Use repository documents and Git history as the primary
technical evidence for implementation status.

**Status:** Existing practice reinforced by the owner.

ChatGPT memory is supporting context, not a substitute for repository
verification.

Permanent handover publication is still pending approval.

### D-005 — JSON compatibility

**Decision:** Preserve the existing unversioned JSON array format
and supported legacy data.

**Status:** Binding.

No schema changes or migrations without authorization.

Preserve project/task IDs, ordering, statuses, user text and supported
unknown fields. Do not silently discard data.

### D-006 — User-data protection

**Decision:** Preserve user data and safe persistence behavior.

**Status:** Binding.

No destructive installation workaround or user-data deletion.

Backup preview remains read-only.

Dormant import/restore/merge must not be activated without approval.

### D-007 — Evidence-based completion

**Decision:** Separate implementation, execution, verification and
release acceptance.

**Status:** Approved.

A stored VERIFIED task status is not independent verification evidence.

Compiled tests are not passed tests.
A successful build is not release approval.

### D-008 — Approved UI and theme

**Decision:** Preserve the approved visual direction and styling.

**Status:** Approved.

Reuse existing components and theme definitions.
Avoid radical redesign during reliability work.

### D-009 — Development workflow

**Decision:** ChatGPT plans and reviews; Codex Desktop executes
approved local development batches.

**Status:** Approved.

Workflow: VERIFY → IMPLEMENT → TEST → DIAGNOSE → FIX → RETEST → DOCUMENT.

Prioritize core functionality and dependencies over cosmetic work.

### D-010 — Controlled repair authorization

**Decision:** Routine fixes within approved scope do not require
separate approval for every change.

**Status:** Approved with limits.

- Fix evidence-supported defects.
- Maximum two evidence-based repair attempts per defect.
- Do not weaken assertions to manufacture passing tests.
- No restricted schema, signing, device or release changes.
- Stop and report unresolved or out-of-scope blockers.

### D-011 — Android verification isolation

**Decision:** Use disposable emulator environments for authorized tests.

**Status:** Approved.

Use explicit serials, synthetic data and verified readiness.

Preserve physical devices and unrelated AVDs.

Stop and preserve evidence after unexpected reboot, kernel fault
or transport loss.

### D-012 — Release status

**Decision:** Keep the application NOT Ready for Release until the
release gate is completed and accepted.

**Status:** Binding.

Individual test passes do not close unrelated requirements.

## B. Current verification cycle

### D-013 — Compact saved-state baselines

**Decision:** Use compact conflict-baseline representation while
retaining legacy restoration compatibility.

**Status:** Implemented locally according to Codex reports.

**Evidence:** Commit `e3d9d72`, tests and measurements.

**Limitation:** Pending Android verification remains open.

### D-014 — Per-project task context

**Decision:** Preserve task query/filter/scroll context across
supported recreation and navigation.

**Status:** Implemented locally, partially verified.

Two focused Android tests passed in the reported run.
Accumulated multi-project context verification remains unfinished.

### D-015 — Test-readiness correction

**Decision:** Retain the Compose test-only readiness correction
pending runtime verification.

**Status:** Applied but not validated by a passing affected test.

The original test failure is not a proven production defect.

### D-016 — Emulator readiness

**Decision:** Require stable ADB and responsive Android services,
not solely `sys.boot_completed`.

**Status:** Successfully evaluated in a standalone check.

**Evidence:** 13/13 checks over 60.112 seconds.

This does not prove APK installation or instrumentation stability.

### D-017 — Remaining Android checks

**Decision:** Run the six unfinished Android checks individually
after the readiness gate.

**Status:** Approved direction, execution pending.

Do not repeat previously passing tests without a specific reason.
No physical-device or actual process-death acceptance in this scope.

## C. GitHub synchronization

### D-018 — Preserve local history

**Decision:** Preserve the eight existing local commits.

**Status:** Current intended approach.

The owner verified GitHub HEAD `94757c6` as an ancestor of local
HEAD `f5bee4f`.

Do not recreate these commits manually through GitHub.

### D-019 — Review before publication

**Decision:** Compare local/remote state and review changed files
before push.

**Status:** Approved; preliminary checks completed.

Commit ancestry, file inventory, whitespace validation and limited
token screening have been performed.

These checks are not a comprehensive security audit.

### D-020 — Permanent handover documents

**Proposal:** Add `CHAT_HANDOVER.md` and `DECISIONS.md` to the repository.

**Status:** Drafts provisionally accepted for local preparation;
final content review and commit authorization pending.

Publication requires separate approval.

## D. Deferred features and restricted decisions

The following are not automatically authorized:

- JSON schema changes and migrations.
- Real import, restore or merge.
- Full Arabic/English/follow-device language selection.
- Expanded task metadata, archive and structured milestones.
- Automatic GitHub integration in the Android application.
- AI-agent execution or remote code execution.
- Replacement of the existing architecture.
- Signing or Application ID changes.
- Physical-device installation or replacement.
- Merge into `main`.
- Release publication.

The broader historical plan in `docs/V0_2_APPROVED_PLAN.md`
does not override the current reduced release scope.

## E. Pending owner decisions

1. Approve or revise these two complete documentation drafts.
2. Approve the exact local documentation commit.
3. Separately authorize any GitHub push after final diff review.
4. Resume six unfinished Android checks when Codex is available.
5. Review release gates before any release-readiness decision.

## F. Maintenance rules

- Record decision, status and supporting evidence.
- Never silently convert a proposal into approval.
- Preserve historical decisions and identify superseding decisions.
- Keep implementation evidence in `PROJECT_STATUS.md`.
- Keep planned work in `ROADMAP.md`.
- Never store credentials or personal secrets.
- Reverify repository state before claiming implementation.

**Application status: NOT Ready for Release.**
