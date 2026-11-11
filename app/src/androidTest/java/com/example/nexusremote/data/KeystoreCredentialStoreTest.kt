package com.example.nexusremote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexusremote.data.security.KeystoreCipher
import com.example.nexusremote.data.security.KeystoreCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/**
 * Real Android Keystore on the emulator, with a test-only alias and folder that are removed
 * after every test. The app's real alias and files/secure folder are never touched.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreCredentialStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "nexus_test_${UUID.randomUUID()}"
    private val dir = File(context.cacheDir, "secure-test-${UUID.randomUUID()}")
    private val store = KeystoreCredentialStore(context, cipher = KeystoreCipher(alias), dir = dir)

    private val deviceA = UUID.randomUUID().toString()
    private val deviceB = UUID.randomUUID().toString()

    @After
    fun cleanUp() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        dir.deleteRecursively()
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
    }

    @Test
    fun storeReloadAndDelete() = runBlocking {
        val created = store.getOrCreateKeyPair(deviceA)
        assertTrue(store.hasCredentials(deviceA))

        val reloaded = store.loadKeyPair(deviceA)
        assertNotNull(reloaded)
        assertArrayEquals(created.private.encoded, reloaded!!.private.encoded)
        assertArrayEquals(created.public.encoded, reloaded.public.encoded)
        assertEquals("RSA", reloaded.private.algorithm)

        store.deleteKeyPair(deviceA)
        assertFalse(store.hasCredentials(deviceA))
        assertNull(store.loadKeyPair(deviceA))
        assertFalse(File(dir, "adb_$deviceA.bin").exists())
    }

    @Test
    fun getOrCreateReturnsTheSameKeyEveryTime() = runBlocking {
        val first = store.getOrCreateKeyPair(deviceA)
        val second = store.getOrCreateKeyPair(deviceA)
        assertArrayEquals(first.private.encoded, second.private.encoded)
    }

    @Test
    fun eachDeviceGetsItsOwnKey() = runBlocking {
        val a = store.getOrCreateKeyPair(deviceA)
        val b = store.getOrCreateKeyPair(deviceB)
        assertFalse(a.public.encoded.contentEquals(b.public.encoded))
        store.deleteCredentials(deviceA)
        assertNull(store.loadKeyPair(deviceA))
        assertNotNull("deleting A must not affect B", store.loadKeyPair(deviceB))
    }

    @Test
    fun privateKeyIsNotStoredInPlaintext() = runBlocking {
        val pair = store.getOrCreateKeyPair(deviceA)
        val onDisk = File(dir, "adb_$deviceA.bin").readBytes()
        val probe = pair.private.encoded.copyOfRange(32, 96)
        assertFalse("PKCS#8 bytes must not appear on disk", onDisk.containsSubsequence(probe))
    }

    @Test
    fun blobCopiedToAnotherDeviceIdCannotBeDecrypted() = runBlocking {
        store.getOrCreateKeyPair(deviceA)
        File(dir, "adb_$deviceA.bin").copyTo(File(dir, "adb_$deviceB.bin"))
        assertNull("ciphertext is bound to its device id", store.loadKeyPair(deviceB))
    }

    @Test
    fun invalidDeviceIdsAreRejected() = runBlocking {
        assertNull(store.loadKeyPair("../../shared_prefs/x"))
        assertFalse(store.hasCredentials("../x"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.getOrCreateKeyPair("../escape") }
        }
        Unit
    }

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
