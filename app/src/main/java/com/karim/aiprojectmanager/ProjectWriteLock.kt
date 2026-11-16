package com.karim.aiprojectmanager

/** A process-local mutex shared by project saves and isolated restore transactions.
 * Does not coordinate with other processes or writers outside these two code paths.
 */
internal object ProjectWriteLock {
    val monitor = Any()
}

/** Include AtomicFile staging/backup names when deciding whether files are independent. */
internal fun requireIndependentAtomicFiles(vararg files: java.io.File) {
    val occupied = HashSet<String>()
    files.forEach { file ->
        val path = file.canonicalPath
        for (candidate in listOf(path, "$path.new", "$path.bak")) {
            if (!occupied.add(candidate)) throw java.io.IOException("Files overlap with atomic storage sidecars")
        }
    }
}
