package com.example.nexusremote.data.security

import java.security.KeyPair

/**
 * Storage for the per-TV ADB host RSA key pair. Implementations must keep private key
 * material encrypted at rest and must never log it.
 */
interface HostKeyStore {
    /** Loads the device's key pair, or generates and stores a new 2048-bit RSA pair. */
    suspend fun getOrCreateKeyPair(deviceId: String): KeyPair

    /** Loads the device's key pair, or returns null if none is stored or it cannot be decrypted. */
    suspend fun loadKeyPair(deviceId: String): KeyPair?

    suspend fun deleteKeyPair(deviceId: String)
}
