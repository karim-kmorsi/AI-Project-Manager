# GitHub import and synchronization safety

Phase 2D and 2E provide read-only previews. Persistent import and synchronization
application require owner approval. This proposal uses the existing unversioned
project array and the existing atomic writer.

## Proposed import mapping

| GitHub input | Proposed local field | Missing or invalid input |
| --- | --- | --- |
| Numeric repository ID | `id` as `github:<id>` | Reject a missing, nonpositive, or invalid ID. |
| Validated `owner/repository` | `name` | Reject an invalid name or one that cannot form a canonical GitHub URL. |
| Description and canonical GitHub URL | `description`, with the URL on its own line | Use the URL alone for a blank description. |
| README, branch, tree, and commit metadata | Display in the remote preview | Keep them out of the project record. |
| Local planning fields | Empty `blueprint`, `roadmap`, and `tasks` for a new project | Keep existing values for every existing project. |

A matching stable project ID blocks creation. A case-insensitive full-name
match or an exact canonical URL on a description line flags a possible
duplicate for review. A name or URL match does not establish a stable link.
A repository transfer or rename keeps the same numeric repository ID.

Before adding a write action, show the owner the exact proposed record and
any duplicate candidates. Use an explicit confirm step for one new project.
If storage cannot be read, a duplicate is found, or the project list changes
while confirmation is open, cancel the write and require a refreshed preview.
Keep existing records, order, task status values, unknown JSON fields, and
drafts. Append a new record only after successful validation.

For recovery, capture an exact pre-import copy of the unversioned array and
use the existing stale-write guard and atomic writer. On write failure, report
the error and verify the original bytes are still readable. If the owner
undoes a successful import, remove only the inserted record after checking
that it has not changed. A later edit or ambiguous state requires owner review.
A whole-file backup restore remains a separate operation.

## Proposed synchronization checks

The preview compares a repository's metadata, default branch, and commit SHA
with an in-memory baseline. It uses the stable project ID to associate local
data. It reports a conflict for concurrent changes, duplicate candidates, or
a changed association. A changed remote head marks the old snapshot stale.
A missing repository, denied access, expired session, or interrupted request
produces an error before a comparison result can replace the current state.

Any future apply operation should re-read the local project under the existing
stale-write guard and re-resolve the remote repository ID and branch head
immediately before confirmation. If either differs from the reviewed
baseline, invalidate the proposed change and open a new comparison. Keep the
immutable commit SHA for content reads. Establish conflict resolution and an
owner-approved rollback policy before implementing apply. Keep automatic
synchronization disabled.
