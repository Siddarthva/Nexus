package com.example.nexusremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexusremote.data.local.DataStoreDeviceRepository
import com.example.nexusremote.data.local.DataStoreSettingsRepository
import com.example.nexusremote.fakes.TestData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Real DataStore on the emulator, using a throw-away file (never the app's real prefs).
 * "Process restart" is simulated by closing the DataStore (cancelling its scope) and opening
 * a brand-new instance on the same file, which forces a re-read from disk.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileName = "test_prefs_${UUID.randomUUID()}"
    private val jobs = mutableListOf<Job>()

    private fun openStore(): DataStore<Preferences> {
        val job = Job().also { jobs += it }
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            context.preferencesDataStoreFile(fileName)
        }
    }

    private suspend fun restart(): DataStore<Preferences> {
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
        jobs.clear()
        return openStore()
    }

    @After
    fun cleanUp() = runBlocking {
        jobs.forEach { it.cancel(); it.join() }
        context.preferencesDataStoreFile(fileName).delete()
        Unit
    }

    @Test
    fun saveAndReloadTvProfile() = runBlocking {
        val repo = DataStoreDeviceRepository(openStore())
        repo.upsert(TestData.livingRoom.copy(pairingPort = 37123))
        repo.setActiveDevice(TestData.LIVING_ROOM_ID)

        val reloaded = DataStoreDeviceRepository(restart())
        val device = reloaded.getDevice(TestData.LIVING_ROOM_ID)!!
        assertEquals(TestData.livingRoom, device)
        assertNull("pairing port must never be persisted", device.pairingPort)
        assertEquals(TestData.LIVING_ROOM_ID, reloaded.activeDeviceId.first())
        assertFalse(
            "pairing port must not appear in the file",
            context.preferencesDataStoreFile(fileName).readBytes().toString(Charsets.ISO_8859_1).contains("37123"),
        )
    }

    @Test
    fun upsertReplacesAndDeleteRemoves() = runBlocking {
        val repo = DataStoreDeviceRepository(openStore())
        repo.upsert(TestData.livingRoom)
        repo.upsert(TestData.bedroom)
        repo.upsert(TestData.livingRoom.copy(friendlyName = "Lounge"))
        assertEquals(listOf("Lounge", "Bedroom TV"), repo.devices.first().map { it.friendlyName })
        repo.setActiveDevice(TestData.LIVING_ROOM_ID)
        repo.delete(TestData.LIVING_ROOM_ID)
        assertNull(repo.activeDeviceId.first())
        assertEquals(TestData.BEDROOM_ID, repo.activeDevice.first()?.id)
    }

    @Test
    fun settingsTogglesPersist() = runBlocking {
        val repo = DataStoreSettingsRepository(openStore())
        val defaults = repo.settings.first()
        assertTrue(defaults.hapticsEnabled)
        assertFalse(defaults.soundEnabled)
        assertTrue(defaults.confirmPowerOff)

        repo.setHapticsEnabled(false)
        repo.setSoundEnabled(true)
        repo.setConfirmPowerOff(false)

        val reloaded = DataStoreSettingsRepository(restart()).settings.first()
        assertFalse(reloaded.hapticsEnabled)
        assertTrue(reloaded.soundEnabled)
        assertFalse(reloaded.confirmPowerOff)
    }

    @Test
    fun favoritesPersistAcrossRestart() = runBlocking {
        val repo = DataStoreSettingsRepository(openStore())
        repo.toggleFavorite("com.netflix.ninja")
        repo.toggleFavorite("com.google.android.youtube.tv")
        repo.toggleFavorite("com.google.android.youtube.tv") // toggled off again
        repo.toggleFavorite("bad name; reboot") // invalid: ignored

        val reloaded = DataStoreSettingsRepository(restart())
        assertEquals(setOf("com.netflix.ninja"), reloaded.favoritePackages.first())
    }
}
