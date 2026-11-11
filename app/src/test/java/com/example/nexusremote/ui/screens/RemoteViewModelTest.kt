package com.example.nexusremote.ui.screens

import app.cash.turbine.test
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.AppSettings
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.fakes.FakeEnvironment
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.testutil.MainDispatcherRule
import com.example.nexusremote.ui.components.UiMessage
import com.example.nexusremote.ui.screens.remote.RemoteViewModel
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RemoteViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun TestScope.setup(
        connection: ConnectionState = ConnectionState.Connected(TestData.LIVING_ROOM_ID),
        settings: AppSettings = AppSettings(),
    ): Pair<FakeEnvironment, RemoteViewModel> {
        val env = FakeEnvironment(backgroundScope, listOf(TestData.livingRoom), connection = connection, settings = settings)
        val vm = RemoteViewModel(env.container)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return env to vm
    }

    @Test
    fun controlsEnabledOnlyWhenActiveTvIsConnected() = runTest {
        val (env, vm) = setup()
        assertTrue(vm.uiState.value.controlsEnabled)
        env.client.setState(ConnectionState.Disconnected)
        assertFalse(vm.uiState.value.controlsEnabled)
        env.client.setState(ConnectionState.Connected(TestData.BEDROOM_ID)) // a different TV
        assertFalse(vm.uiState.value.controlsEnabled)
        env.client.setState(ConnectionState.Failed(ErrorCode.AUTH_REVOKED, "x"))
        assertFalse(vm.uiState.value.controlsEnabled)
    }

    @Test
    fun pressSendsTheCommand() = runTest {
        val (env, vm) = setup()
        listOf(TvCommand.UP, TvCommand.SELECT, TvCommand.BACK, TvCommand.HOME, TvCommand.MUTE).forEach(vm::press)
        advanceUntilIdle()
        assertEquals(listOf(TvCommand.UP, TvCommand.SELECT, TvCommand.BACK, TvCommand.HOME, TvCommand.MUTE), env.client.sentCommands)
    }

    @Test
    fun disabledControlsSendNothingWhileDisconnected() = runTest {
        val (env, vm) = setup(connection = ConnectionState.Disconnected)
        TvCommand.entries.forEach { vm.press(it); vm.repeat(it) }
        advanceUntilIdle()
        assertTrue(env.client.sentCommands.isEmpty())
        assertFalse(vm.uiState.value.showPowerConfirm)
    }

    @Test
    fun repeatIsIgnoredForNonRepeatableCommands() = runTest {
        val (env, vm) = setup()
        vm.repeat(TvCommand.POWER)
        vm.repeat(TvCommand.SELECT)
        vm.repeat(TvCommand.VOLUME_DOWN)
        advanceUntilIdle()
        assertEquals(listOf(TvCommand.VOLUME_DOWN), env.client.sentCommands)
    }

    @Test
    fun powerAsksForConfirmationAndCancelSendsNothing() = runTest {
        val (env, vm) = setup(settings = AppSettings(confirmPowerOff = true))
        vm.press(TvCommand.POWER)
        assertTrue(vm.uiState.value.showPowerConfirm)
        vm.dismissPower()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPowerConfirm)
        assertTrue(env.client.sentCommands.isEmpty())
    }

    @Test
    fun powerConfirmSendsPower() = runTest {
        val (env, vm) = setup(settings = AppSettings(confirmPowerOff = true))
        vm.press(TvCommand.POWER)
        vm.confirmPower()
        advanceUntilIdle()
        assertEquals(listOf(TvCommand.POWER), env.client.sentCommands)
    }

    @Test
    fun powerWithoutConfirmationSettingSendsImmediately() = runTest {
        val (env, vm) = setup(settings = AppSettings(confirmPowerOff = false))
        vm.press(TvCommand.POWER)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPowerConfirm)
        assertEquals(listOf(TvCommand.POWER), env.client.sentCommands)
    }

    @Test
    fun failedCommandEmitsAnErrorMessage() = runTest {
        val (env, vm) = setup()
        env.client.commandResult = { NexusResult.Failure(ErrorCode.COMMAND_FAILED, "x") }
        vm.messages.test {
            vm.press(TvCommand.HOME)
            assertEquals(UiMessage.Error(ErrorCode.COMMAND_FAILED), awaitItem())
        }
    }

    @Test
    fun reconnectCallsConnectForActiveTv() = runTest {
        val (env, vm) = setup(connection = ConnectionState.Disconnected)
        vm.reconnect()
        advanceUntilIdle()
        assertEquals(listOf(TestData.LIVING_ROOM_ID), env.client.connectCalls.map { it.id })
        assertTrue(vm.uiState.value.controlsEnabled)
    }
}
