package com.example.nexusremote.data.security

import android.content.Context
import android.util.AtomicFile
import com.example.nexusremote.domain.repository.CredentialStore
import com.example.nexusremote.util.InputValidators
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

/**
 * Stores one ADB host key pair per TV.
 *
 * The RSA private key (PKCS#8 bytes) is encrypted with AES-256-GCM using a key that lives in
 * the Android Keystore ([KeystoreCipher]) and never leaves it. The encrypted blob is written
 * to files/secure/adb_<uuid>.bin, which is excluded from backup. The public key is re-derived
 * from the private key, so nothing else needs storing. Plaintext key bytes are wiped after use.
 */
class KeystoreCredentialStore(
    context: Context,
    private val cipher: KeystoreCipher = KeystoreCipher(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Tests pass a separate folder so they never touch real app secrets. */
    private val dir: File = File(context.filesDir, "secure"),
) : HostKeyStore, CredentialStore {
    private val mutex = Mutex()

    override suspend fun getOrCreateKeyPair(deviceId: String): KeyPair = withContext(io) {
        mutex.withLock {
            readLocked(deviceId) ?: generateAndStoreLocked(deviceId)
        }
    }

    override suspend fun loadKeyPair(deviceId: String): KeyPair? = withContext(io) {
        mutex.withLock { readLocked(deviceId) }
    }

    override suspend fun deleteKeyPair(deviceId: String) = withContext(io) {
        mutex.withLock {
            fileFor(deviceId)?.let { AtomicFile(it).delete() }
            Unit
        }
    }

    override suspend fun hasCredentials(deviceId: String): Boolean = withContext(io) {
        fileFor(deviceId)?.exists() == true
    }

    override suspend fun deleteCredentials(deviceId: String) = deleteKeyPair(deviceId)

    private fun fileFor(deviceId: String): File? {
        // Device ids become file names: only canonical UUIDs are allowed (no path traversal).
        if (!InputValidators.isValidDeviceId(deviceId)) return null
        return File(dir, "adb_${deviceId.lowercase()}.bin")
    }

    private fun readLocked(deviceId: String): KeyPair? {
        val file = fileFor(deviceId) ?: return null
        if (!file.exists()) return null
        val blob = runCatching { AtomicFile(file).readFully() }.getOrNull() ?: return null
        val pkcs8 = try {
            cipher.decrypt(blob, aad(deviceId))
        } catch (_: GeneralSecurityException) {
            // Keystore key lost (e.g. app data partially cleared) - treat as "not paired".
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return try {
            rebuildKeyPair(pkcs8)
        } catch (_: GeneralSecurityException) {
            null
        } finally {
            pkcs8.fill(0)
        }
    }

    private fun generateAndStoreLocked(deviceId: String): KeyPair {
        val file = fileFor(deviceId) ?: throw IllegalArgumentException("Invalid device id")
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(RSA_BITS)
        val pair = generator.generateKeyPair()
        val pkcs8 = pair.private.encoded
        try {
            val blob = cipher.encrypt(pkcs8, aad(deviceId))
            dir.mkdirs()
            val atomic = AtomicFile(file)
            val out = atomic.startWrite()
            try {
                out.write(blob)
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
                throw e
            }
        } finally {
            pkcs8.fill(0)
        }
        return pair
    }

    private fun rebuildKeyPair(pkcs8: ByteArray): KeyPair {
        val factory = KeyFactory.getInstance("RSA")
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        val crt = private as? RSAPrivateCrtKey ?: throw GeneralSecurityException("Unexpected key type")
        val public = factory.generatePublic(RSAPublicKeySpec(crt.modulus, crt.publicExponent))
        return KeyPair(public, private)
    }

    /** Binds each ciphertext to its device id so blobs cannot be swapped between files. */
    private fun aad(deviceId: String): ByteArray = "nexus-adb-key:$deviceId".toByteArray(Charsets.UTF_8)

    private companion object {
        const val RSA_BITS = 2048
    }
}
