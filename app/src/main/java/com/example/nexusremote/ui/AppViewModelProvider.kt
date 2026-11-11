package com.example.nexusremote.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.app.NexusApplication
import com.example.nexusremote.ui.screens.apps.AppsViewModel
import com.example.nexusremote.ui.screens.home.HomeViewModel
import com.example.nexusremote.ui.screens.keyboard.KeyboardViewModel
import com.example.nexusremote.ui.screens.pairing.PairingViewModel
import com.example.nexusremote.ui.screens.paircode.PairCodeViewModel
import com.example.nexusremote.ui.screens.remote.RemoteViewModel
import com.example.nexusremote.ui.screens.settings.SettingsViewModel
import com.example.nexusremote.ui.screens.tvsettings.TvSettingsViewModel

/** Builds every ViewModel from the [AppContainer] (manual DI, no Hilt). */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(container()) }
        initializer { PairingViewModel(createSavedStateHandle(), container()) }
        initializer { PairCodeViewModel(createSavedStateHandle(), container()) }
        initializer { RemoteViewModel(container()) }
        initializer { AppsViewModel(container()) }
        initializer { KeyboardViewModel(container()) }
        initializer { SettingsViewModel(container()) }
        initializer { TvSettingsViewModel(container()) }
    }

    private fun CreationExtras.container(): AppContainer =
        (this[APPLICATION_KEY] as NexusApplication).container
}
