package com.example.nexusremote.domain.model

/** User preferences stored in DataStore. */
data class AppSettings(
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val confirmPowerOff: Boolean = true,
)
