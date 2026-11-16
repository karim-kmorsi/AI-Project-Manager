# AI Project Manager — Physical Visual Acceptance

**Date:** 2026-11-12  
**Branch / starting HEAD:** `feature/github-snapshot-preview` / `dd1bc43`  
**Device:** REDMAGIC 10 Pro, `FY2433810B9D`  
**Decision: FAIL — CORRECTIONS REQUIRED**

## Setup and safety

Installed and launched the actual app on the owner-authorized phone using isolated package `com.karim.aiprojectmanager.vnext`. The original `com.karim.aiprojectmanager` package remained installed and untouched. The isolated package contains synthetic review data only. The app-private export preview used the synthetic file `Downloads/CodexVNextVisualReview20261112.json`. No system locale, signing configuration, stored project data, GitHub authentication logic, or original app data was changed. No commit or push was made.

The six approved image files in the supplied ZIP were inspected. All screenshots cited below are captures from the running phone app, not previews or mockups.

## Screens inspected

- Dashboard: English and Arabic.
- Create project dialog: English, opened and populated with synthetic values.
- Project Overview: English and Arabic; project edit dialog opened in English and dismissed without saving.
- Blueprint and Roadmap: Arabic; editor text input/save behavior was exercised with synthetic text.
- Tasks: English and Arabic; empty state, added task, edit dialog, and status action inspected. Status was changed only on the synthetic task.
- Settings and Advanced Settings: Arabic; navigation Back checked.
- GitHub settings and unauthenticated public repository probe: Arabic.
- Export picker/result, read-only backup preview, and Backlog preview: opened using synthetic data.

## Not inspected

- Authenticated GitHub installation/repository discovery and repository preview: the local build has no GitHub App Client ID, so Connect is disabled. No live authorization was attempted.
- Recovery/conflict/error screen: no existing failure was present and no corruption, process death, or destructive recovery was induced.
- Blueprint, Roadmap, Settings, and GitHub in English; not needed to establish the major current issues, but remain a localization coverage gap.
- Other device sizes, font scaling, and system light theme.

## Findings

### Major — Project content navigation differs materially from the approved reference

**Screen:** Project Overview / Blueprint / Roadmap / Tasks  
**Observed:** The app uses four large, equal-width Material tabs below a project summary card. The approved project references use a branded project header and a more compact section navigation/content hierarchy. At the tested 1216×2688 device size, the tabs consume a full row and push the first content panel downward; the editor/task content does not follow the reference’s tighter hierarchy. This is consistent across project sections.  
**Expected:** Match the approved project-page header and navigation proportions while retaining the existing working navigation.  
**Evidence:** [09 Project Overview Arabic](audit-evidence/ui-vnext-physical-2026-11-12/09-project-overview-ar-fixed.png), [12 Blueprint Arabic](audit-evidence/ui-vnext-physical-2026-11-12/12-blueprint-ar-content-saved.png), [13 Roadmap Arabic](audit-evidence/ui-vnext-physical-2026-11-12/13-roadmap-ar.png), [33 Tasks English](audit-evidence/ui-vnext-physical-2026-11-12/33-tasks-en-actual.png).  
**Correction:** Bring the project header and section navigation closer to the approved proportions and hierarchy; preserve all current tab actions and scroll behavior.

### Major — Dashboard header branding and composition do not match the reference

**Screen:** Dashboard  
**Observed:** The running screen shows a small circular launcher mark next to the product title and a compact header/search layout. The approved dashboard reference gives the original approved logo a much larger, more prominent role and uses different spacing/hierarchy. The mark itself is the correct existing identity; its on-screen scale and placement differ.  
**Expected:** Use the approved logo treatment and dashboard composition from the supplied reference.  
**Evidence:** [02 Dashboard English after heading fix](audit-evidence/ui-vnext-physical-2026-11-12/02-dashboard-en-heading-fix.png), [28 Dashboard English LTR](audit-evidence/ui-vnext-physical-2026-11-12/28-dashboard-en-ltr.png).  
**Correction:** Adjust logo scale/placement and header spacing to the approved reference without replacing or redrawing the logo.

### Major — GitHub and recovery acceptance evidence is incomplete

**Screens:** Authenticated GitHub discovery/repository preview; recovery/conflict/error states.  
**Observed:** GitHub Connect was disabled because the isolated local build did not have a Client ID. Recovery/conflict/error states were not safely accessible.  
**Expected:** Actual running-app inspection of these screens and their visible actions.  
**Evidence:** [26 GitHub public probe](audit-evidence/ui-vnext-physical-2026-11-12/26-github-public-probe.png), [19 Settings](audit-evidence/ui-vnext-physical-2026-11-12/19-settings-ar.png).  
**Correction:** Repeat these checks with a separately configured test build and non-destructive synthetic recovery fixture. This is an evidence gap, not a claim that those screens failed.

### Minor — Localization and localization coverage

**Screen:** Project Overview, Tasks, dialogs, editor chrome.  
**Observed:** English Overview and Tasks labels were initially mixed Arabic/English and task summaries were hardcoded. These were corrected during this authorized automatic-fix review. Latest English screenshots show English Overview, task status/summary, and project edit labels; Arabic overview/task views remain readable RTL. Several other screens were only inspected in Arabic, so English LTR coverage is incomplete.  
**Expected:** Consistent selected-locale labels and complete LTR/RTL acceptance across the interface.  
**Evidence:** [36 Overview English final](audit-evidence/ui-vnext-physical-2026-11-12/36-overview-en-final.png), [34 Project edit English actual](audit-evidence/ui-vnext-physical-2026-11-12/34-project-edit-en-actual.png), [15 Tasks Arabic empty-state fix](audit-evidence/ui-vnext-physical-2026-11-12/15-tasks-ar-empty-fix.png), [33 Tasks English actual](audit-evidence/ui-vnext-physical-2026-11-12/33-tasks-en-actual.png).  
**Correction:** Complete English and Arabic review for remaining screens and localize any remaining hardcoded visible strings.

## Corrections applied during this authorized review

- Fixed low-contrast dark-theme section/project headings and Tasks empty-state text.
- Fixed progress and editor count text wrapping vertically by allowing paragraph text to opt out of full-row width.
- Added English/Arabic resources for project navigation, Overview, project edit labels, task labels, and task status summaries.
- Confirmed export completion, read-only backup preview, public repository probe, project/task navigation, and synthetic task status action.

Modified implementation files include `app/build.gradle.kts` (isolated test build type), `MainActivity.kt`, `ParagraphText.kt`, `ProjectDesignSystem.kt`, `ProjectOverviewScreen.kt`, `VNextPresentation.kt`, and new/updated `values` and `values-ar` resources. Report/evidence additions are listed in the repository working tree. The project has uncommitted work; no commits or pushes were made.

## Build and evidence

- `:app:assembleVnext`: **passed** after final source edits; APK package verified as `com.karim.aiprojectmanager.vnext`.
- `git diff --check`: **passed**.
- JVM tests and Android instrumentation tests: **not run**, per request.
- Original package presence was confirmed before and after the isolated install.
- Screenshot evidence: `docs/audit-evidence/ui-vnext-physical-2026-11-12/`.

The project remains **NOT Ready for Release**. Do not treat this report as owner acceptance. Corrections listed above require owner review before another acceptance pass.
