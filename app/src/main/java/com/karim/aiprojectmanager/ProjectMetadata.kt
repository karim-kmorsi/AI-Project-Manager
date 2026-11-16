package com.karim.aiprojectmanager

/** Change only existing metadata; retain identity, documents and tasks verbatim. */
internal fun editedProjectMetadata(project: ProjectItem, name: String, description: String): ProjectItem {
    require(name.isNotBlank()) { "Project name must not be blank" }
    return project.copy(name = name.trim(), description = description)
}
