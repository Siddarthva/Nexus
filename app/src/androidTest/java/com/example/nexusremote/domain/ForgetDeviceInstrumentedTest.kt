package com.example.nexusremote.domain

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexusremote.data.local.DataStoreDeviceRepository
import com.example.nexusremote.data.security.KeystoreCipher
import com.example.nexusremote.data.security.KeystoreCredentialStore
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.usecase.ForgetDeviceUseCase
import com.example.nexusremote.fakes.FakeTvControlClient
import com.example.nexusremote.fakes.TestData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Forget = real DataStore profile + real Keystore-backed key are both deleted. */
@RunWith(AndroidJUnit4::class)
class ForgetDeviceInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefsName = "test_forget_${UUID.randomUUID()}"
    private val alias = "nexus_test_${UUID.randomUUID()}"
    private val dir = File(context.cacheDir, "secure-test-${UUID.randomUUID()}")
    private val job = Job()

    private val devices = DataStoreDeviceRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            context.preferencesDataStoreFile(prefsName)
        },
    )
    private val keys = KeystoreCredentialStore(context, cipher = KeystoreCipher(alias), dir = dir)

    @After
    fun cleanUp() = runBlocking {
        job.cancel(); job.join()
        context.preferencesDataStoreFile(prefsName).delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        dir.deleteRecursively()
        Unit
    }

    @Test
    fun forgetDeletesProfileAndStoredKeyAndDisconnects() = runBlocking {
        devices.upsert(TestData.livingRoom)
        devices.upsert(TestData.bedroom)
        devices.setActiveDevice(TestData.LIVING_ROOM_ID)
        keys.getOrCreateKeyPair(TestData.LIVING_ROOM_ID)
        keys.getOrCreateKeyPair(TestData.BEDROOM_ID)
        val client = FakeTvControlClient(ConnectionState.Connected(TestData.LIVING_ROOM_ID))

        ForgetDeviceUseCase(client, devices, keys)(TestData.LIVING_ROOM_ID)

        assertNull(devices.getDevice(TestData.LIVING_ROOM_ID))
        assertFalse(keys.hasCredentials(TestData.LIVING_ROOM_ID))
        assertNull(keys.loadKeyPair(TestData.LIVING_ROOM_ID))
        assertEquals(1, client.disconnectCalls)
        assertEquals(TestData.BEDROOM_ID, devices.activeDeviceId.first())
        assertNotNull("other TV's key is untouched", keys.loadKeyPair(TestData.BEDROOM_ID))
    }

    @Test
    fun forgettingAnUnconnectedTvDoesNotDisconnectTheCurrentOne() = runBlocking {
        devices.upsert(TestData.livingRoom)
        devices.upsert(TestData.bedroom)
        keys.getOrCreateKeyPair(TestData.BEDROOM_ID)
        val client = FakeTvControlClient(ConnectionState.Connected(TestData.LIVING_ROOM_ID))

        ForgetDeviceUseCase(client, devices, keys)(TestData.BEDROOM_ID)

        assertEquals(0, client.disconnectCalls)
        assertNull(devices.getDevice(TestData.BEDROOM_ID))
        assertFalse(keys.hasCredentials(TestData.BEDROOM_ID))
    }
}
