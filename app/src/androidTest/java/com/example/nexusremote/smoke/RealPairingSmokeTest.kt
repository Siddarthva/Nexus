package com.example.nexusremote.smoke

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexusremote.adb.AdbKtTransport
import com.example.nexusremote.adb.AdbTvControlClient
import com.example.nexusremote.data.security.KeystoreCipher
import com.example.nexusremote.data.security.KeystoreCredentialStore
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/**
 * OPTIONAL real-network smoke test: pairs and connects to an Android TV emulator (or TV) using
 * the REAL adb-kt transport.
 *
 * Disabled by default. To enable:
 *  1. Create test-config.properties in the project root (git-ignored) with:
 *       smokeHost=<ip reachable from the phone emulator>
 *       smokePairingPort=<pairing port shown on the TV>
 *       smokeConnectPort=<connect port shown on the TV>
 *       smokePairingCode=<six-digit code shown on the TV>
 *  2. Remove the @Ignore annotation below (locally, do not commit).
 *  3. Run: gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nexusremote.smoke.RealPairingSmokeTest
 *
 * Even when it passes, the result is UNVERIFIED for the Haier TV: an emulator is not the target
 * firmware. It does not replace docs/hardware-test-checklist.md.
 */
@Ignore("Opt-in only: needs a reachable Android TV and test-config.properties. See class KDoc.")
@RunWith(AndroidJUnit4::class)
class RealPairingSmokeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val args = InstrumentationRegistry.getArguments()
    private val alias = "nexus_smoke_${UUID.randomUUID()}"
    private val dir = File(context.cacheDir, "secure-smoke-${UUID.randomUUID()}")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun cleanUp() {
        scope.cancel()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        dir.deleteRecursively()
    }

    @Test
    fun pairConnectSendHomeDisconnect() = runBlocking {
        val host = args.getString("nexus.smokeHost")
        val pairingPort = args.getString("nexus.smokePairingPort")?.toIntOrNull()
        val connectPort = args.getString("nexus.smokeConnectPort")?.toIntOrNull()
        val code = args.getString("nexus.smokePairingCode")
        assumeTrue(
            "test-config.properties values missing: smokeHost, smokePairingPort, smokeConnectPort, smokePairingCode",
            host != null && pairingPort != null && connectPort != null && code != null,
        )

        val keys = KeystoreCredentialStore(context, cipher = KeystoreCipher(alias), dir = dir)
        val client = AdbTvControlClient(AdbKtTransport(), keys, scope)
        val deviceId = UUID.randomUUID().toString()

        val paired = client.pair(PairingRequest(deviceId, host!!, pairingPort!!, code!!.toCharArray()))
        assertTrue("pair failed: $paired", paired.isSuccess)

        val device = TvDevice(id = deviceId, friendlyName = "Smoke", host = host, connectPort = connectPort!!)
        val connected = client.connect(device)
        assertTrue("connect failed: $connected", connected.isSuccess)

        val home = client.sendCommand(TvCommand.HOME)
        assertTrue("HOME failed: $home", home is NexusResult.Success)

        client.disconnect()
    }
}
