package com.example.nexusremote.domain.repository

/** Domain view of the secure store: the domain may only check for or delete a device's secrets. */
interface CredentialStore {
    suspend fun hasCredentials(deviceId: String): Boolean
    suspend fun deleteCredentials(deviceId: String)
}
