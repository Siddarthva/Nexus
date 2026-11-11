package com.example.nexusremote.fakes

import com.example.nexusremote.adb.AdbShellCommand
import com.example.nexusremote.adb.AdbTransport
import com.example.nexusremote.adb.AdbTransportException
import com.example.nexusremote.adb.AdbTransportException.Kind
import com.example.nexusremote.adb.ShellOutput
import java.security.KeyPair
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scriptable [AdbTransport] used to drive the real AdbTvControlClient without a network.
 * Failures are thrown as [AdbTransportException] exactly like the real adapter does.
 */
internal class FakeAdbTransport : AdbTransport {

    @Volatile
    var connected = false
    override val isConnected: Boolean get() = connected

    val pairCalls = CopyOnWriteArrayList<Pair<String, Int>>()

    /** The pairing-code arrays received, kept by reference to check they were wiped afterwards. */
    val pairCodeArrays = CopyOnWriteArrayList<ByteArray>()

    /** A copy of each code as received, taken before the client wipes the original. */
    val pairCodeCopies = CopyOnWriteArrayList<String>()
    val connectCalls = CopyOnWriteArrayList<Pair<String, Int>>()
    val shellCommands = CopyOnWriteArrayList<AdbShellCommand>()
    var closeCalls = 0
        private set

    /** Kind to throw from pair(), or null for success. */
    var pairFailure: Kind? = null

    /** Failure for each successive connect attempt; null (or an exhausted list) means success. */
    var connectFailures: MutableList<Kind?> = mutableListOf()

    /** Shell behaviour. Throw an AdbTransportException to simulate a transport failure. */
    var shellHandler: (AdbShellCommand) -> ShellOutput = { ShellOutput(0, "", "") }

    fun script(scenario: FakeScenario) {
        when (scenario) {
            FakeScenario.SUCCESS -> {
                pairFailure = null
                connectFailures = mutableListOf()
            }
            FakeScenario.TIMEOUT -> {
                pairFailure = Kind.TIMEOUT
                connectFailures = MutableList(10) { Kind.TIMEOUT }
            }
            FakeScenario.WRONG_CODE -> pairFailure = Kind.WRONG_CODE
            FakeScenario.AUTH_REVOKED -> {
                connectFailures = MutableList(10) { Kind.NOT_PAIRED }
                shellHandler = { throw AdbTransportException(Kind.NOT_PAIRED) }
            }
        }
    }

    override suspend fun pair(host: String, pairingPort: Int, code: ByteArray, keyPair: KeyPair) {
        pairCalls.add(host to pairingPort)
        pairCodeArrays.add(code)
        pairCodeCopies.add(String(code, Charsets.US_ASCII))
        pairFailure?.let { throw AdbTransportException(it) }
    }

    override suspend fun connect(host: String, connectPort: Int, keyPair: KeyPair) {
        connectCalls.add(host to connectPort)
        val failure = connectFailures.removeFirstOrNull()
        if (failure != null) {
            connected = false
            throw AdbTransportException(failure)
        }
        connected = true
    }

    override suspend fun shell(command: AdbShellCommand): ShellOutput {
        if (!connected) throw AdbTransportException(Kind.CLOSED)
        shellCommands.add(command)
        return shellHandler(command)
    }

    override fun close() {
        closeCalls++
        connected = false
    }
}
