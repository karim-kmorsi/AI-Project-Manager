package com.karim.aiprojectmanager

internal data class GitHubImportPreview(
    val project: ProjectItem,
    val candidates: List<ProjectItem>,
    val planningDocuments: Map<GitHubPlanningFile, GitHubPreviewSection<GitHubPlanningDocument>>?
)

internal enum class GitHubImportOutcome { IMPORTED, DUPLICATE, FAILED }

internal fun previewGitHubImport(
    repository: GitHubRepository,
    local: List<ProjectItem>,
    planningDocuments: Map<GitHubPlanningFile, GitHubPreviewSection<GitHubPlanningDocument>>? = null
): GitHubImportPreview? {
    val name = repository.fullName
    if (repository.id <= 0 || !isGitHubRepositoryName(name)) {
        return null
    }

    val sourceUrl = "https://github.com/$name"
    val project = ProjectItem(
        id = "github:${repository.id}",
        name = name,
        description = listOfNotNull(repository.description?.takeIf { it.isNotBlank() }, sourceUrl)
            .joinToString("\n\n"),
        blueprint = planningDocuments?.get(GitHubPlanningFile.BLUEPRINT)
            .availableText().orEmpty(),
        roadmap = planningDocuments?.get(GitHubPlanningFile.ROADMAP)
            .availableText().orEmpty()
    )
    val candidates = local.filter { existing ->
        existing.id == project.id ||
            existing.name.trim().equals(name, ignoreCase = true) ||
            existing.description.lineSequence().any { it.trim().trimEnd('/').equals(sourceUrl, ignoreCase = true) }
    }
    return GitHubImportPreview(project, candidates, planningDocuments)
}

private fun GitHubPreviewSection<GitHubPlanningDocument>?.availableText(): String =
    (this as? GitHubPreviewSection.Available)?.value?.text.orEmpty()
