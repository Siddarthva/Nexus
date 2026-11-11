package com.example.nexusremote.domain.model

/** Connection lifecycle of the active TV, exposed as a StateFlow by the TV client. */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Pairing : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val deviceId: String) : ConnectionState

    /** [message] is a short developer-safe description; it never contains secrets. */
    data class Failed(val errorCode: ErrorCode, val message: String) : ConnectionState
}
