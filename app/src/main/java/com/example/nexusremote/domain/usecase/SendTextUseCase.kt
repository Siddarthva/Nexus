package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.NexusResult

/** Character-set validation and escaping happen inside the client adapter (single choke point). */
class SendTextUseCase(private val client: TvControlClient) {
    suspend operator fun invoke(text: String): NexusResult<Unit> = client.sendText(text)
}
