package com.example.nexusremote.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexusremote.ui.navigation.NexusApp
import com.example.nexusremote.ui.navigation.Routes
import com.example.nexusremote.ui.theme.NexusTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** Single activity. All screens are Compose destinations in [NexusApp]. */
class MainActivity : ComponentActivity() {

    /** Route requested by the Quick Settings tile, consumed once by the nav host. */
    private val pendingDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            NexusTheme {
                val destination by pendingDestination.collectAsStateWithLifecycle()
                NexusApp(
                    pendingDestination = destination,
                    onDestinationHandled = { pendingDestination.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Only an allowlisted destination is accepted from the intent.
        if (intent?.getStringExtra(Routes.EXTRA_DESTINATION) == Routes.REMOTE) {
            pendingDestination.value = Routes.REMOTE
        }
    }
}
