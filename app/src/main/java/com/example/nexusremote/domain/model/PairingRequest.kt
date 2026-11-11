package com.example.nexusremote.domain.model

/**
 * One pairing attempt. The six-digit [code] is held in a CharArray so it can be wiped with
 * [clear] as soon as the attempt finishes. It is never persisted and never logged:
 * [toString] is redacted on purpose.
 *
 * @property deviceId id of the profile being created; the ADB host key is stored under it.
 */
class PairingRequest(
    val deviceId: String,
    val host: String,
    val pairingPort: Int,
    code: CharArray,
) {
    private val codeChars: CharArray = code.copyOf()

    init {
        code.fill('\u0000')
    }

    /** Returns a copy of the code. Callers must wipe the copy after use. */
    fun codeCopy(): CharArray = codeChars.copyOf()

    /** Overwrites the in-memory code. Safe to call more than once. */
    fun clear() {
        codeChars.fill('\u0000')
    }

    override fun toString(): String = "PairingRequest(host=<redacted>, pairingPort=$pairingPort, code=<redacted>)"
}
