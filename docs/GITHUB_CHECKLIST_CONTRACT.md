# GitHub checklist — integration contract

## Sources and priority
1. Repository-root `PROJECT_CHECKLIST.md` at the default branch's commit SHA.
2. If that file is absent, Markdown checkboxes in the pinned README.
3. Recent open and closed GitHub Issues (up to 10 each), excluding pull requests.

The app uses the existing authenticated GitHub App session. If the repository is not granted to that installation, access is blocked; no bypass is attempted.

## Mobile behavior
- The **Tasks** tab automatically loads suggestions for a GitHub-linked project when opened.
- The user can refresh and preview new tasks, then choose **Add suggested tasks**.
- Existing local tasks are preserved; matching titles are not re-added. New task IDs include source keys.
- Remote checked boxes and closed issues map to `IMPLEMENTED_UNVERIFIED`, never `VERIFIED`.
- Manual task editing and status changes remain local and persistent using existing project storage.
- Refresh does not overwrite local task status or title, and the app never writes to GitHub.
- Missing checklist/README or empty Issues do not imply 0% verified completion.

## Operational notes
Coding agents can maintain `PROJECT_CHECKLIST.md` in the repository as milestones are completed, with evidence notes. The app reads GitHub when online; it is not a code-analysis engine and cannot infer an authoritative project plan from commits alone.

## Limitations
This is one-way GitHub-to-phone suggestion import, **not** bidirectional synchronization. Rewriting the title of an existing remote task can create a new suggestion, which the user must review. The preview shows at most 20 titles, while the apply action imports all proposed additions (up to 300 checklist entries plus recent Issues). The default-branch GitHub App access restriction remains external to this feature.

Release acceptance requires passing CI and a live import/restart/refresh test with a repository authorized for the GitHub App. The feature must not be described as release-ready before those checks.
