package com.example.nexusremote.ui.screens

import app.cash.turbine.test
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.fakes.FakeEnvironment
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.testutil.MainDispatcherRule
import com.example.nexusremote.ui.components.UiMessage
import com.example.nexusremote.ui.screens.apps.AppsViewModel
import com.example.nexusremote.ui.screens.home.HomeViewModel
import com.example.nexusremote.ui.screens.keyboard.KeyboardViewModel
import com.example.nexusremote.ui.screens.settings.SettingsViewModel
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Home, Apps, Keyboard and Settings ViewModels against fakes. */
class OtherViewModelsTest {

    @get:Rule val main = MainDispatcherRule()

    private fun <T> TestScope.observe(flow: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
        return flow
    }

    private val connected = ConnectionState.Connected(TestData.LIVING_ROOM_ID)

    // ---- Home ---------------------------------------------------------------------------------

    @Test
    fun homeWithNoTvHasNoActiveDevice() = runTest {
        val vm = HomeViewModel(FakeEnvironment(backgroundScope).container)
        val state = observe(vm.uiState)
        assertTrue(state.value.loaded)
        assertNull(state.value.activeDevice)
    }

    @Test
    fun homeConnectUpdatesStateAndReportsSuccess() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom))
        val vm = HomeViewModel(env.container)
        val state = observe(vm.uiState)
        assertFalse(state.value.isConnectedToActive)
        vm.messages.test {
            vm.connect()
            assertEquals(UiMessage.Text(R.string.home_connected_message, listOf(TestData.livingRoom.friendlyName)), awaitItem())
        }
        assertTrue(state.value.isConnectedToActive)
    }

    @Test
    fun homeConnectFailureReportsErrorCode() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom))
        env.client.connectResult = NexusResult.Failure(ErrorCode.NETWORK_UNREACHABLE, "x")
        val vm = HomeViewModel(env.container)
        observe(vm.uiState)
        vm.messages.test {
            vm.connect()
            assertEquals(UiMessage.Error(ErrorCode.NETWORK_UNREACHABLE), awaitItem())
        }
        assertFalse(vm.uiState.value.busy)
    }

    // ---- Apps ---------------------------------------------------------------------------------

    @Test
    fun appsLoadWhenConnectedAndSearchFilters() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom), connection = connected)
        env.client.appsResult = NexusResult.Success(TestData.apps)
        val vm = AppsViewModel(env.container)
        val state = observe(vm.uiState)
        advanceUntilIdle()
        assertEquals(3, state.value.others.size)
        vm.onQueryChange("net")
        assertEquals(listOf("Netflix"), state.value.others.map { it.label })
        vm.onQueryChange("youtube.tv") // also matches package names
        assertEquals(listOf("YouTube"), state.value.others.map { it.label })
    }

    @Test
    fun appsNotLoadedWhileDisconnected() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom))
        val vm = AppsViewModel(env.container)
        val state = observe(vm.uiState)
        advanceUntilIdle()
        assertFalse(state.value.connected)
        assertEquals(0, env.client.listCalls)
    }

    @Test
    fun favoritesAreSeparatedAndLaunchUsesPackageName() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom), connection = connected)
        env.client.appsResult = NexusResult.Success(TestData.apps)
        val vm = AppsViewModel(env.container)
        val state = observe(vm.uiState)
        advanceUntilIdle()
        vm.toggleFavorite(TestData.apps[1])
        advanceUntilIdle()
        assertEquals(listOf("Netflix"), state.value.favorites.map { it.label })
        assertEquals(2, state.value.others.size)
        vm.launch(TestData.apps[1])
        advanceUntilIdle()
        assertEquals(listOf("com.netflix.ninja"), env.client.launchedPackages)
    }

    @Test
    fun launchFailureIsReported() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom), connection = connected)
        env.client.launchResult = { NexusResult.Failure(ErrorCode.COMMAND_FAILED, "x") }
        val vm = AppsViewModel(env.container)
        vm.messages.test {
            vm.launch(TestData.apps[0])
            assertEquals(UiMessage.Text(R.string.apps_opening, listOf("YouTube")), awaitItem())
            assertEquals(UiMessage.TextWithError(R.string.apps_launch_failed, "YouTube", ErrorCode.COMMAND_FAILED), awaitItem())
        }
    }

    // ---- Keyboard -----------------------------------------------------------------------------

    @Test
    fun keyboardRejectsUnsupportedCharactersWithoutSending() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom), connection = connected)
        val vm = KeyboardViewModel(env.container)
        val state = observe(vm.uiState)
        vm.onTextChange("hi \$HOME; ok")
        assertEquals(listOf('$', ';'), state.value.unsupported)
        assertFalse(state.value.canSend)
        vm.messages.test {
            vm.send()
            assertEquals(UiMessage.Text(R.string.keyboard_unsupported, listOf("$ ;")), awaitItem())
        }
        assertTrue(env.client.sentTexts.isEmpty())
    }

    @Test
    fun keyboardSendsSupportedText() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom), connection = connected)
        val vm = KeyboardViewModel(env.container)
        val state = observe(vm.uiState)
        vm.onTextChange("it's a test!")
        assertTrue(state.value.canSend)
        vm.messages.test {
            vm.send()
            assertEquals(UiMessage.Text(R.string.keyboard_sent), awaitItem())
        }
        assertEquals(listOf("it's a test!"), env.client.sentTexts)
    }

    @Test
    fun keyboardDisabledWhileDisconnectedAndClearWorks() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom))
        val vm = KeyboardViewModel(env.container)
        val state = observe(vm.uiState)
        vm.onTextChange("hello")
        assertFalse(state.value.canSend)
        vm.clear()
        assertEquals("", state.value.text)
    }

    @Test
    fun keyboardTooLongAndEmptyAreRejected() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom), connection = connected)
        val vm = KeyboardViewModel(env.container)
        vm.messages.test {
            vm.send()
            assertEquals(UiMessage.Text(R.string.keyboard_empty), awaitItem())
            vm.onTextChange("a".repeat(301))
            vm.send()
            assertEquals(UiMessage.Text(R.string.keyboard_too_long), awaitItem())
        }
        assertTrue(env.client.sentTexts.isEmpty())
    }

    // ---- Settings -----------------------------------------------------------------------------

    @Test
    fun settingsTogglesPersistThroughRepository() = runTest {
        val env = FakeEnvironment(backgroundScope)
        val vm = SettingsViewModel(env.container)
        val state = observe(vm.uiState)
        vm.setHaptics(false); vm.setSound(true); vm.setConfirmPower(false)
        advanceUntilIdle()
        val s = state.value.settings
        assertFalse(s.hapticsEnabled); assertTrue(s.soundEnabled); assertFalse(s.confirmPowerOff)
    }

    @Test
    fun settingsEditValidatesAndSaves() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom))
        val vm = SettingsViewModel(env.container)
        assertFalse(vm.saveEdit(TestData.livingRoom, "", "41000"))
        assertFalse(vm.saveEdit(TestData.livingRoom, "TV", "70000"))
        assertTrue(vm.saveEdit(TestData.livingRoom, "Lounge", "42000"))
        advanceUntilIdle()
        val saved = env.deviceRepo.getDevice(TestData.LIVING_ROOM_ID)!!
        assertEquals("Lounge", saved.friendlyName)
        assertEquals(42000, saved.connectPort)
    }

    @Test
    fun settingsForgetRemovesDeviceAndKey() = runTest {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom, TestData.bedroom))
        env.credentials.putKey(TestData.LIVING_ROOM_ID)
        val vm = SettingsViewModel(env.container)
        val state = observe(vm.uiState)
        vm.forget(TestData.livingRoom)
        advanceUntilIdle()
        assertEquals(listOf(TestData.BEDROOM_ID), state.value.devices.map { it.id })
        assertFalse(env.credentials.hasCredentials(TestData.LIVING_ROOM_ID))
    }
}
