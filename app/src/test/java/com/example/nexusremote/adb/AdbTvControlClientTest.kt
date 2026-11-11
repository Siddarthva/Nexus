package com.example.nexusremote.adb

import com.example.nexusremote.adb.AdbTransportException.Kind
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.fakes.FakeAdbTransport
import com.example.nexusremote.fakes.FakeCredentialStore
import com.example.nexusremote.fakes.FakeScenario
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the REAL AdbTvControlClient state machine with a scripted fake transport.
 * Proves the client's logic; says nothing about adb-kt or a real TV.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdbTvControlClientTest {

    private val device = TestData.livingRoom
    private val transport = FakeAdbTransport()
    private val keys = FakeCredentialStore()

    private fun TestScope.client() = AdbTvControlClient(
        transport = transport,
        hostKeys = keys,
        appScope = backgroundScope,
        io = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.recordStates(client: AdbTvControlClient): MutableList<ConnectionState> {
        val states = mutableListOf<ConnectionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { client.connectionState.toList(states) }
        return states
    }

    private fun request(code: String = "123456") =
        PairingRequest(device.id, device.host, 37000, code.toCharArray())

    // ---- Happy path: Disconnected -> Pairing -> Connecting -> Connected ------------------------

    @Test
    fun fullHappyPathTransitions() = runTest {
        val client = client()
        val states = recordStates(client)

        assertEquals(NexusResult.unit(), client.pair(request()))
        assertEquals(NexusResult.unit(), client.connect(device))
        runCurrent()

        assertEquals(
            listOf(
                ConnectionState.Disconnected,
                ConnectionState.Pairing,
                ConnectionState.Disconnected,
                ConnectionState.Connecting,
                ConnectionState.Connected(device.id),
            ),
            states,
        )
        assertEquals(listOf(device.host to 37000), transport.pairCalls)
        assertEquals(listOf(device.host to device.connectPort), transport.connectCalls)
    }

    @Test
    fun pairingUsesPairingPortAndConnectUsesSeparateConnectPort() = runTest {
        val client = client()
        client.pair(PairingRequest(device.id, device.host, 37111, "123456".toCharArray()))
        client.connect(device.copy(connectPort = 41999))
        assertEquals(37111, transport.pairCalls.single().second)
        assertEquals(41999, transport.connectCalls.single().second)
    }

    @Test
    fun pairingCodeIsPassedThenWipedFromMemory() = runTest {
        val req = request("654321")
        client().pair(req)
        assertEquals("654321", transport.pairCodeCopies.single())
        assertTrue("code bytes must be zeroed", transport.pairCodeArrays.single().all { it == 0.toByte() })
        assertTrue("request copy must be zeroed", req.codeCopy().all { it == '\u0000' })
    }

    // ---- Failure paths into Failed(errorCode) ------------------------------------------------

    @Test
    fun wrongCodeReturnsPairingCodeWrongAndDoesNotLeaveClientInPairing() = runTest {
        transport.script(FakeScenario.WRONG_CODE)
        val client = client()
        val result = client.pair(request())
        assertEquals(ErrorCode.PAIRING_CODE_WRONG, (result as NexusResult.Failure).errorCode)
        assertEquals(ConnectionState.Disconnected, client.connectionState.value)
    }

    @Test
    fun pairingTimeoutMapsToPairingTimeout() = runTest {
        transport.script(FakeScenario.TIMEOUT)
        val result = client().pair(request())
        assertEquals(ErrorCode.PAIRING_TIMEOUT, (result as NexusResult.Failure).errorCode)
    }

    @Test
    fun connectTimeoutEndsInFailedConnectTimeout() = runTest {
        transport.script(FakeScenario.TIMEOUT)
        keys.putKey(device.id)
        val client = client()
        val result = client.connect(device)
        assertEquals(ErrorCode.CONNECT_TIMEOUT, (result as NexusResult.Failure).errorCode)
        val state = client.connectionState.value as ConnectionState.Failed
        assertEquals(ErrorCode.CONNECT_TIMEOUT, state.errorCode)
    }

    @Test
    fun authRevokedForPreviouslyConnectedDevice() = runTest {
        transport.script(FakeScenario.AUTH_REVOKED)
        keys.putKey(device.id)
        val client = client()
        val result = client.connect(device) // device.lastConnectedAt != null
        assertEquals(ErrorCode.AUTH_REVOKED, (result as NexusResult.Failure).errorCode)
        assertEquals(ErrorCode.AUTH_REVOKED, (client.connectionState.value as ConnectionState.Failed).errorCode)
    }

    @Test
    fun notPairedForNeverConnectedDevice() = runTest {
        transport.script(FakeScenario.AUTH_REVOKED)
        keys.putKey(device.id)
        val result = client().connect(device.copy(lastConnectedAt = null))
        assertEquals(ErrorCode.NOT_PAIRED, (result as NexusResult.Failure).errorCode)
    }

    @Test
    fun connectWithoutStoredKeyFailsNotPairedWithoutTouchingNetwork() = runTest {
        val client = client()
        val result = client.connect(device)
        assertEquals(ErrorCode.NOT_PAIRED, (result as NexusResult.Failure).errorCode)
        assertTrue(transport.connectCalls.isEmpty())
    }

    // ---- Bounded retry -----------------------------------------------------------------------

    @Test
    fun transientConnectFailuresRetryWithExponentialBackoffThenSucceed() = runTest {
        keys.putKey(device.id)
        transport.connectFailures = mutableListOf(Kind.TIMEOUT, Kind.UNREACHABLE, null)
        val client = client()
        val start = currentTime
        val result = client.connect(device)
        assertTrue(result.isSuccess)
        assertEquals(3, transport.connectCalls.size)
        assertEquals("500 ms + 1000 ms of backoff", 1_500L, currentTime - start)
    }

    @Test
    fun retriesStopAfterThreeAttempts() = runTest {
        keys.putKey(device.id)
        transport.connectFailures = MutableList(10) { Kind.TIMEOUT }
        val result = client().connect(device)
        assertEquals(ErrorCode.CONNECT_TIMEOUT, (result as NexusResult.Failure).errorCode)
        assertEquals(3, transport.connectCalls.size)
    }

    @Test
    fun authFailureIsNeverRetried() = runTest {
        keys.putKey(device.id)
        transport.connectFailures = MutableList(10) { Kind.NOT_PAIRED }
        client().connect(device)
        assertEquals(1, transport.connectCalls.size)

        transport.connectCalls.clear()
        transport.connectFailures = MutableList(10) { Kind.AUTH_FAILED }
        client().connect(device)
        assertEquals(1, transport.connectCalls.size)
    }

    @Test
    fun refusedPortIsNotRetried() = runTest {
        keys.putKey(device.id)
        transport.connectFailures = MutableList(10) { Kind.REFUSED }
        val result = client().connect(device)
        assertEquals(ErrorCode.WIRELESS_DEBUG_OFF, (result as NexusResult.Failure).errorCode)
        assertEquals(1, transport.connectCalls.size)
    }

    // ---- Commands ----------------------------------------------------------------------------

    @Test
    fun commandSendsExactlyOneKeyevent() = runTest {
        keys.putKey(device.id)
        val client = client()
        client.connect(device)
        assertTrue(client.sendCommand(TvCommand.HOME).isSuccess)
        assertEquals(listOf("input keyevent 3"), transport.shellCommands.map { it.shell })
    }

    @Test
    fun commandWhenNeverConnectedFailsWithoutShell() = runTest {
        val result = client().sendCommand(TvCommand.HOME)
        assertEquals(ErrorCode.COMMAND_FAILED, (result as NexusResult.Failure).errorCode)
        assertTrue(transport.shellCommands.isEmpty())
    }

    @Test
    fun authErrorDuringCommandMovesToFailedAndDoesNotReconnectInALoop() = runTest {
        keys.putKey(device.id)
        val client = client()
        client.connect(device)
        transport.shellHandler = { throw AdbTransportException(Kind.NOT_PAIRED) }

        val first = client.sendCommand(TvCommand.HOME)
        assertEquals(ErrorCode.AUTH_REVOKED, (first as NexusResult.Failure).errorCode)
        assertEquals(ErrorCode.AUTH_REVOKED, (client.connectionState.value as ConnectionState.Failed).errorCode)

        val connectsBefore = transport.connectCalls.size
        val second = client.sendCommand(TvCommand.HOME)
        assertFalse(second.isSuccess)
        assertEquals("no automatic reconnect after auth failure", connectsBefore, transport.connectCalls.size)
    }

    @Test
    fun failedKeyeventIsNotResent() = runTest {
        keys.putKey(device.id)
        val client = client()
        client.connect(device)
        transport.shellHandler = { throw AdbTransportException(Kind.TIMEOUT) }
        val result = client.sendCommand(TvCommand.VOLUME_UP)
        assertFalse(result.isSuccess)
        // Exactly one attempt reached the transport: a key event that may already have
        // reached the TV is never re-sent automatically.
        assertEquals(listOf("input keyevent 24"), transport.shellCommands.map { it.shell })
    }

    @Test
    fun unsupportedTextIsRejectedBeforeReachingTheTransport() = runTest {
        keys.putKey(device.id)
        val client = client()
        client.connect(device)
        val result = client.sendText("rm -rf \$HOME; reboot")
        assertEquals(ErrorCode.INVALID_INPUT, (result as NexusResult.Failure).errorCode)
        assertTrue(transport.shellCommands.isEmpty())
    }

    @Test
    fun idleDropIsDetectedByTheMonitor() = runTest {
        keys.putKey(device.id)
        val client = client()
        client.connect(device)
        assertEquals(ConnectionState.Connected(device.id), client.connectionState.value)
        transport.connected = false
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(ConnectionState.Disconnected, client.connectionState.value)
    }

    @Test
    fun disconnectClosesTransportAndReportsDisconnected() = runTest {
        keys.putKey(device.id)
        val client = client()
        client.connect(device)
        client.disconnect()
        assertFalse(transport.connected)
        assertEquals(ConnectionState.Disconnected, client.connectionState.value)
    }
}
