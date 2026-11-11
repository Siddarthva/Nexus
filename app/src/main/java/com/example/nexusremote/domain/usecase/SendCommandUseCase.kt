package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.NexusResult

class SendCommandUseCase(private val client: TvControlClient) {
    suspend operator fun invoke(command: TvCommand): NexusResult<Unit> = client.sendCommand(command)
}
