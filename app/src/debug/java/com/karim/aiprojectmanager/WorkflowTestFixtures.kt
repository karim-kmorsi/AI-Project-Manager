package com.karim.aiprojectmanager

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Debug-only host: real MainActivity callbacks/Compose, unique disposable files on every launch. */
class IsolatedWorkflowActivity : MainActivity() {
    private var root: File? = null
    val fixtureRoot: File get() = checkNotNull(root)
    val notifications = CopyOnWriteArrayList<String>()

    companion object { @Volatile var current: IsolatedWorkflowActivity? = null }
    override fun onCreate(savedInstanceState: Bundle?) {
        val token = savedInstanceState?.getString("fixtureToken") ?: UUID.randomUUID().toString()
        require(UUID.fromString(token).toString() == token)
        root = File(super.getCacheDir(), "workflow-fixture-$token")
        if (!fixtureRoot.exists()) {
            check(fixtureRoot.mkdir())
            ProjectStorage(File(filesDir, "projects.json")).save(listOf(
                ProjectItem("active", "Lifecycle project", "Keep description", "Original Blueprint", "Original Roadmap",
                    listOf(TaskItem("t", "Preserved task", "VERIFIED"))),
                ProjectItem("other", "Unrelated project", "Keep everything", "Other Blueprint", "Other Roadmap")
            ))
        }
        super.onCreate(savedInstanceState)
        current = this
    }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("fixtureToken", fixtureRoot.name.removePrefix("workflow-fixture-"))
        super.onSaveInstanceState(outState)
    }
    override fun getFilesDir(): File = root?.let { File(it, "files").apply { check(isDirectory || mkdir()) } } ?: super.getFilesDir()
    override fun getCacheDir(): File = root?.let { File(it, "cache").apply { check(isDirectory || mkdir()) } } ?: super.getCacheDir()
    override fun notifyUser(message: String) { notifications.add(message) }
}

/** Instrumentation redirects only document-picker intents here; no system UI or external files. */
class DeterministicPickerActivity : Activity() {
    @Volatile var ready = false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); current = this }
    override fun onResume() { super.onResume(); ready = true }
    override fun onPause() { ready = false; super.onPause() }
    fun deliver(result: Int, uri: Uri? = null) {
        setResult(result, uri?.let { Intent().setData(it) })
        finish()
    }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    companion object { @Volatile var current: DeterministicPickerActivity? = null }
}

/** Same-app, non-exported content provider with deterministic resolver errors and private fixtures. */
class DeterministicDocumentProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/json"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        opens.incrementAndGet()
        val name = uri.lastPathSegment ?: throw FileNotFoundException("Missing fixture")
        if (name == "denied") throw SecurityException("Disposable provider denied access")
        if (name == "unavailable") throw FileNotFoundException("Disposable provider unavailable")
        if (name == "broken-output") {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            pipe[0].closeWithError("Disposable provider failed")
            return pipe[1]
        }
        if (name == "delayed.json") {
            delayedStarted.countDown()
            check(releaseDelayed.await(10, TimeUnit.SECONDS)) { "Disposable provider gate timed out" }
        }
        require(name in setOf("selected.json", "export.json", "delayed.json"))
        val file = File(checkNotNull(directory), name)
        val flags = if (mode.contains('w')) ParcelFileDescriptor.MODE_WRITE_ONLY or
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(file, flags)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    companion object {
        @Volatile var directory: File? = null
        val opens = AtomicInteger()
        @Volatile var delayedStarted = CountDownLatch(1)
        @Volatile var releaseDelayed = CountDownLatch(1)
        fun uri(name: String): Uri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.debug.documents/$name")
    }
}
