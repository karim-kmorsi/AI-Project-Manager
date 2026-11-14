package com.karim.aiprojectmanager

internal data class GitHubBranchPreview(
    val name: String,
    val protected: Boolean,
    val commitSha: String,
    val treeSha: String,
    val message: String,
    val committedAt: String?
)

internal data class GitHubReadmePreview(val text: String, val truncated: Boolean)
internal data class GitHubTreeEntry(val path: String, val directory: Boolean)
internal data class GitHubTreePreview(
    val entries: List<GitHubTreeEntry>,
    val truncated: Boolean,
    val rootFiles: Set<String> = emptySet()
)

internal enum class GitHubPlanningFile(val fileName: String) {
    BLUEPRINT("BLUEPRINT.md"),
    ROADMAP("ROADMAP.md")
}

internal data class GitHubPlanningDocument(val text: String)

internal interface GitHubRepositoryPreviewSource {
    suspend fun snapshot(repository: GitHubRepository): GitHubRemoteSnapshot
    suspend fun branch(repository: GitHubRepository): GitHubBranchPreview
    suspend fun readme(repository: GitHubRepository, commitSha: String): GitHubReadmePreview
    suspend fun tree(repository: GitHubRepository, treeSha: String): GitHubTreePreview
    suspend fun planningDocument(
        repository: GitHubRepository,
        document: GitHubPlanningFile,
        commitSha: String
    ): GitHubPlanningDocument = throw GitHubReadFailure(GitHubReadError.NOT_FOUND)
}

internal sealed interface GitHubPreviewSection<out T> {
    data class Available<T>(val value: T) : GitHubPreviewSection<T>
    data class Unavailable(val reason: GitHubReadError) : GitHubPreviewSection<Nothing>
}

internal data class GitHubRepositoryPreview(
    val branch: GitHubPreviewSection<GitHubBranchPreview>,
    val readme: GitHubPreviewSection<GitHubReadmePreview>?,
    val tree: GitHubPreviewSection<GitHubTreePreview>?,
    val planningDocuments: Map<GitHubPlanningFile, GitHubPreviewSection<GitHubPlanningDocument>>?
)

internal suspend fun loadGitHubRepositoryPreview(
    source: GitHubRepositoryPreviewSource,
    repository: GitHubRepository
): GitHubRepositoryPreview {
    val branch = previewSection { source.branch(repository) }
    return when (branch) {
        is GitHubPreviewSection.Available -> {
            val readme = previewSection { source.readme(repository, branch.value.commitSha) }
            val tree = previewSection { source.tree(repository, branch.value.treeSha) }
            val documents = GitHubPlanningFile.entries.associateWith { document ->
                when (tree) {
                    is GitHubPreviewSection.Available -> if (document.fileName in tree.value.rootFiles) {
                        previewSection { source.planningDocument(repository, document, branch.value.commitSha) }
                    } else {
                        GitHubPreviewSection.Unavailable(GitHubReadError.NOT_FOUND)
                    }
                    is GitHubPreviewSection.Unavailable ->
                        GitHubPreviewSection.Unavailable(tree.reason)
                }
            }
            GitHubRepositoryPreview(branch, readme, tree, documents)
        }
        is GitHubPreviewSection.Unavailable -> GitHubRepositoryPreview(branch, null, null, null)
    }
}

private suspend fun <T> previewSection(load: suspend () -> T): GitHubPreviewSection<T> = try {
    GitHubPreviewSection.Available(load())
} catch (failure: GitHubReadFailure) {
    if (failure.reason in setOf(GitHubReadError.EXPIRED, GitHubReadError.RATE_LIMIT)) throw failure
    GitHubPreviewSection.Unavailable(failure.reason)
}
