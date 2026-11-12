package com.karim.aiprojectmanager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal enum class GitHubSessionStorageError { CORRUPT, KEYSTORE, KEY_MISSING, KEY_INVALIDATED, IO }

/** Sanitized: platform and crypto exceptions may contain implementation details. */
internal class GitHubSessionStorageFailure(val reason: GitHubSessionStorageError) : Exception(reason.name)

internal class GitHubSessionCredentials internal constructor(
    val login: String,
    private val accessToken: String
) {
    internal fun tokenForVerification(): String = accessToken
    override fun toString(): String = "GitHubSessionCredentials(login=$login, accessToken=[redacted])"
}

internal interface GitHubSessionPersistence {
    fun read(): GitHubSessionCredentials?
    fun write(login: String, accessToken: String)
    fun clear()
}

/** Used only by isolated UI/auth tests which don't need durable credentials. */
internal class InMemoryGitHubSessionPersistence : GitHubSessionPersistence {
    private var credentials: GitHubSessionCredentials? = null
    override fun read() = credentials
    override fun write(login: String, accessToken: String) {
        credentials = GitHubSessionCredentials(login, accessToken)
    }
    override fun clear() { credentials = null }
}

/** AES-GCM envelope in no-backup app storage; the AES key never leaves Android Keystore. */
internal class EncryptedGitHubSessionPersistence(
    context: Context,
    private val keyAlias: String = KEY_ALIAS,
    fileName: String = FILE_NAME
) : GitHubSessionPersistence {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))

    override fun read(): GitHubSessionCredentials? {
        val bytes = try {
            file.openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_FILE_SIZE) corrupt()
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: FileNotFoundException) {
            return null
        } catch (failure: GitHubSessionStorageFailure) {
            if (failure.reason == GitHubSessionStorageError.CORRUPT) discardCiphertext()
            throw failure
        } catch (_: Exception) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.IO)
        }
        try {
            if (bytes.size !in (HEADER_SIZE + GCM_IV_SIZE + GCM_TAG_SIZE)..MAX_FILE_SIZE) corrupt()
            val input = DataInputStream(ByteArrayInputStream(bytes))
            if (input.readInt() != MAGIC || input.readUnsignedByte() != FORMAT_VERSION) corrupt()
            val ivLength = input.readUnsignedByte()
            if (ivLength != GCM_IV_SIZE) corrupt()
            val iv = ByteArray(ivLength).also(input::readFully)
            val encrypted = ByteArray(input.available()).also(input::readFully)
            if (encrypted.size < GCM_TAG_SIZE) corrupt()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, loadKey(create = false), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(AAD)
            val plaintext = try { cipher.doFinal(encrypted) } catch (_: AEADBadTagException) { corrupt() }
            try {
                val decoded = DataInputStream(ByteArrayInputStream(plaintext))
                if (decoded.readInt() != FORMAT_VERSION) corrupt()
                val login = decoded.readUTF()
                val token = decoded.readUTF()
                if (decoded.available() != 0 || !LOGIN.matches(login) || !TOKEN.matches(token)) corrupt()
                return GitHubSessionCredentials(login, token)
            } finally {
                plaintext.fill(0)
            }
        } catch (failure: GitHubSessionStorageFailure) {
            if (failure.reason in setOf(GitHubSessionStorageError.CORRUPT,
                    GitHubSessionStorageError.KEY_MISSING, GitHubSessionStorageError.KEY_INVALIDATED)) discardCiphertext()
            throw failure
        } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
            discardCiphertext()
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEY_INVALIDATED)
        } catch (_: GeneralSecurityException) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEYSTORE)
        } catch (_: Exception) {
            discardCiphertext()
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.CORRUPT)
        }
    }

    override fun write(login: String, accessToken: String) {
        if (!LOGIN.matches(login) || !TOKEN.matches(accessToken)) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.CORRUPT)
        }
        try {
            val payload = ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(FORMAT_VERSION)
                    output.writeUTF(login)
                    output.writeUTF(accessToken)
                }
            }.toByteArray()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, loadKey(create = true))
            cipher.updateAAD(AAD)
            val encrypted = try { cipher.doFinal(payload) } finally { payload.fill(0) }
            val iv = cipher.iv
            if (iv.size != GCM_IV_SIZE) throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEYSTORE)
            val envelope = ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(MAGIC)
                    output.writeByte(FORMAT_VERSION)
                    output.writeByte(iv.size)
                    output.write(iv)
                    output.write(encrypted)
                }
            }.toByteArray()
            var stream: java.io.FileOutputStream? = null
            try {
                stream = file.startWrite()
                stream.write(envelope)
                file.finishWrite(stream)
                stream = null
            } finally {
                if (stream != null) file.failWrite(stream)
            }
        } catch (failure: GitHubSessionStorageFailure) {
            throw failure
        } catch (failure: android.security.keystore.KeyPermanentlyInvalidatedException) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEY_INVALIDATED)
        } catch (_: GeneralSecurityException) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEYSTORE)
        } catch (_: Exception) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.IO)
        }
    }

    override fun clear() {
        try {
            file.delete()
        } catch (_: Exception) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.IO)
        }
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        } catch (_: Exception) {
            // The ciphertext has been deleted. A leftover unreferenced key contains no credential.
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEYSTORE)
        }
    }

    private fun loadKey(create: Boolean): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = try { keyStore.getKey(keyAlias, null) as? SecretKey }
            catch (_: java.security.UnrecoverableKeyException) {
                throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEY_INVALIDATED)
            }
            if (existing != null) return existing
            if (!create) throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEY_MISSING)
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
            return generator.generateKey()
        } catch (failure: GitHubSessionStorageFailure) {
            throw failure
        } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEY_INVALIDATED)
        } catch (_: Exception) {
            throw GitHubSessionStorageFailure(GitHubSessionStorageError.KEYSTORE)
        }
    }

    private fun discardCiphertext() {
        try { file.delete() } catch (_: Exception) { }
    }

    private fun corrupt(): Nothing = throw GitHubSessionStorageFailure(GitHubSessionStorageError.CORRUPT)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.karim.aiprojectmanager.github.session.v1"
        const val FILE_NAME = "github-session.enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAGIC = 0x47484131 // GHA1
        const val FORMAT_VERSION = 1
        const val HEADER_SIZE = 6
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_SIZE = GCM_TAG_BITS / 8
        const val MAX_FILE_SIZE = 8 * 1024
        val AAD = byteArrayOf(0x47, 0x48, 0x41, 0x31, 0x01)
        val LOGIN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,38}")
        val TOKEN = Regex("ghu_[A-Za-z0-9]{1,252}")
    }
}
