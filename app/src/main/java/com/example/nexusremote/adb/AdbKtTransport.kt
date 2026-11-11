package com.example.nexusremote.adb

import com.example.nexusremote.adb.AdbTransportException.Kind
import io.github.rhythmcache.adb.AdbAuth
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.AdbKeyProvider
import io.github.rhythmcache.adb.pairing.ConnectionRole
import io.github.rhythmcache.adb.pairing.PairingConnection
import io.github.rhythmcache.adb.pairing.PeerInfoBuilder
import io.github.rhythmcache.adb.pairing.buildPairingIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPair

/**
 * [AdbTransport] backed by adb-kt (com.github.rhythmcache:adb-kt, Apache-2.0).
 * See docs/adr-001-adb-library.md.
 *
 * Keys: the Keystore-protected key pair is handed to adb-kt through an in-memory
 * [AdbKeyProvider]; adb-kt's FileKeyProvider (which writes keys to plain files) is never used.
 */
// UNVERIFIED-ON-HARDWARE: this whole adapter (SPAKE2 pairing, TLS connect, shell v2 over
// TLS) compiles against adb-kt v1.0.13 but has not been run against a real TV.
internal class AdbKtTransport(
    private val connectTimeoutMs: Int = 5_000,
    private val pairingReadTimeoutMs: Int = 15_000,
    private val handshakeTimeoutMs: Long = 15_000,
    private val shellTimeoutMs: Long = 10_000,
) : AdbTransport {

    @Volatile
    private var client: AdbClient? = null

    override val isConnected: Boolean
        get() = client?.isClosed == false

    override suspend fun pair(host: String, pairingPort: Int, code: ByteArray, keyPair: KeyPair) =
        withContext(Dispatchers.IO) {
            // UNVERIFIED-ON-HARDWARE: we open the pairing socket ourselves (instead of
            // AdbClient.pairTls) so we can set connect/read timeouts and pass the code as
            // bytes that the caller wipes, rather than as an immutable String.
            val socket = Socket()
            try {
                guarded(pairing = true) {
                    val provider = keyProviderFor(keyPair)
                    socket.connect(InetSocketAddress(host, pairingPort), connectTimeoutMs)
                    socket.soTimeout = pairingReadTimeoutMs
                    socket.tcpNoDelay = true
                    val identity = buildPairingIdentity(provider)
                    val peerInfo = PeerInfoBuilder.forOurKeyProvider(provider)
                    // PairingConnection.run closes the socket on success and failure.
                    val result = PairingConnection.run(socket, ConnectionRole.CLIENT, code, peerInfo, identity)
                    result.getOrElse { throw AdbKtErrorClassifier.classify(it, pairing = true) }
                }
            } finally {
                runCatching { socket.close() }
            }
            Unit
        }

    override suspend fun connect(host: String, connectPort: Int, keyPair: KeyPair) =
        withContext(Dispatchers.IO) {
            close()
            guarded(pairing = false) {
                // adb-kt opens its TLS socket without a connect timeout, so first probe the port
                // with a bounded timeout to fail fast when the TV is off or the port is stale.
                // UNVERIFIED-ON-HARDWARE: assumes a bare TCP probe is harmless to adbd.
                Socket().use { it.connect(InetSocketAddress(host, connectPort), connectTimeoutMs) }
                val provider = keyProviderFor(keyPair)
                client = withTimeout(handshakeTimeoutMs + connectTimeoutMs) {
                    AdbClient.connectTls(
                        host = host,
                        port = connectPort,
                        keyProvider = provider,
                        handshakeTimeoutMs = handshakeTimeoutMs,
                    )
                }
            }
            Unit
        }

    override suspend fun shell(command: AdbShellCommand): ShellOutput = withContext(Dispatchers.IO) {
        val active = client?.takeIf { !it.isClosed } ?: throw AdbTransportException(Kind.CLOSED)
        guarded(pairing = false) {
            val result = withTimeout(shellTimeoutMs) { active.shell(command.shell) }
            ShellOutput(result.exitCode, result.stdoutText, result.stderrText)
        }
    }

    /**
     * Runs a call into adb-kt and converts EVERY failure, including Errors such as
     * NoSuchFieldError / LinkageError, into an [AdbTransportException], so nothing from the
     * library can reach an uncaught-exception handler. Coroutine cancellation is re-thrown
     * unchanged (except our own timeouts, which become TIMEOUT).
     */
    private inline fun <T> guarded(pairing: Boolean, block: () -> T): T =
        try {
            block()
        } catch (e: TimeoutCancellationException) {
            throw AdbTransportException(Kind.TIMEOUT, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AdbTransportException) {
            throw e
        } catch (t: Throwable) {
            throw AdbKtErrorClassifier.classify(t, pairing)
        }

    override fun close() {
        val current = client
        client = null
        runCatching { current?.close() }
    }

    /**
     * Re-creates the key pair through adb-kt's BouncyCastle-backed parser so its X.509 and
     * signing code receive key objects it fully supports. The DER copy is wiped afterwards.
     */
    // UNVERIFIED-ON-HARDWARE: that a key generated by the platform provider, stored in the
    // Keystore-backed store and re-parsed here produces a certificate and ADB public key that
    // adbd accepts for both pairing and later TLS connects.
    private fun keyProviderFor(keyPair: KeyPair): AdbKeyProvider {
        val der = keyPair.private.encoded
        try {
            val bcPrivate = AdbAuth.parsePrivateKey(der) ?: throw AdbTransportException(Kind.AUTH_FAILED)
            return AdbKeyProvider.from(AdbAuth.deriveKeyPair(bcPrivate), IDENTITY_COMMENT)
        } finally {
            der.fill(0)
        }
    }

    private companion object {
        /** Shown on the TV's list of authorized debugging hosts. */
        const val IDENTITY_COMMENT = "nexus-tv-remote"
    }
}
