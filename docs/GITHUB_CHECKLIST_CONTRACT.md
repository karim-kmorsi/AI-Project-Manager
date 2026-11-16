# GitHub checklist contract

A repository may provide `PROJECT_CHECKLIST.md` at its root. A coding agent updates this file as work progresses. GitHub is transport, not independent proof of correctness.

The parser accepts Markdown task list checkboxes, including nested and numbered lists, and ignores fenced code. Checked boxes map to `IMPLEMENTED_UNVERIFIED`, never `VERIFIED`. Missing files must not be interpreted as 0% progress.

This first slice adds a pure parser, safe candidate additions, and unit tests. It does not yet fetch the file from GitHub or display it in Android. Candidate additions never overwrite local tasks. UI preview, explicit apply, and refresh remain to implement.
