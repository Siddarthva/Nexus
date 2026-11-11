package com.example.nexusremote.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.example.nexusremote.domain.model.DiscoveredTv
import com.example.nexusremote.domain.repository.EndpointDiscovery
import com.example.nexusremote.domain.repository.TvDiscovery
import com.example.nexusremote.util.InputValidators
import com.example.nexusremote.util.SafeLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address

/**
 * Finds TVs with Wireless debugging enabled using Android NSD (mDNS). Discovery only listens
 * for service advertisements; it never connects to or sends anything to a TV.
 *
 * Services are resolved one at a time (older Android allows only one resolve in flight).
 * Only IPv4 addresses are kept, because the rest of the app accepts IPv4 only.
 */
// UNVERIFIED-ON-HARDWARE: whether the Haier TV advertises these services on the LAN, the
// exact service-name format (used for mdnsId), and whether routers forward mDNS between bands.
class NsdTvDiscovery(
    context: Context,
    private val scope: CoroutineScope,
) : TvDiscovery, EndpointDiscovery {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val lock = Any()

    private var users = 0
    private var foreground = false
    private var listeners: List<NsdManager.DiscoveryListener> = emptyList()
    private var resolveQueue: Channel<Pending>? = null
    private var resolveJob: Job? = null
    private var stoppedAt = 0L

    /** Resolved services keyed by "type|serviceName". */
    private val services = mutableMapOf<String, Resolved>()

    private val _tvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    override val tvs: StateFlow<List<DiscoveredTv>> = _tvs.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private data class Pending(val type: String, val info: NsdServiceInfo)
    private data class Resolved(val type: String, val name: String, val host: String, val port: Int)

    override fun acquire() = synchronized(lock) {
        users++
        updateLocked()
    }

    override fun release() = synchronized(lock) {
        users = (users - 1).coerceAtLeast(0)
        updateLocked()
    }

    override fun setForeground(foreground: Boolean) = synchronized(lock) {
        this.foreground = foreground
        updateLocked()
    }

    override fun refresh() = synchronized(lock) {
        stopLocked()
        services.clear()
        publishLocked()
        updateLocked()
    }

    /** Port lookup for ConnectTvUseCase. Uses current results only; never opens a socket. */
    override suspend fun findConnectPort(host: String): Int? =
        _tvs.value.firstOrNull { it.host == host }?.connectPort

    private fun updateLocked() {
        val shouldScan = users > 0 && foreground
        if (shouldScan && listeners.isEmpty()) startLocked()
        if (!shouldScan && listeners.isNotEmpty()) stopLocked()
    }

    private fun startLocked() {
        // Keep results across short gaps (e.g. moving from the TV list to the pairing screen),
        // but drop them after a long pause, when they may be stale.
        if (System.currentTimeMillis() - stoppedAt > STALE_AFTER_MS) {
            services.clear()
            publishLocked()
        }
        val queue = Channel<Pending>(Channel.UNLIMITED)
        resolveQueue = queue
        resolveJob = scope.launch {
            for (pending in queue) {
                val resolved = resolve(pending) ?: continue
                synchronized(lock) {
                    if (listeners.isNotEmpty()) {
                        services["${resolved.type}|${resolved.name}"] = resolved
                        publishLocked()
                    }
                }
            }
        }
        listeners = SERVICE_TYPES.map { type ->
            listenerFor(type, queue).also { listener ->
                try {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: IllegalArgumentException) {
                    SafeLog.event(TAG, "discovery start rejected")
                }
            }
        }
        _scanning.value = true
        SafeLog.event(TAG, "scan started")
    }

    private fun stopLocked() {
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners = emptyList()
        resolveQueue?.close()
        resolveQueue = null
        resolveJob?.cancel()
        resolveJob = null
        stoppedAt = System.currentTimeMillis()
        _scanning.value = false
        SafeLog.event(TAG, "scan stopped")
    }

    private fun listenerFor(type: String, queue: Channel<Pending>) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) =
            SafeLog.event(TAG, "discovery failed to start ($errorCode)")
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            queue.trySend(Pending(type, serviceInfo))
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(lock) {
                if (services.remove("$type|${serviceInfo.serviceName}") != null) publishLocked()
            }
        }
    }

    @Suppress("DEPRECATION") // resolveService is deprecated on API 34+ but is the only option on minSdk 26.
    private suspend fun resolve(pending: Pending): Resolved? {
        val result = CompletableDeferred<NsdServiceInfo?>()
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                result.complete(null)
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                result.complete(serviceInfo)
            }
        }
        val info = try {
            nsd.resolveService(pending.info, listener)
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { result.await() }
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        val host = ipv4Of(info)?.takeIf(InputValidators::isValidIpv4) ?: return null
        if (!InputValidators.isValidPort(info.port)) return null
        return Resolved(pending.type, pending.info.serviceName, host, info.port)
    }

    @Suppress("DEPRECATION")
    private fun ipv4Of(info: NsdServiceInfo): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
        } else {
            (info.host as? Inet4Address)?.hostAddress
        }

    private fun publishLocked() {
        _tvs.value = services.values
            .groupBy { it.host }
            .map { (host, entries) ->
                val connect = entries.firstOrNull { it.type == CONNECT_TYPE }
                val pairing = entries.firstOrNull { it.type == PAIRING_TYPE }
                val name = (connect ?: pairing)!!.name
                DiscoveredTv(
                    host = host,
                    serviceName = name,
                    mdnsId = mdnsIdOf(name),
                    pairingPort = pairing?.port,
                    connectPort = connect?.port,
                )
            }
            .sortedBy { it.host }
    }

    internal companion object {
        const val TAG = "NsdTvDiscovery"
        const val CONNECT_TYPE = "_adb-tls-connect._tcp"
        const val PAIRING_TYPE = "_adb-tls-pairing._tcp"
        val SERVICE_TYPES = listOf(CONNECT_TYPE, PAIRING_TYPE)
        const val RESOLVE_TIMEOUT_MS = 3_000L
        const val STALE_AFTER_MS = 30_000L

        /** "adb-<id>-<random>" (the format adbd uses) -> "<id>"; anything else -> null. */
        // UNVERIFIED-ON-HARDWARE: the service-name format on the Haier TV.
        private val ADB_NAME = Regex("""^adb-(.+)-[A-Za-z0-9]{6}$""")

        fun mdnsIdOf(serviceName: String): String? = ADB_NAME.matchEntire(serviceName)?.groupValues?.get(1)
    }
}
