package com.example.nexusremote.adb

import com.example.nexusremote.adb.AdbTransportException.Kind
import io.github.rhythmcache.adb.AdbException
import io.github.rhythmcache.adb.pairing.PairingException
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Translates adb-kt and java.net exceptions into library-independent [Kind]s.
 * Walks the cause chain; anything unrecognised becomes [Kind.UNKNOWN]. The resulting
 * [AdbTransportException] message is only the kind name, so raw exception text (which may
 * contain hosts or payload fragments) never reaches the UI or logs.
 */
internal object AdbKtErrorClassifier {

    fun classify(error: Throwable, pairing: Boolean): AdbTransportException {
        var current: Throwable? = error
        while (current != null) {
            kindOf(current, pairing)?.let { return AdbTransportException(it, error) }
            current = current.cause
        }
        return AdbTransportException(Kind.UNKNOWN, error)
    }

    private fun kindOf(t: Throwable, pairing: Boolean): Kind? = when (t) {
        is AdbTransportException -> t.kind
        is PairingException ->
            if (t.message?.contains("wrong pairing code", ignoreCase = true) == true) Kind.WRONG_CODE else Kind.PROTOCOL
        // UNVERIFIED-ON-HARDWARE: adbd reads our PeerInfo first and drops the connection when
        // it cannot decrypt it, so a wrong code may surface as EOF instead of a decrypt error.
        is EOFException -> if (pairing) Kind.WRONG_CODE else Kind.CLOSED
        is SocketTimeoutException -> Kind.TIMEOUT
        is NoRouteToHostException, is UnknownHostException -> Kind.UNREACHABLE
        is ConnectException ->
            if (t.message?.contains("refused", ignoreCase = true) == true) Kind.REFUSED else Kind.UNREACHABLE
        // UNVERIFIED-ON-HARDWARE: that a revoked or unknown host key surfaces as a TLS
        // certificate-rejection alert (AdbException.NotPaired) on the target TV.
        is AdbException.NotPaired -> Kind.NOT_PAIRED
        is AdbException.Authentication -> Kind.AUTH_FAILED
        is AdbException.Timeout -> Kind.TIMEOUT
        is AdbException.StreamClosed -> Kind.CLOSED
        is AdbException.Protocol -> Kind.PROTOCOL
        is AdbException.RemoteFailure -> Kind.PROTOCOL
        // The library (or one of its dependencies) uses an API this Android version lacks, or a
        // class failed to initialise. Never crash the app for this; report UNSUPPORTED.
        is LinkageError, is ExceptionInInitializerError -> Kind.INCOMPATIBLE
        else -> null
    }
}
