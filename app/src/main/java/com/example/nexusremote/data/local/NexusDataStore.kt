package com.example.nexusremote.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore file: files/datastore/nexus_prefs.preferences_pb.
 * Holds only non-secret data and is excluded from backup (see res/xml/data_extraction_rules.xml
 * and res/xml/backup_rules.xml).
 */
val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_prefs")

internal object PrefKeys {
    val DEVICES_JSON = stringPreferencesKey("tv_profiles_json")
    val ACTIVE_DEVICE_ID = stringPreferencesKey("active_tv_id")
    val HAPTICS = booleanPreferencesKey("haptics_enabled")
    val SOUND = booleanPreferencesKey("sound_enabled")
    val CONFIRM_POWER = booleanPreferencesKey("confirm_power_off")
    val FAVORITES = stringSetPreferencesKey("favorite_packages")

    /** Quick-launch row: comma-separated app ids in display order, and the hidden ids. */
    val QUICK_LAUNCH_ORDER = stringPreferencesKey("quick_launch_order")
    val QUICK_LAUNCH_HIDDEN = stringSetPreferencesKey("quick_launch_hidden")
}
