package com.example.nexusremote.app

import android.content.Context
import com.example.nexusremote.adb.AdbKtTransport
import com.example.nexusremote.adb.AdbTvControlClient
import com.example.nexusremote.adb.NsdTvDiscovery
import com.example.nexusremote.data.local.DataStoreDeviceRepository
import com.example.nexusremote.data.local.DataStoreQuickLaunchPreferences
import com.example.nexusremote.data.local.DataStoreSettingsRepository
import com.example.nexusremote.data.local.nexusDataStore
import com.example.nexusremote.data.security.KeystoreCredentialStore
import com.example.nexusremote.domain.repository.CredentialStore
import com.example.nexusremote.domain.repository.DeviceRepository
import com.example.nexusremote.domain.repository.EndpointDiscovery
import com.example.nexusremote.domain.repository.InMemoryQuickLaunchPreferences
import com.example.nexusremote.domain.repository.QuickLaunchPreferences
import com.example.nexusremote.domain.repository.SettingsRepository
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.domain.repository.TvDiscovery
import com.example.nexusremote.domain.repository.DisabledTvDiscovery
import com.example.nexusremote.domain.usecase.ConnectTvUseCase
import com.example.nexusremote.domain.usecase.ForgetDeviceUseCase
import com.example.nexusremote.domain.usecase.LaunchAppUseCase
import com.example.nexusremote.domain.usecase.OpenTvSettingsUseCase
import com.example.nexusremote.domain.usecase.ListAppsUseCase
import com.example.nexusremote.domain.usecase.PairTvUseCase
import com.example.nexusremote.domain.usecase.SaveDeviceUseCase
import com.example.nexusremote.domain.usecase.SendCommandUseCase
import com.example.nexusremote.domain.usecase.SendTextUseCase
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.util.SafeLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection. Production code uses [create]; tests build an instance with
 * fakes (e.g. a fake [TvControlClient]) through the constructor.
 */
class AppContainer(
    /** Process-lifetime scope for the connection monitor. Nothing here outlives the process. */
    val appScope: CoroutineScope,
    val credentialStore: CredentialStore,
    val deviceRepository: DeviceRepository,
    val settingsRepository: SettingsRepository,
    val tvClient: TvControlClient,
    endpointDiscovery: EndpointDiscovery,
    /** Local-network TV discovery shown on the Home list. */
    val tvDiscovery: TvDiscovery = DisabledTvDiscovery,
    /** Quick-launch order/visibility. Production passes the DataStore-backed implementation. */
    val quickLaunchPreferences: QuickLaunchPreferences = InMemoryQuickLaunchPreferences(),
) {
    val pairTv = PairTvUseCase(tvClient)
    val connectTv = ConnectTvUseCase(tvClient, deviceRepository, endpointDiscovery)
    val sendCommand = SendCommandUseCase(tvClient)
    val sendText = SendTextUseCase(tvClient)
    val launchApp = LaunchAppUseCase(tvClient)
    val listApps = ListAppsUseCase(tvClient)
    val saveDevice = SaveDeviceUseCase(deviceRepository)
    val forgetDevice = ForgetDeviceUseCase(tvClient, deviceRepository, credentialStore)
    val openTvSettings = OpenTvSettingsUseCase(tvClient)

    companion object {
        /** Production wiring: DataStore, Keystore-backed secrets, adb-kt transport, NSD discovery. */
        fun create(context: Context): AppContainer {
            val appContext = context.applicationContext
            // Background work (connection monitor, discovery, disconnect-on-background) must never
            // crash the app: failures are logged (class name only) and dropped.
            val safetyNet = CoroutineExceptionHandler { _, t ->
                SafeLog.error("AppScope", "background", ErrorCode.UNKNOWN, t)
            }
            val appScope = CoroutineScope(SupervisorJob() + safetyNet)
            val keystoreStore = KeystoreCredentialStore(appContext)
            val discovery = NsdTvDiscovery(appContext, appScope)
            return AppContainer(
                appScope = appScope,
                credentialStore = keystoreStore,
                deviceRepository = DataStoreDeviceRepository(appContext.nexusDataStore),
                settingsRepository = DataStoreSettingsRepository(appContext.nexusDataStore),
                tvClient = AdbTvControlClient(
                    transport = AdbKtTransport(),
                    hostKeys = keystoreStore,
                    appScope = appScope,
                ),
                // The same scanner answers "what is this TV's current connect port?".
                endpointDiscovery = discovery,
                tvDiscovery = discovery,
                quickLaunchPreferences = DataStoreQuickLaunchPreferences(appContext.nexusDataStore),
            )
        }
    }
}
