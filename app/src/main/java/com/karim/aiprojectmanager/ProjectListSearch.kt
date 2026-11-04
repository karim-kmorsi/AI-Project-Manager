package com.karim.aiprojectmanager

/**
 * Read-only filtering of existing project metadata. No storage writes or indexing.
 * Keeps the saved project order and leaves all stored records untouched.
 */
internal fun filterProjectsByQuery(projects: List<ProjectItem>, query: String): List<ProjectItem> {
    val term = query.trim()
    if (term.isEmpty()) return projects
    return projects.filter { project ->
        project.name.contains(term, ignoreCase = true) ||
            project.description.contains(term, ignoreCase = true)
    }
}
