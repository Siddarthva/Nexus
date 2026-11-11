package com.example.nexusremote.domain.repository

/**
 * Finds a TV's current Wireless Debugging connect port. The port changes when the TV
 * reboots or Wireless Debugging is toggled, so a saved port can go stale.
 */
interface EndpointDiscovery {
    /** Returns the advertised connect port for [host], or null if none was found in time. */
    suspend fun findConnectPort(host: String): Int?
}
