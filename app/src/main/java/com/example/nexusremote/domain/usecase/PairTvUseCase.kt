package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.InputValidators
import com.example.nexusremote.util.NexusResult

/** Validates a pairing request, pairs, and always wipes the in-memory pairing code. */
class PairTvUseCase(private val client: TvControlClient) {
    suspend operator fun invoke(request: PairingRequest): NexusResult<Unit> {
        try {
            val code = request.codeCopy()
            val codeValid = code.size == 6 && code.all { it in '0'..'9' }
            code.fill('\u0000')
            if (!InputValidators.isValidIpv4(request.host) ||
                !InputValidators.isValidPort(request.pairingPort) ||
                !InputValidators.isValidDeviceId(request.deviceId) ||
                !codeValid
            ) {
                return NexusResult.Failure(ErrorCode.INVALID_INPUT, "Invalid pairing input")
            }
            return client.pair(request)
        } finally {
            request.clear()
        }
    }
}
