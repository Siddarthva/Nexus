package com.example.nexusremote.domain.model

/**
 * A TV found on the local network through mDNS. One entry per IP address, merging the two
 * Wireless Debugging services the TV may advertise:
 * - `_adb-tls-connect._tcp`: present while Wireless debugging is on (gives [connectPort]).
 * - `_adb-tls-pairing._tcp`: present only while the TV shows "Pair device with pairing code"
 *   (gives [pairingPort]).
 *
 * @property mdnsId stable part of the service name (the TV's ADB id), used to recognise a
 *   paired TV even if its IP address changes. Null if the name has an unexpected format.
 */
data class DiscoveredTv(
    val host: String,
    val serviceName: String,
    val mdnsId: String?,
    val pairingPort: Int?,
    val connectPort: Int?,
)
