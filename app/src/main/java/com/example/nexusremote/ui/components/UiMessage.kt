package com.example.nexusremote.ui.components

import android.content.Context
import androidx.annotation.StringRes
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.ErrorCode

/** One-off message for a snackbar. Holds resource ids, never pre-built text, so VMs stay context-free. */
sealed interface UiMessage {
    data class Error(val code: ErrorCode) : UiMessage
    data class Text(@StringRes val res: Int, val args: List<Any> = emptyList()) : UiMessage

    /** A message whose second argument is a localized error description. */
    data class TextWithError(@StringRes val res: Int, val subject: String, val code: ErrorCode) : UiMessage
}

@StringRes
fun ErrorCode.messageRes(): Int = when (this) {
    ErrorCode.INVALID_INPUT -> R.string.error_invalid_input
    ErrorCode.PAIRING_CODE_WRONG -> R.string.error_pairing_code_wrong
    ErrorCode.PAIRING_TIMEOUT -> R.string.error_pairing_timeout
    ErrorCode.PAIRING_EXPIRED -> R.string.error_pairing_expired
    ErrorCode.NOT_PAIRED -> R.string.error_not_paired
    ErrorCode.AUTH_REVOKED -> R.string.error_auth_revoked
    ErrorCode.WIRELESS_DEBUG_OFF -> R.string.error_wireless_debug_off
    ErrorCode.NETWORK_UNREACHABLE -> R.string.error_network_unreachable
    ErrorCode.CONNECT_TIMEOUT -> R.string.error_connect_timeout
    ErrorCode.COMMAND_FAILED -> R.string.error_command_failed
    ErrorCode.UNSUPPORTED -> R.string.error_unsupported
    ErrorCode.UNKNOWN -> R.string.error_unknown
}

/** Localized error text including the stable code, which helps when reporting problems. */
fun ErrorCode.describe(context: Context): String =
    context.getString(R.string.error_with_code, context.getString(messageRes()), name)

fun UiMessage.resolve(context: Context): String = when (this) {
    is UiMessage.Error -> code.describe(context)
    is UiMessage.Text -> context.getString(res, *args.toTypedArray())
    is UiMessage.TextWithError -> context.getString(res, subject, code.describe(context))
}
