package com.example.nexusremote.data.local

import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.util.InputValidators
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Encodes TV profiles as a JSON array string for DataStore.
 * Persisted fields: id, name, host, connectPort, lastConnectedAt, manufacturerHint, mdnsId.
 * The pairing port is deliberately never written.
 */
internal object DeviceProfileCodec {
    private const val ID = "id"
    private const val NAME = "name"
    private const val HOST = "host"
    private const val CONNECT_PORT = "connectPort"
    private const val LAST_CONNECTED = "lastConnectedAt"
    private const val MANUFACTURER = "manufacturerHint"
    private const val MDNS_ID = "mdnsId"

    fun encode(devices: List<TvDevice>): String {
        val array = JSONArray()
        devices.forEach { d ->
            array.put(
                JSONObject()
                    .put(ID, d.id)
                    .put(NAME, d.friendlyName)
                    .put(HOST, d.host)
                    .put(CONNECT_PORT, d.connectPort)
                    .apply {
                        d.lastConnectedAt?.let { put(LAST_CONNECTED, it) }
                        d.manufacturerHint?.let { put(MANUFACTURER, it) }
                        d.mdnsId?.let { put(MDNS_ID, it) }
                    },
            )
        }
        return array.toString()
    }

    /** Invalid or corrupted entries are skipped rather than crashing the app. */
    fun decode(json: String?): List<TvDevice> {
        if (json.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(json)
        } catch (_: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val device = TvDevice(
                id = o.optString(ID),
                friendlyName = o.optString(NAME),
                host = o.optString(HOST),
                connectPort = o.optInt(CONNECT_PORT, -1),
                lastConnectedAt = if (o.has(LAST_CONNECTED)) o.optLong(LAST_CONNECTED) else null,
                manufacturerHint = if (o.has(MANUFACTURER)) o.optString(MANUFACTURER) else null,
                mdnsId = if (o.has(MDNS_ID)) o.optString(MDNS_ID) else null,
            )
            device.takeIf {
                InputValidators.isValidDeviceId(it.id) &&
                    InputValidators.isValidIpv4(it.host) &&
                    InputValidators.isValidPort(it.connectPort)
            }
        }
    }
}
