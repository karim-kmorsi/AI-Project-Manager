package com.karim.aiprojectmanager

internal data class GitHubImportPreview(val project: ProjectItem, val candidates: List<ProjectItem>)

internal fun previewGitHubImport(repository: GitHubRepository, local: List<ProjectItem>): GitHubImportPreview? {
    val name = repository.fullName
    if (repository.id <= 0 || !isGitHubRepositoryName(name)) {
        return null
    }

    val sourceUrl = "https://github.com/$name"
    val project = ProjectItem(
        id = "github:${repository.id}",
        name = name,
        description = listOfNotNull(repository.description?.takeIf { it.isNotBlank() }, sourceUrl)
            .joinToString("\n\n")
    )
    val candidates = local.filter { existing ->
        existing.id == project.id ||
            existing.name.trim().equals(name, ignoreCase = true) ||
            existing.description.lineSequence().any { it.trim().trimEnd('/').equals(sourceUrl, ignoreCase = true) }
    }
    return GitHubImportPreview(project, candidates)
}
