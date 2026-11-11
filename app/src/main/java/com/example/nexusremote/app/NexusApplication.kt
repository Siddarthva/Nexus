package com.example.nexusremote.app

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.nexusremote.domain.model.ConnectionState
import kotlinx.coroutines.launch

class NexusApplication : Application() {
    /**
     * The process-wide container. Instrumented tests replace it with one built from fakes
     * before any screen (and therefore any ViewModel) is created.
     */
    @set:VisibleForTesting
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(DisconnectWhenBackgrounded())
    }

    /**
     * Stops TV discovery and closes the ADB connection to the TV as soon as no NEXUS screen is
     * visible (app sent to the background or closed). ProcessLifecycleOwner debounces rotation and other config
     * changes, so those do not disconnect. Nothing reconnects automatically when the app
     * returns: the user taps Connect / Reconnect.
     */
    private inner class DisconnectWhenBackgrounded : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // Discovery may scan again (only if a TV-list screen is visible).
            container.tvDiscovery.setForeground(true)
        }

        override fun onStop(owner: LifecycleOwner) {
            val active = container
            // Stop mDNS scanning whenever the app leaves the foreground.
            active.tvDiscovery.setForeground(false)
            // Only when a session is open or being opened, so a Failed(AUTH_REVOKED) state (and
            // its "Pair again" prompt) is preserved. disconnect() waits for any in-flight
            // connect/pair to finish, then closes the socket.
            when (active.tvClient.connectionState.value) {
                is ConnectionState.Connected, ConnectionState.Connecting, ConnectionState.Pairing ->
                    active.appScope.launch { active.tvClient.disconnect() }
                ConnectionState.Disconnected, is ConnectionState.Failed -> Unit
            }
        }
    }
}
