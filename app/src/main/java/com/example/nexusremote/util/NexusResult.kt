package com.example.nexusremote.util

import com.example.nexusremote.domain.model.ErrorCode

/** Result type used across layers. Failures carry a user-safe [ErrorCode]. */
sealed class NexusResult<out T> {
    data class Success<out T>(val value: T) : NexusResult<T>()

    /** [message] is developer-safe (no secrets, no raw payloads). */
    data class Failure(val errorCode: ErrorCode, val message: String) : NexusResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    inline fun <R> map(transform: (T) -> R): NexusResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun onFailure(action: (Failure) -> Unit): NexusResult<T> {
        if (this is Failure) action(this)
        return this
    }

    inline fun onSuccess(action: (T) -> Unit): NexusResult<T> {
        if (this is Success) action(value)
        return this
    }

    companion object {
        fun unit(): NexusResult<Unit> = Success(Unit)
    }
}
