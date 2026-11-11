package com.example.nexusremote.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexusremote.R
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.AppSettings
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.fakes.FakeEnvironment
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.testutil.FakeContainerRule
import com.example.nexusremote.testutil.setScreen
import com.example.nexusremote.testutil.string
import com.example.nexusremote.ui.screens.remote.RemoteScreen
import com.example.nexusremote.util.NexusResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteScreenTest {

    @get:Rule val fakes = FakeContainerRule()
    @get:Rule val compose = createComposeRule()

    private fun launch(
        connection: ConnectionState = ConnectionState.Connected(TestData.LIVING_ROOM_ID),
        settings: AppSettings = AppSettings(hapticsEnabled = false),
    ): FakeEnvironment {
        val env = fakes.install(devices = listOf(TestData.livingRoom), connection = connection, settings = settings)
        compose.setScreen { RemoteScreen(it, onOpenApps = {}, onNavigate = {}) }
        return env
    }

    private fun tap(descriptionRes: Int) =
        compose.onNodeWithContentDescription(string(descriptionRes)).performScrollTo().performClick()

    @Test
    fun everyButtonSendsTheCorrectCommand() {
        val env = launch()
        val buttons = listOf(
            R.string.cd_dpad_up to TvCommand.UP,
            R.string.cd_dpad_down to TvCommand.DOWN,
            R.string.cd_dpad_left to TvCommand.LEFT,
            R.string.cd_dpad_right to TvCommand.RIGHT,
            R.string.cd_ok to TvCommand.SELECT,
            R.string.cd_back to TvCommand.BACK,
            R.string.cd_home to TvCommand.HOME,
            R.string.cd_volume_up to TvCommand.VOLUME_UP,
            R.string.cd_volume_down to TvCommand.VOLUME_DOWN,
            R.string.cd_mute to TvCommand.MUTE,
            R.string.cd_play_pause to TvCommand.PLAY_PAUSE,
            R.string.cd_rewind to TvCommand.REWIND,
            R.string.cd_fast_forward to TvCommand.FAST_FORWARD,
        )
        buttons.forEach { (description, _) -> tap(description) }
        compose.waitUntil(5_000) { env.client.sentCommands.size >= buttons.size }
        compose.waitForIdle()
        assertEquals(buttons.map { it.second }, env.client.sentCommands.toList())
    }

    @Test
    fun controlsAreDisabledAndSendNothingWhileDisconnected() {
        val env = launch(connection = ConnectionState.Disconnected)
        compose.onNodeWithContentDescription(string(R.string.cd_dpad_up)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(string(R.string.cd_home)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(string(R.string.cd_power)).assertIsNotEnabled()
        tap(R.string.cd_dpad_up)
        tap(R.string.cd_volume_up)
        compose.waitForIdle()
        assertTrue(env.client.sentCommands.isEmpty())
        compose.onNodeWithText(string(R.string.remote_disconnected_banner)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.remote_reconnect)) and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun commandFailedShowsSnackbar() {
        val env = launch()
        env.client.commandResult = { NexusResult.Failure(ErrorCode.COMMAND_FAILED, "x") }
        tap(R.string.cd_home)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("COMMAND_FAILED", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(string(R.string.error_command_failed), substring = true).assertIsDisplayed()
    }

    @Test
    fun powerShowsConfirmationAndCancelSendsNothing() {
        val env = launch(settings = AppSettings(hapticsEnabled = false, confirmPowerOff = true))
        tap(R.string.cd_power)
        compose.onNodeWithText(string(R.string.power_confirm_title)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.action_cancel)) and hasClickAction()).performClick()
        compose.onNodeWithText(string(R.string.power_confirm_title)).assertDoesNotExist()
        compose.waitForIdle()
        assertTrue(env.client.sentCommands.isEmpty())
    }

    @Test
    fun powerConfirmSendsPower() {
        val env = launch(settings = AppSettings(hapticsEnabled = false, confirmPowerOff = true))
        tap(R.string.cd_power)
        compose.onNode(hasText(string(R.string.power_confirm_action)) and hasClickAction()).performClick()
        compose.waitUntil(5_000) { env.client.sentCommands.isNotEmpty() }
        assertEquals(listOf(TvCommand.POWER), env.client.sentCommands.toList())
    }

    @Test
    fun powerWithoutConfirmationSettingSendsImmediately() {
        val env = launch(settings = AppSettings(hapticsEnabled = false, confirmPowerOff = false))
        tap(R.string.cd_power)
        compose.onNodeWithText(string(R.string.power_confirm_title)).assertDoesNotExist()
        compose.waitUntil(5_000) { env.client.sentCommands.isNotEmpty() }
        assertEquals(listOf(TvCommand.POWER), env.client.sentCommands.toList())
    }
}
