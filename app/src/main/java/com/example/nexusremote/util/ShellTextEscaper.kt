package com.example.nexusremote.util

/**
 * Converts user text into ONE safely quoted argument for `input text`.
 *
 * Supported set: ASCII letters, digits, space and the punctuation in [ALLOWED_PUNCTUATION].
 * Anything else (including `%`, backslash, backtick, `$`, `;`, `|`, `<`, `>`, newlines,
 * emoji and non-Latin scripts) is rejected before any escaping happens.
 *
 * Escaping, applied per character:
 *  - The whole argument is wrapped in single quotes, so the device's `sh -c` performs
 *    no expansion or word splitting on it.
 *  - `'` becomes `'\''` (close quote, escaped quote, reopen quote).
 *  - Space becomes `%s`, the `input text` encoding for a space. `%` itself is not in the
 *    supported set, so `%s` can never be produced by user input.
 *
 * Examples:
 *  - `hello world` -> `'hello%sworld'`
 *  - `it's`        -> `'it'\''s'`
 *  - `a&b=c`       -> `'a&b=c'`
 */
object ShellTextEscaper {

    const val ALLOWED_PUNCTUATION = ".,!?-_@:/'\"()&+=#*"
    const val MAX_LENGTH = 300

    sealed interface Outcome {
        data class Escaped(val argument: String) : Outcome
        data object Empty : Outcome
        data object TooLong : Outcome

        /** [rejected] holds each unsupported character once, in first-seen order. */
        data class Unsupported(val rejected: List<Char>) : Outcome
    }

    fun isSupported(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == ' ' || c in ALLOWED_PUNCTUATION

    fun escape(text: String): Outcome {
        if (text.isEmpty()) return Outcome.Empty
        if (text.length > MAX_LENGTH) return Outcome.TooLong
        val rejected = text.filterNot(::isSupported).toList().distinct()
        if (rejected.isNotEmpty()) return Outcome.Unsupported(rejected)

        val out = StringBuilder(text.length + 8)
        out.append('\'')
        for (c in text) {
            when (c) {
                '\'' -> out.append("'\\''")
                ' ' -> out.append("%s")
                else -> out.append(c)
            }
        }
        out.append('\'')
        return Outcome.Escaped(out.toString())
    }
}
