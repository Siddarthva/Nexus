package com.example.nexusremote.adb

import com.example.nexusremote.adb.AdbTransportException.Kind
import com.example.nexusremote.domain.model.ErrorCode

/** Maps transport failures to user-safe [ErrorCode]s, depending on what was being attempted. */
internal object AdbErrorMapper {

    enum class Operation { PAIR, CONNECT, COMMAND }

    // UNVERIFIED-ON-HARDWARE: the mapping of TV-side failures (closed pairing dialog,
    // Wireless Debugging switched off, revoked authorization) to transport kinds is inferred
    // from the ADB protocol and library source, not observed on a real Haier/Google TV.
    fun map(kind: Kind, operation: Operation, previouslyConnected: Boolean): ErrorCode = when (kind) {
        Kind.WRONG_CODE -> ErrorCode.PAIRING_CODE_WRONG
        Kind.TIMEOUT -> if (operation == Operation.PAIR) ErrorCode.PAIRING_TIMEOUT else ErrorCode.CONNECT_TIMEOUT
        Kind.REFUSED -> when (operation) {
            // The pairing port only listens while the "Pair device with pairing code" dialog is open.
            Operation.PAIR -> ErrorCode.PAIRING_EXPIRED
            else -> ErrorCode.WIRELESS_DEBUG_OFF
        }
        Kind.UNREACHABLE -> ErrorCode.NETWORK_UNREACHABLE
        Kind.NOT_PAIRED, Kind.AUTH_FAILED ->
            if (previouslyConnected) ErrorCode.AUTH_REVOKED else ErrorCode.NOT_PAIRED
        Kind.CLOSED -> if (operation == Operation.COMMAND) ErrorCode.COMMAND_FAILED else ErrorCode.CONNECT_TIMEOUT
        Kind.PROTOCOL -> if (operation == Operation.PAIR) ErrorCode.PAIRING_EXPIRED else ErrorCode.UNSUPPORTED
        Kind.INCOMPATIBLE -> ErrorCode.UNSUPPORTED
        Kind.UNKNOWN -> ErrorCode.UNKNOWN
    }

    /** Auth failures must never be retried automatically; the user has to re-pair. */
    fun isRetryable(kind: Kind): Boolean = when (kind) {
        Kind.TIMEOUT, Kind.CLOSED, Kind.UNREACHABLE -> true
        else -> false
    }
}
