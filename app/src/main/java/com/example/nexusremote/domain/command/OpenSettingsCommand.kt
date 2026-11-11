package com.example.nexusremote.domain.command

/**
 * The complete, closed set of TV settings screens the app can open. Each entry maps to one
 * fixed intent action string, and only these constants ever reach `am start -a`.
 *
 * These commands only OPEN a settings page on the TV; they never change a setting.
 * Wi-Fi / Bluetooth on-off toggles and "settings put" style writes are deliberately absent.
 */
enum class OpenSettingsCommand(val action: String) {
    WIFI("android.settings.WIFI_SETTINGS"),
    BLUETOOTH("android.settings.BLUETOOTH_SETTINGS"),
    DISPLAY("android.settings.DISPLAY_SETTINGS"),
    SOUND("android.settings.SOUND_SETTINGS"),
    APPS("android.settings.APPLICATION_SETTINGS"),
    DEVICE_INFO("android.settings.DEVICE_INFO_SETTINGS"),
}
