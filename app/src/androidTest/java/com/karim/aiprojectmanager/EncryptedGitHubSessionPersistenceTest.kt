package com.karim.aiprojectmanager

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedGitHubSessionPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptsCredentialsWithKeystoreAndUsesANewIvForEachWrite() {
        val (store, file, alias) = fixture()
        try {
            store.write("octocat", TOKEN)
            val firstCiphertext = file.readBytes()
            assertFalse(firstCiphertext.toString(Charsets.ISO_8859_1).contains(TOKEN))

            store.write("octocat", TOKEN)
            val secondCiphertext = file.readBytes()
            assertNotEquals(firstCiphertext.toList(), secondCiphertext.toList())

            val restored = requireNotNull(EncryptedGitHubSessionPersistence(context, alias, file.name).read())
            assertEquals("octocat", restored.login)
            assertEquals(TOKEN, restored.tokenForVerification())
        } finally {
            store.clear()
            file.delete()
        }
    }

    @Test fun missingKeystoreKeyDoesNotRestoreAndRemovesUnusableCiphertext() {
        val (store, file, alias) = fixture()
        try {
            store.write("octocat", TOKEN)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
            val failure = assertThrows(GitHubSessionStorageFailure::class.java) { store.read() }
            assertEquals(GitHubSessionStorageError.KEY_MISSING, failure.reason)
            assertFalse(file.exists())
        } finally {
            store.clear()
            file.delete()
        }
    }

    @Test fun corruptCiphertextIsRejectedAndDiscardedWithoutReturningCredentials() {
        val (store, file, alias) = fixture()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val failure = assertThrows(GitHubSessionStorageFailure::class.java) { store.read() }
            assertEquals(GitHubSessionStorageError.CORRUPT, failure.reason)
            assertNull(runCatching { store.read() }.getOrNull())
            assertFalse(file.exists())
        } finally {
            store.clear()
            file.delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) }
        }
    }

    private fun fixture(): Triple<EncryptedGitHubSessionPersistence, File, String> {
        val suffix = System.nanoTime().toString()
        val alias = "com.karim.aiprojectmanager.test.session.$suffix"
        val file = File(context.noBackupFilesDir, "github-test-$suffix.enc")
        return Triple(EncryptedGitHubSessionPersistence(context, alias, file.name), file, alias)
    }

    private companion object { const val TOKEN = "ghu_OnlySyntheticTestToken" }
}
