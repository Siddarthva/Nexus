package com.example.nexusremote.domain.model

/**
 * A saved TV profile. Contains no secrets.
 *
 * @property id random UUID string; also keys the device's ADB host key in the secure store.
 * @property pairingPort temporary pairing port. Kept in memory only and never persisted.
 * @property connectPort Wireless Debugging connect port. Distinct from the pairing port and
 *   may change after the TV reboots or Wireless Debugging is toggled.
 * @property lastConnectedAt epoch millis of the last successful connection, or null.
 * @property mdnsId the TV's ADB id from its mDNS service name, when it was found by discovery.
 *   Lets the app recognise this TV even if its IP address changes.
 */
data class TvDevice(
    val id: String,
    val friendlyName: String,
    val host: String,
    val pairingPort: Int? = null,
    val connectPort: Int,
    val lastConnectedAt: Long? = null,
    val manufacturerHint: String? = null,
    val mdnsId: String? = null,
)
