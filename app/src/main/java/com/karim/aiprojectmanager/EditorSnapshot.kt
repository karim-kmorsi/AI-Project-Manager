package com.karim.aiprojectmanager

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import java.util.Collections

/** Immutable editor base; only the Saver exposes a detached, Bundle-compatible copy. */
@Immutable
internal class EditorSnapshot private constructor(values: List<String>) {
    private val fields: List<String> = Collections.unmodifiableList(ArrayList(values))

    override fun equals(other: Any?): Boolean = other is EditorSnapshot && fields == other.fields
    override fun hashCode(): Int = fields.hashCode()

    companion object {
        // Retain the existing ArrayList<String> saved-state representation, never project JSON.
        val saver = Saver<EditorSnapshot, ArrayList<String>>(
            save = { ArrayList(it.fields) },
            restore = { EditorSnapshot(it) }
        )

        fun from(project: ProjectItem): EditorSnapshot = EditorSnapshot(buildList {
            add(project.id)
            add(project.name)
            add(project.description)
            add(project.blueprint)
            add(project.roadmap)
            add(project.tasks.size.toString())
            project.tasks.forEach { task -> add(task.id); add(task.title); add(task.status) }
        })
    }
}

internal fun editorSnapshot(project: ProjectItem): EditorSnapshot = EditorSnapshot.from(project)
