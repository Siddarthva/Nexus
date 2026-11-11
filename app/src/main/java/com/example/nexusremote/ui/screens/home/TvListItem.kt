package com.example.nexusremote.ui.screens.home

import com.example.nexusremote.domain.model.DiscoveredTv
import com.example.nexusremote.domain.model.TvDevice

/**
 * One row in the TV list: a TV seen on the network, a paired TV, or both.
 *
 * @property name saved friendly name, or null for an unpaired TV (the UI then shows
 *   "Android TV (<idHint>)").
 * @property deviceId set when this TV is paired (saved profile with a stored key).
 * @property onNetwork true when the TV is currently advertised on the network.
 */
data class TvListItem(
    val key: String,
    val name: String?,
    val idHint: String?,
    val host: String,
    val deviceId: String?,
    val onNetwork: Boolean,
    val pairingPort: Int?,
    val connectPort: Int?,
    val mdnsId: String?,
) {
    val paired: Boolean get() = deviceId != null
}

/**
 * Merges discovery results with paired TVs. A discovered TV matches a paired one by mDNS id
 * first (survives IP changes), then by IP. Paired TVs that are not visible on the network are
 * listed after the discovered ones so they can still be tried.
 */
fun buildTvList(discovered: List<DiscoveredTv>, paired: List<TvDevice>): List<TvListItem> {
    val matchedIds = mutableSetOf<String>()
    val fromNetwork = discovered.map { tv ->
        val match = paired.firstOrNull { it.mdnsId != null && it.mdnsId == tv.mdnsId }
            ?: paired.firstOrNull { it.host == tv.host }
        match?.let { matchedIds += it.id }
        TvListItem(
            key = "net:${tv.host}",
            name = match?.friendlyName,
            idHint = tv.mdnsId ?: tv.serviceName,
            host = tv.host,
            deviceId = match?.id,
            onNetwork = true,
            pairingPort = tv.pairingPort,
            connectPort = tv.connectPort,
            mdnsId = tv.mdnsId,
        )
    }
    val offline = paired.filterNot { it.id in matchedIds }.map { device ->
        TvListItem(
            key = "saved:${device.id}",
            name = device.friendlyName,
            idHint = null,
            host = device.host,
            deviceId = device.id,
            onNetwork = false,
            pairingPort = null,
            connectPort = device.connectPort,
            mdnsId = device.mdnsId,
        )
    }
    return fromNetwork.sortedBy { it.name == null } + offline
}
