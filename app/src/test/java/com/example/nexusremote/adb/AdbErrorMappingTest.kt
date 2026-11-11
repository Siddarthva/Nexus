package com.example.nexusremote.adb

import com.example.nexusremote.adb.AdbErrorMapper.Operation
import com.example.nexusremote.adb.AdbTransportException.Kind
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.fakes.FakeCredentialStore
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.util.NexusResult
import io.github.rhythmcache.adb.AdbException
import io.github.rhythmcache.adb.pairing.PairingException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyPair

/**
 * Library exception -> transport Kind -> user-facing ErrorCode.
 * Exception instances are constructed directly; no network is involved.
 */
class AdbErrorMappingTest {

    private fun kind(t: Throwable, pairing: Boolean) = AdbKtErrorClassifier.classify(t, pairing).kind

    // ---- Stage 1: library / java.net exceptions -> Kind ---------------------------------------

    @Test
    fun pairingExceptionsAreClassified() {
        assertEquals(Kind.WRONG_CODE, kind(PairingException("Failed to decrypt peer's PeerInfo -- likely wrong pairing code"), true))
        assertEquals(Kind.PROTOCOL, kind(PairingException("Expected SPAKE2_MSG, got type=9"), true))
        assertEquals(Kind.WRONG_CODE, kind(EOFException(), pairing = true))
        assertEquals(Kind.CLOSED, kind(EOFException(), pairing = false))
    }

    @Test
    fun socketExceptionsAreClassified() {
        assertEquals(Kind.TIMEOUT, kind(SocketTimeoutException("connect timed out"), false))
        assertEquals(Kind.REFUSED, kind(ConnectException("failed to connect: ECONNREFUSED (Connection refused)"), false))
        assertEquals(Kind.UNREACHABLE, kind(ConnectException("ENETUNREACH (Network is unreachable)"), false))
        assertEquals(Kind.UNREACHABLE, kind(NoRouteToHostException("No route to host"), false))
        assertEquals(Kind.UNREACHABLE, kind(UnknownHostException("tv"), false))
    }

    @Test
    fun adbKtExceptionsAreClassified() {
        assertEquals(Kind.NOT_PAIRED, kind(AdbException.NotPaired(), false))
        assertEquals(Kind.AUTH_FAILED, kind(AdbException.Authentication("rejected"), false))
        assertEquals(Kind.TIMEOUT, kind(AdbException.Timeout(), false))
        assertEquals(Kind.CLOSED, kind(AdbException.StreamClosed(), false))
        assertEquals(Kind.PROTOCOL, kind(AdbException.Protocol("bad packet"), false))
        assertEquals(Kind.PROTOCOL, kind(AdbException.RemoteFailure("FAIL"), false))
    }

    @Test
    fun causeChainIsWalked() {
        val wrapped = AdbException.Transport("TLS handshake failed", SocketTimeoutException())
        assertEquals(Kind.TIMEOUT, kind(wrapped, false))
        val deep = IOException("outer", RuntimeException("mid", AdbException.NotPaired()))
        assertEquals(Kind.NOT_PAIRED, kind(deep, false))
    }

    @Test
    fun unknownExceptionsFallBackToUnknownWithoutRawText() {
        val secret = "code 123456 host 192.0.2.10 key=MIIEvQIBADANBg"
        val result = AdbKtErrorClassifier.classify(IllegalStateException(secret), pairing = false)
        assertEquals(Kind.UNKNOWN, result.kind)
        assertEquals("UNKNOWN", result.message)
        assertFalse(result.message!!.contains("123456"))
        assertEquals(ErrorCode.UNKNOWN, AdbErrorMapper.map(Kind.UNKNOWN, Operation.CONNECT, true))
    }

    // ---- Stage 2: Kind -> ErrorCode ----------------------------------------------------------

    @Test
    fun kindToErrorCodeForPairing() {
        val expected = mapOf(
            Kind.WRONG_CODE to ErrorCode.PAIRING_CODE_WRONG,
            Kind.TIMEOUT to ErrorCode.PAIRING_TIMEOUT,
            Kind.REFUSED to ErrorCode.PAIRING_EXPIRED,
            Kind.UNREACHABLE to ErrorCode.NETWORK_UNREACHABLE,
            Kind.NOT_PAIRED to ErrorCode.NOT_PAIRED,
            Kind.AUTH_FAILED to ErrorCode.NOT_PAIRED,
            Kind.CLOSED to ErrorCode.CONNECT_TIMEOUT,
            Kind.PROTOCOL to ErrorCode.PAIRING_EXPIRED,
            Kind.UNKNOWN to ErrorCode.UNKNOWN,
        )
        assertEquals(Kind.entries.toSet(), expected.keys)
        expected.forEach { (k, code) -> assertEquals("PAIR $k", code, AdbErrorMapper.map(k, Operation.PAIR, false)) }
    }

