package com.karim.aiprojectmanager

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import java.security.MessageDigest

/** Immutable conflict token, never project JSON. SHA-256 collision risk is nonzero; this is not authentication. */
@Immutable
internal class EditorSnapshot private constructor(private val fingerprint: String?) {
    // An unsupported/corrupt restored baseline must fail closed, not rebase pending input on newer data.
    override fun equals(other: Any?): Boolean = this === other ||
        (other is EditorSnapshot && fingerprint != null && fingerprint == other.fingerprint)
    override fun hashCode(): Int = fingerprint?.hashCode() ?: 0

    companion object {
        private const val FORMAT = "sha256-utf16-v1"
        private val HEX = Regex("[0-9a-f]{64}")
        val saver = Saver<EditorSnapshot, ArrayList<String>>(
            save = { arrayListOf(FORMAT, it.fingerprint ?: "invalid") },
            restore = { saved ->
                val values: List<*> = saved
                when {
                    values.size == 2 && values[0] == FORMAT && values[1] is String && HEX.matches(values[1] as String) ->
                        EditorSnapshot(values[1] as String)
                    // Prior saved state was the full ordered field list, with at least six project fields.
                    values.size >= 6 && values.all { it is String } -> EditorSnapshot(digest(saved))
                    else -> EditorSnapshot(null)
                }
            }
        )

        fun from(project: ProjectItem): EditorSnapshot = EditorSnapshot(digest(buildList {
            add(project.id)
            add(project.name)
            add(project.description)
            add(project.blueprint)
            add(project.roadmap)
            add(project.tasks.size.toString())
            project.tasks.forEach { task -> add(task.id); add(task.title); add(task.status) }
        }))

        private fun digest(fields: List<String>): String {
            val hash = MessageDigest.getInstance("SHA-256")
            hash.update("AI-PM-EDITOR-SNAPSHOT-1".toByteArray(Charsets.US_ASCII))
            fun integer(value: Int) {
                for (shift in intArrayOf(24, 16, 8, 0)) hash.update((value ushr shift).toByte())
            }
            integer(fields.size)
            val buffer = ByteArray(2048)
            for (field in fields) {
                integer(field.length)
                var used = 0
                // Hash exact UTF-16 code units, including unpaired surrogates; UTF-8 replacement
                // would collapse distinct freeform strings. Lengths/count preserve boundaries/order.
                for (character in field) {
                    buffer[used++] = (character.code ushr 8).toByte()
                    buffer[used++] = character.code.toByte()
                    if (used == buffer.size) { hash.update(buffer); used = 0 }
                }
                if (used > 0) hash.update(buffer, 0, used)
            }
            return hash.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
    }
}

internal fun editorSnapshot(project: ProjectItem): EditorSnapshot = EditorSnapshot.from(project)
