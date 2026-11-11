package com.example.nexusremote.util

import java.util.UUID

/** Strict validators for every value that reaches the network or the TV shell. */
object InputValidators {

    private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

    /** Letters, digits and underscores in dot-separated segments; at least two segments. */
    private val PACKAGE_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

    private val PAIRING_CODE = Regex("""^\d{6}$""")

    const val MAX_PACKAGE_NAME_LENGTH = 255
    const val MAX_FRIENDLY_NAME_LENGTH = 40

    /** IPv4 dotted-quad only. Hostnames are rejected so no DNS lookup can leave the LAN. */
    fun isValidIpv4(host: String): Boolean = IPV4.matches(host.trim())

    fun isValidPort(port: Int?): Boolean = port != null && port in 1..65535

    fun parsePort(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 5 || !trimmed.all { it.isDigit() }) return null
        return trimmed.toIntOrNull()?.takeIf { isValidPort(it) }
    }

    fun isValidPairingCode(code: CharSequence): Boolean = PAIRING_CODE.matches(code)

    fun isValidPackageName(name: String): Boolean =
        name.length <= MAX_PACKAGE_NAME_LENGTH && PACKAGE_NAME.matches(name)

    fun isValidFriendlyName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed.length <= MAX_FRIENDLY_NAME_LENGTH
    }

    /** Device ids are used as file names for key storage, so only canonical UUIDs are accepted. */
    fun isValidDeviceId(id: String): Boolean =
        runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false)
}