    @Test
    fun kindToErrorCodeForConnect() {
        assertEquals(ErrorCode.CONNECT_TIMEOUT, AdbErrorMapper.map(Kind.TIMEOUT, Operation.CONNECT, true))
        assertEquals(ErrorCode.WIRELESS_DEBUG_OFF, AdbErrorMapper.map(Kind.REFUSED, Operation.CONNECT, true))
        assertEquals(ErrorCode.NETWORK_UNREACHABLE, AdbErrorMapper.map(Kind.UNREACHABLE, Operation.CONNECT, true))
        assertEquals(ErrorCode.AUTH_REVOKED, AdbErrorMapper.map(Kind.NOT_PAIRED, Operation.CONNECT, previouslyConnected = true))
        assertEquals(ErrorCode.NOT_PAIRED, AdbErrorMapper.map(Kind.NOT_PAIRED, Operation.CONNECT, previouslyConnected = false))
        assertEquals(ErrorCode.AUTH_REVOKED, AdbErrorMapper.map(Kind.AUTH_FAILED, Operation.CONNECT, previouslyConnected = true))
        assertEquals(ErrorCode.UNSUPPORTED, AdbErrorMapper.map(Kind.PROTOCOL, Operation.CONNECT, true))
        assertEquals(ErrorCode.CONNECT_TIMEOUT, AdbErrorMapper.map(Kind.CLOSED, Operation.CONNECT, true))
    }

    @Test
    fun kindToErrorCodeForCommands() {
        assertEquals(ErrorCode.COMMAND_FAILED, AdbErrorMapper.map(Kind.CLOSED, Operation.COMMAND, true))
        assertEquals(ErrorCode.CONNECT_TIMEOUT, AdbErrorMapper.map(Kind.TIMEOUT, Operation.COMMAND, true))
        assertEquals(ErrorCode.AUTH_REVOKED, AdbErrorMapper.map(Kind.NOT_PAIRED, Operation.COMMAND, true))
    }

    @Test
    fun onlyTransientKindsAreRetryable() {
        val retryable = Kind.entries.filter(AdbErrorMapper::isRetryable).toSet()
        assertEquals(setOf(Kind.TIMEOUT, Kind.CLOSED, Kind.UNREACHABLE), retryable)
        assertFalse(AdbErrorMapper.isRetryable(Kind.NOT_PAIRED))
        assertFalse(AdbErrorMapper.isRetryable(Kind.AUTH_FAILED))
        assertFalse(AdbErrorMapper.isRetryable(Kind.WRONG_CODE))
    }

    // ---- End to end through the client: raw text never reaches results or state ---------------

    @Test
    fun rawExceptionTextNeverReachesResultOrState() = runTest {
        val secret = "pairing code 987654 at 192.0.2.10"
        val transport = object : AdbTransport {
            override val isConnected = false
            override suspend fun pair(host: String, pairingPort: Int, code: ByteArray, keyPair: KeyPair) =
                throw AdbKtErrorClassifier.classify(RuntimeException(secret), pairing = true)
            override suspend fun connect(host: String, connectPort: Int, keyPair: KeyPair) =
                throw AdbKtErrorClassifier.classify(RuntimeException(secret), pairing = false)
            override suspend fun shell(command: AdbShellCommand): ShellOutput = throw IllegalStateException()
            override fun close() = Unit
        }
        val keys = FakeCredentialStore().apply { putKey(TestData.LIVING_ROOM_ID) }
        val client = AdbTvControlClient(transport, keys, backgroundScope, StandardTestDispatcher(testScheduler))

        val result = client.connect(TestData.livingRoom) as NexusResult.Failure
        assertEquals(ErrorCode.UNKNOWN, result.errorCode)
        assertFalse(result.message.contains("987654"))
        assertFalse(result.message.contains("192.0.2.10"))
        val state = client.connectionState.value as ConnectionState.Failed
        assertFalse(state.message.contains("987654"))
        assertTrue(state.message.isNotBlank())
    }
}
