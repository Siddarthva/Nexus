package com.example.nexusremote.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexusremote.R

object Routes {
    const val HOME = "home"
    const val REMOTE = "remote"
    const val APPS = "apps"
    const val KEYBOARD = "keyboard"
    const val SETTINGS = "settings"
    /** TV settings shortcuts (opened from the Remote "More" menu). */
    const val TV_SETTINGS = "tvsettings"

    /** Manual fallback: pair by typing IP, ports and code. */
    const val PAIR_ARG_DEVICE_ID = "deviceId"
    const val PAIR_PATTERN = "pair?$PAIR_ARG_DEVICE_ID={$PAIR_ARG_DEVICE_ID}"

    fun pair(deviceId: String? = null): String =
        if (deviceId == null) "pair" else "pair?$PAIR_ARG_DEVICE_ID=$deviceId"

    /** Pair a discovered TV with only the six-digit code. */
    const val PAIR_CODE_ARG_HOST = "host"
    const val PAIR_CODE_PATTERN =
        "paircode?$PAIR_CODE_ARG_HOST={$PAIR_CODE_ARG_HOST}&$PAIR_ARG_DEVICE_ID={$PAIR_ARG_DEVICE_ID}"

    fun pairCode(host: String, deviceId: String? = null): String =
        "paircode?$PAIR_CODE_ARG_HOST=$host" + (deviceId?.let { "&$PAIR_ARG_DEVICE_ID=$it" } ?: "")

    /** Intent extra used by the Quick Settings tile to open a screen. */
    const val EXTRA_DESTINATION = "com.example.nexusremote.DESTINATION"
}

/** Main navigation: TV list, remote, settings. Apps and Keyboard live in the Remote "More" menu. */
enum class BottomDestination(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Rounded.Tv),
    REMOTE(Routes.REMOTE, R.string.nav_remote, Icons.Rounded.SettingsRemote),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
}
