package com.example.nexusremote.adb

import java.security.KeyPair

/**
 * Minimal ADB host operations NEXUS needs. [AdbTvControlClient] depends only on this
 * interface, so the underlying ADB library can be replaced without touching the client.
 *
 * All functions may block on network I/O; callers run them on Dispatchers.IO.
 * Failures are reported as [AdbTransportException] with a library-independent [AdbTransportException.Kind].
 */
internal interface AdbTransport {
    val isConnected: Boolean

    /**
     * Wireless Debugging pairing against the TV's temporary pairing endpoint.
     * [code] holds the ASCII digits; the implementation must not copy it into a String.
     */
    suspend fun pair(host: String, pairingPort: Int, code: ByteArray, keyPair: KeyPair)

    /** Opens an authenticated TLS ADB connection to the TV's separate connect endpoint. */
    suspend fun connect(host: String, connectPort: Int, keyPair: KeyPair)

    /** Runs one allowlisted shell command and waits for it to finish. */
    suspend fun shell(command: AdbShellCommand): ShellOutput

    fun close()
}

internal data class ShellOutput(val exitCode: Int, val stdout: String, val stderr: String)

internal class AdbTransportException(val kind: Kind, cause: Throwable? = null) :
    Exception(kind.name, cause) {

    enum class Kind {
        /** Pairing handshake could not decrypt the TV's reply: wrong six-digit code. */
        WRONG_CODE,
        TIMEOUT,

        /** TCP connection refused: port closed (pairing dialog closed / Wireless Debugging off). */
        REFUSED,
        UNREACHABLE,

        /** TV rejected our host key: not paired, or authorization was revoked. */
        NOT_PAIRED,
        AUTH_FAILED,
        CLOSED,
        PROTOCOL,

        /**
         * The ADB library could not run on this phone (LinkageError such as NoSuchFieldError /
         * NoSuchMethodError / NoClassDefFoundError, or a failed static initialiser).
         */
        INCOMPATIBLE,
        UNKNOWN,
    }
}
