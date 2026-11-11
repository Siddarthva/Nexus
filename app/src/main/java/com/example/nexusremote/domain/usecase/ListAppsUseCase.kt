package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.model.TvApp
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.NexusResult

class ListAppsUseCase(private val client: TvControlClient) {
    suspend operator fun invoke(): NexusResult<List<TvApp>> =
        client.listInstalledPackages().map { apps -> apps.sortedBy { it.label.lowercase() } }
}
