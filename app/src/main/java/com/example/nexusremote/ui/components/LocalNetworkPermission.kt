package com.example.nexusremote.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** Android 17 (API 37) is the first release that enforces the local-network permission. */
private const val LOCAL_NETWORK_ENFORCED_SDK = 37

fun needsLocalNetworkPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= LOCAL_NETWORK_ENFORCED_SDK &&
        ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED

/**
 * Returns a function that runs a LAN action, first requesting ACCESS_LOCAL_NETWORK on
 * Android 17+ if it has not been granted. On older Android the action runs immediately.
 * If the user denies, [onDenied] is called and the action is skipped.
 */
// UNVERIFIED-ON-HARDWARE: behaviour of the ACCESS_LOCAL_NETWORK prompt and TCP access to the
// TV on an Android 17 phone has not been tested.
@Composable
fun rememberLocalNetworkGate(onDenied: () -> Unit): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    val denied by rememberUpdatedState(onDenied)
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted) action?.invoke() else denied()
    }

    return remember(context, launcher) {
        { action ->
            if (needsLocalNetworkPermission(context)) {
                pending = action
                launcher.launch(ACCESS_LOCAL_NETWORK)
            } else {
                action()
            }
        }
    }
}

/** Current local-network permission status for screens that scan the network. */
class LocalNetworkPermissionState(
    /** True when scanning/connecting is allowed (always true below Android 17). */
    val granted: Boolean,
    /** True after the user denied the request; the screen shows a message with Retry. */
    val denied: Boolean,
    /** Shows the system permission prompt (no-op if already granted). */
    val request: () -> Unit,
)

/**
 * Tracks ACCESS_LOCAL_NETWORK for screens that scan for TVs. The status is re-checked every
 * time the screen resumes, so granting the permission in system Settings is picked up.
 */
// UNVERIFIED-ON-HARDWARE: prompt and NSD behaviour on an Android 17 phone.
@Composable
fun rememberLocalNetworkPermissionState(): LocalNetworkPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(!needsLocalNetworkPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(context) {
        granted = !needsLocalNetworkPermission(context)
        if (granted) denied = false
        onPauseOrDispose { }
    }
    return LocalNetworkPermissionState(granted, denied) {
        if (needsLocalNetworkPermission(context)) launcher.launch(ACCESS_LOCAL_NETWORK) else granted = true
    }
}

/** Opens this app's page in system Settings (for a permission that was permanently denied). */
fun openAppSettings(context: Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
