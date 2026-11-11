package com.example.nexusremote.domain.model

/** Stable, user-safe error codes. Safe to log and to show to users. */
enum class ErrorCode {
    INVALID_INPUT,
    PAIRING_CODE_WRONG,
    PAIRING_TIMEOUT,
    PAIRING_EXPIRED,
    NOT_PAIRED,
    AUTH_REVOKED,
    WIRELESS_DEBUG_OFF,
    NETWORK_UNREACHABLE,
    CONNECT_TIMEOUT,
    COMMAND_FAILED,
    UNSUPPORTED,
    UNKNOWN,
}
