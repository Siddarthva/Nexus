package com.example.nexusremote.fakes

import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.model.AppSettings
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.TvApp
import com.example.nexusremote.domain.model.TvDevice
import kotlinx.coroutines.CoroutineScope

/** A complete [AppContainer] built from fakes, plus handles to every fake for assertions. */
class FakeEnvironment(
    scope: CoroutineScope,
    devices: List<TvDevice> = emptyList(),
    activeId: String? = devices.firstOrNull()?.id,
    connection: ConnectionState = ConnectionState.Disconnected,
    settings: AppSettings = AppSettings(),
) {
    val client = FakeTvControlClient(connection)
    val deviceRepo = FakeDeviceRepository(devices, activeId)
    val settingsRepo = FakeSettingsRepository(settings)
    val credentials = FakeCredentialStore()
    val discovery = FakeEndpointDiscovery()

    val container = AppContainer(
        appScope = scope,
        credentialStore = credentials,
        deviceRepository = deviceRepo,
        settingsRepository = settingsRepo,
        tvClient = client,
        endpointDiscovery = discovery,
    )
}

/** Shared test data. Addresses are from the RFC 5737 documentation range and are never contacted. */
object TestData {
    const val LIVING_ROOM_ID = "11111111-1111-4111-8111-111111111111"
    const val BEDROOM_ID = "22222222-2222-4222-8222-222222222222"

    val livingRoom = TvDevice(
        id = LIVING_ROOM_ID,
        friendlyName = "Living room TV",
        host = "192.0.2.10",
        connectPort = 40001,
        lastConnectedAt = 1_700_000_000_000,
        manufacturerHint = "Haier",
    )

    val bedroom = TvDevice(
        id = BEDROOM_ID,
        friendlyName = "Bedroom TV",
        host = "192.0.2.11",
        connectPort = 40002,
    )

    val apps = listOf(
        TvApp("com.google.android.youtube.tv", "YouTube"),
        TvApp("com.netflix.ninja", "Netflix"),
        TvApp("com.amazon.amazonvideo.livingroom", "Prime Video"),
    )
}
