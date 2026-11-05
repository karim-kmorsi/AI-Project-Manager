package com.karim.aiprojectmanager

/** Saveable editor base: structured values, never persisted into the project JSON. */
internal fun editorSnapshot(project: ProjectItem): ArrayList<String> = arrayListOf(
    project.id, project.name, project.description, project.blueprint, project.roadmap,
    project.tasks.size.toString()
).apply {
    project.tasks.forEach { task -> add(task.id); add(task.title); add(task.status) }
}
