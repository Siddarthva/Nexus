package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.command.OpenSettingsCommand
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.NexusResult

/** Opens one fixed TV settings page. Sent once per tap; never retried. */
class OpenTvSettingsUseCase(private val client: TvControlClient) {
    suspend operator fun invoke(command: OpenSettingsCommand): NexusResult<Unit> = client.openSettings(command)
}
