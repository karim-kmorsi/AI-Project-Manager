package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPreparationTest {
    @Test fun previewsTheMappingAndFlagsStableIdNameAndUrlDuplicates() {
        val stable = ProjectItem("github:24", "Older name", "Keep this", tasks = listOf(TaskItem("t", "Task")))
        val name = ProjectItem("local-1", " OWNER/REPO ", "Keep blueprint", blueprint = "Draft")
        val url = ProjectItem("local-2", "Custom title", "Notes\nhttps://github.com/owner/repo/")
        val unrelated = ProjectItem("local-3", "Other", "https://github.com/owner/repo-extra")
        val local = listOf(stable, name, url, unrelated)

        val preview = requireNotNull(previewGitHubImport(repository, local))

        assertEquals(ProjectItem("github:24", "owner/repo", "About\n\nhttps://github.com/owner/repo"), preview.project)
        assertEquals(listOf(stable, name, url), preview.candidates)
        assertEquals(listOf(stable, name, url, unrelated), local)
        assertEquals("Draft", local[1].blueprint)
        assertEquals("Task", local[0].tasks.single().title)
    }

    @Test fun rejectsInvalidSourceWithoutProducingAProject() {
        assertNull(previewGitHubImport(repository.copy(id = 0), emptyList()))
        assertNull(previewGitHubImport(repository.copy(fullName = "owner/.."), emptyList()))
        assertNull(previewGitHubImport(repository.copy(fullName = "owner/repo?query=1"), emptyList()))
    }

    @Test fun mapsAvailableDocumentsWithoutReplacingRepositoryMetadataOrLocalProjects() {
        val local = listOf(ProjectItem("local", "Personal", "Keep this", blueprint = "Local draft"))
        val documents = mapOf(
            GitHubPlanningFile.BLUEPRINT to GitHubPreviewSection.Available(
                GitHubPlanningDocument("Imported blueprint")
            ),
            GitHubPlanningFile.ROADMAP to GitHubPreviewSection.Unavailable(GitHubReadError.NOT_FOUND)
        )

        val proposal = requireNotNull(previewGitHubImport(repository, local, documents))

        assertEquals("About\n\nhttps://github.com/owner/repo", proposal.project.description)
        assertEquals("Imported blueprint", proposal.project.blueprint)
        assertEquals("", proposal.project.roadmap)
        assertTrue(proposal.project.tasks.isEmpty())
        assertEquals("Keep this", local.single().description)
        assertEquals("Local draft", local.single().blueprint)
        assertEquals(documents, proposal.planningDocuments)
    }

    @Test fun detectsRemoteChangesAndConflictsWithLocalEdits() {
        val local = ProjectItem("github:24", "Project", "Notes", roadmap = "Local roadmap")
        val changed = local.copy(roadmap = "Edited roadmap")
        val current = GitHubRemoteSnapshot(repository.copy(description = "Changed", defaultBranch = "next"), "new")
        val localList = listOf(local)

        val remoteOnly = compareGitHubSnapshots(baseline, current, localList, localList)
        assertEquals(GitHubSyncDisposition.REMOTE_ONLY, remoteOnly.disposition)
        assertEquals(setOf(GitHubRemoteChange.METADATA, GitHubRemoteChange.DEFAULT_BRANCH, GitHubRemoteChange.HEAD), remoteOnly.changes)
        assertEquals(GitHubSyncDisposition.LOCAL_ONLY,
            compareGitHubSnapshots(baseline, baseline, localList, listOf(changed)).disposition)
        assertEquals(GitHubSyncDisposition.CONFLICT,
            compareGitHubSnapshots(baseline, current, localList, listOf(changed)).disposition)
        assertEquals(GitHubSyncDisposition.UNCHANGED,
            compareGitHubSnapshots(baseline, baseline, localList, localList).disposition)
    }

    @Test fun refusesToInferLinksFromNamesAndDetectsAmbiguity() {
        assertEquals(GitHubSyncDisposition.UNLINKED,
            compareGitHubSnapshots(baseline, baseline, emptyList(), emptyList()).disposition)
        val nameOnly = ProjectItem("unlinked", "owner/repo", "Personal notes")
        val conflict = compareGitHubSnapshots(baseline, baseline, listOf(nameOnly), listOf(nameOnly))
        assertEquals(GitHubSyncDisposition.CONFLICT, conflict.disposition)
        assertTrue(conflict.changes.isEmpty())
        assertEquals(GitHubSyncDisposition.CONFLICT,
            compareGitHubSnapshots(baseline, baseline.copy(repository = repository.copy(id = 25)), emptyList(), emptyList()).disposition)
    }

    @Test fun changedHeadMarksTheEarlierSnapshotStaleEvenWhenMetadataIsIdentical() {
        val linked = listOf(ProjectItem("github:24", "Local", "Keep"))
        val updated = compareGitHubSnapshots(baseline, baseline.copy(head = "new"), linked, linked)
        assertEquals(setOf(GitHubRemoteChange.HEAD), updated.changes)
        assertEquals(GitHubSyncDisposition.REMOTE_ONLY, updated.disposition)
        assertEquals("old", baseline.head)
    }

    private val repository = GitHubRepository(24, "owner/repo", "About", "main", true, false)
    private val baseline = GitHubRemoteSnapshot(repository, "old")
}
