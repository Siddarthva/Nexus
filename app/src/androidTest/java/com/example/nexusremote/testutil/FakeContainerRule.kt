package com.example.nexusremote.testutil

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.app.NexusApplication
import com.example.nexusremote.domain.model.AppSettings
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.fakes.FakeEnvironment
import com.example.nexusremote.ui.theme.NexusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.rules.ExternalResource

/**
 * Injects a [FakeEnvironment] through [NexusApplication.container], so screens get their
 * ViewModels from the real AppViewModelProvider factory, backed by fakes. Restores the real
 * container afterwards. No network or real ADB is ever used.
 */
class FakeContainerRule : ExternalResource() {
    private val app get() = ApplicationProvider.getApplicationContext<NexusApplication>()
    private var original: AppContainer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun install(
        devices: List<TvDevice> = emptyList(),
        activeId: String? = devices.firstOrNull()?.id,
        connection: ConnectionState = ConnectionState.Disconnected,
        settings: AppSettings = AppSettings(),
    ): FakeEnvironment {
        val env = FakeEnvironment(scope, devices, activeId, connection, settings)
        if (original == null) original = app.container
        app.container = env.container
        return env
    }

    override fun after() {
        original?.let { app.container = it }
        scope.cancel()
    }
}

fun string(id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

/** Hosts a screen inside the theme with a snackbar host, as the real root Scaffold does. */
fun ComposeContentTestRule.setScreen(content: @Composable (SnackbarHostState) -> Unit) = setContent {
    NexusTheme {
        val host = remember { SnackbarHostState() }
        Scaffold(snackbarHost = { SnackbarHost(host) }) { padding ->
            Box(Modifier.padding(padding)) { content(host) }
        }
    }
}
