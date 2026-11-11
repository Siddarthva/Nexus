package com.example.nexusremote.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.testutil.FakeContainerRule
import com.example.nexusremote.testutil.setScreen
import com.example.nexusremote.testutil.string
import com.example.nexusremote.ui.screens.apps.AppsScreen
import com.example.nexusremote.ui.screens.keyboard.KeyboardScreen
import com.example.nexusremote.ui.screens.settings.SettingsScreen
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsKeyboardSettingsScreenTest {

    @get:Rule val fakes = FakeContainerRule()
    @get:Rule val compose = createComposeRule()

    private val connected = ConnectionState.Connected(TestData.LIVING_ROOM_ID)

    // ---- Apps ---------------------------------------------------------------------------------

    @Test
    fun searchFiltersAndTapLaunchesTheRightPackage() {
        val env = fakes.install(devices = listOf(TestData.livingRoom), connection = connected)
        env.client.appsResult = NexusResult.Success(TestData.apps)
        compose.setScreen { AppsScreen(it) }

        val openYouTube = string(R.string.cd_open_app, "YouTube")
        val openNetflix = string(R.string.cd_open_app, "Netflix")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(openYouTube).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNode(hasSetTextAction() and hasText(string(R.string.apps_search))).performTextInput("net")
        compose.onNodeWithContentDescription(openYouTube).assertDoesNotExist()
        compose.onNodeWithContentDescription(openNetflix).assertIsDisplayed().performClick()

        compose.waitUntil(5_000) { env.client.launchedPackages.isNotEmpty() }
        assertEquals(listOf("com.netflix.ninja"), env.client.launchedPackages.toList())
    }

    @Test
    fun favoriteStarMovesAppToFavorites() {
        val env = fakes.install(devices = listOf(TestData.livingRoom), connection = connected)
        env.client.appsResult = NexusResult.Success(TestData.apps)
        compose.setScreen { AppsScreen(it) }
        val addNetflix = string(R.string.cd_favorite_add, "Netflix")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(addNetflix).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(addNetflix).performClick()
        compose.onNodeWithText(string(R.string.apps_favorites)).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.cd_favorite_remove, "Netflix")).assertIsDisplayed()
        assertEquals(setOf("com.netflix.ninja"), env.settingsRepo.favoritesState.value)
    }

    // ---- Keyboard -----------------------------------------------------------------------------

    @Test
    fun keyboardRejectsUnsupportedCharactersAndShowsSupportedNote() {
        val env = fakes.install(devices = listOf(TestData.livingRoom), connection = connected)
        compose.setScreen { KeyboardScreen(it) }

        compose.onNodeWithText(string(R.string.keyboard_supported)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(string(R.string.keyboard_field), substring = true))
            .performTextInput("hi \$HOME;")
        compose.onNodeWithText(string(R.string.keyboard_unsupported, "$ ;")).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.keyboard_send)) and hasClickAction()).assertIsNotEnabled()
        compose.waitForIdle()
        assertTrue(env.client.sentTexts.isEmpty())
    }

    @Test
    fun keyboardSendsSupportedText() {
        val env = fakes.install(devices = listOf(TestData.livingRoom), connection = connected)
        compose.setScreen { KeyboardScreen(it) }
        compose.onNode(hasSetTextAction() and hasText(string(R.string.keyboard_field), substring = true))
            .performTextInput("it's a test!")
        compose.onNode(hasText(string(R.string.keyboard_send)) and hasClickAction()).performClick()
        compose.waitUntil(5_000) { env.client.sentTexts.isNotEmpty() }
        assertEquals(listOf("it's a test!"), env.client.sentTexts.toList())
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(string(R.string.keyboard_sent))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ---- Settings -----------------------------------------------------------------------------

    @Test
    fun forgetDeviceAsksForConfirmationAndCancelKeepsIt() {
        val env = fakes.install(devices = listOf(TestData.livingRoom))
        env.credentials.putKey(TestData.LIVING_ROOM_ID)
        compose.setScreen { SettingsScreen(it, onNavigate = {}) }

        compose.onNodeWithContentDescription(string(R.string.cd_forget_tv, TestData.livingRoom.friendlyName)).performClick()
        compose.onNodeWithText(string(R.string.forget_title, TestData.livingRoom.friendlyName)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.forget_body)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.action_cancel)) and hasClickAction()).performClick()

        compose.onNodeWithText(string(R.string.forget_title, TestData.livingRoom.friendlyName)).assertDoesNotExist()
        runBlocking {
            assertNotNull(env.deviceRepo.getDevice(TestData.LIVING_ROOM_ID))
            assertTrue(env.credentials.hasCredentials(TestData.LIVING_ROOM_ID))
        }
    }

    @Test
    fun confirmingForgetRemovesDeviceAndKey() {
        val env = fakes.install(devices = listOf(TestData.livingRoom))
        env.credentials.putKey(TestData.LIVING_ROOM_ID)
        compose.setScreen { SettingsScreen(it, onNavigate = {}) }

        compose.onNodeWithContentDescription(string(R.string.cd_forget_tv, TestData.livingRoom.friendlyName)).performClick()
        compose.onNode(hasText(string(R.string.forget_confirm)) and hasClickAction()).performClick()
        compose.waitUntil(5_000) { env.deviceRepo.deviceState.value.isEmpty() }
        runBlocking {
            assertNull(env.deviceRepo.getDevice(TestData.LIVING_ROOM_ID))
            assertFalse(env.credentials.hasCredentials(TestData.LIVING_ROOM_ID))
        }
        compose.onNodeWithText(string(R.string.settings_no_tvs)).assertIsDisplayed()
    }
}
