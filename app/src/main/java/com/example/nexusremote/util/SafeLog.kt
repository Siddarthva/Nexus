package com.example.nexusremote.util

import android.util.Log
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode

/**
 * The only logger used by the app. Its API accepts just state names, command names and
 * error codes, which makes it hard to log pairing codes, keys, hosts or raw payloads by accident.
 */
object SafeLog {
    private const val TAG = "Nexus"

    fun state(component: String, state: ConnectionState) {
        val label = when (state) {
            ConnectionState.Disconnected -> "Disconnected"
            ConnectionState.Pairing -> "Pairing"
            ConnectionState.Connecting -> "Connecting"
            is ConnectionState.Connected -> "Connected"
            is ConnectionState.Failed -> "Failed(${state.errorCode})"
        }
        Log.i(TAG, "$component state -> $label")
    }

    fun error(component: String, operation: String, code: ErrorCode, throwable: Throwable? = null) {
        // Only the exception TYPE is logged; messages may contain hosts or payload fragments.
        val type = throwable?.javaClass?.simpleName ?: "none"
        Log.w(TAG, "$component $operation failed: $code ($type)")
    }

    fun event(component: String, event: String) {
        Log.d(TAG, "$component $event")
    }
}
