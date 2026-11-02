package com.karim.aiprojectmanager

/** A process-local mutex shared by project saves and isolated restore transactions.
 * Does not coordinate with other processes or writers outside these two code paths.
 */
internal object ProjectWriteLock {
    val monitor = Any()
}
