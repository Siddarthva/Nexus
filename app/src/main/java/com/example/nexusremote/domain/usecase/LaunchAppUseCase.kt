package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.InputValidators
import com.example.nexusremote.util.NexusResult

class LaunchAppUseCase(private val client: TvControlClient) {
    suspend operator fun invoke(packageName: String): NexusResult<Unit> {
        if (!InputValidators.isValidPackageName(packageName)) {
            return NexusResult.Failure(ErrorCode.INVALID_INPUT, "Invalid package name")
        }
        return client.launchPackage(packageName)
    }
}
