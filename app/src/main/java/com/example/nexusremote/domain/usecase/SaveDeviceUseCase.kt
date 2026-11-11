package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.domain.repository.DeviceRepository
import com.example.nexusremote.util.InputValidators
import com.example.nexusremote.util.NexusResult

/** Validates and saves a TV profile (also used for rename and connect-port edits). */
class SaveDeviceUseCase(private val devices: DeviceRepository) {
    suspend operator fun invoke(device: TvDevice, makeActive: Boolean = false): NexusResult<TvDevice> {
        val cleaned = device.copy(friendlyName = device.friendlyName.trim(), host = device.host.trim())
        if (!InputValidators.isValidDeviceId(cleaned.id) ||
            !InputValidators.isValidFriendlyName(cleaned.friendlyName) ||
            !InputValidators.isValidIpv4(cleaned.host) ||
            !InputValidators.isValidPort(cleaned.connectPort)
        ) {
            return NexusResult.Failure(ErrorCode.INVALID_INPUT, "Invalid device profile")
        }
        devices.upsert(cleaned)
        if (makeActive) devices.setActiveDevice(cleaned.id)
        return NexusResult.Success(cleaned)
    }
}
