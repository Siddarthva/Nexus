package com.example.nexusremote.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexusremote.R
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.testutil.FakeContainerRule
import com.example.nexusremote.testutil.setScreen
import com.example.nexusremote.testutil.string
import com.example.nexusremote.ui.screens.home.HomeScreen
import com.example.nexusremote.ui.screens.pairing.PairingScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAndPairingScreenTest {

    @get:Rule val fakes = FakeContainerRule()
    @get:Rule val compose = createComposeRule()

    // ---- Home ---------------------------------------------------------------------------------

    @Test
    fun homeShowsSetupPromptWhenNoTvIsSaved() {
        fakes.install(devices = emptyList())
        compose.setScreen { HomeScreen(it, onNavigate = {}) }
        compose.onNodeWithText(string(R.string.home_setup_title)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.home_setup_action)) and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.home_active_tv)).assertDoesNotExist()
    }

    @Test
    fun homeShowsActiveTvInsteadOfSetupPrompt() {
        fakes.install(devices = listOf(TestData.livingRoom))
        compose.setScreen { HomeScreen(it, onNavigate = {}) }
        compose.onNodeWithText(TestData.livingRoom.friendlyName).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.home_setup_title)).assertDoesNotExist()
    }

    @Test
    fun homeAddTvNavigatesToPairing() {
        fakes.install()
        val routes = mutableListOf<String>()
        compose.setScreen { HomeScreen(it, onNavigate = { r -> routes += r }) }
        compose.onNode(hasText(string(R.string.home_setup_action)) and hasClickAction()).performScrollTo().performClick()
        assertTrue(routes.single().startsWith("pair"))
    }

    // ---- Pairing ------------------------------------------------------------------------------

    private fun field(labelRes: Int) =
        compose.onNode(hasSetTextAction() and hasText(string(labelRes), substring = true)).performScrollTo()

    private fun pairButton() =
        compose.onNode(hasText(string(R.string.pair_action)) and hasClickAction()).performScrollTo()

    @Test
    fun pairButtonDisabledUntilFormIsValid() {
        fakes.install()
        compose.setScreen { PairingScreen(it, onBack = {}, onOpenRemote = {}) }
        pairButton().assertIsNotEnabled()

        field(R.string.pair_field_ip).performTextInput("192.168.1.20")
        field(R.string.pair_field_pairing_port).performTextInput("37000")
        field(R.string.pair_field_connect_port).performTextInput("41000")
        pairButton().assertIsNotEnabled()

        field(R.string.pair_field_code).performTextInput("12345")
        pairButton().assertIsNotEnabled()

        field(R.string.pair_field_code).performTextInput("6")
        pairButton().assertIsEnabled()
    }

    @Test
    fun badInputShowsValidationErrors() {
        fakes.install()
        compose.setScreen { PairingScreen(it, onBack = {}, onOpenRemote = {}) }

        field(R.string.pair_field_ip).performTextInput("999.1.1.1")
        compose.onNodeWithText(string(R.string.pair_error_ip)).performScrollTo().assertIsDisplayed()

        field(R.string.pair_field_pairing_port).performTextInput("0")
        compose.onNodeWithText(string(R.string.pair_error_port)).performScrollTo().assertIsDisplayed()

        field(R.string.pair_field_code).performTextInput("123")
        compose.onNodeWithText(string(R.string.pair_error_code)).performScrollTo().assertIsDisplayed()
        pairButton().assertIsNotEnabled()

        // Fixing the IP removes its error.
        field(R.string.pair_field_ip).performTextClearance()
        field(R.string.pair_field_ip).performTextInput("192.168.1.20")
        compose.onNodeWithText(string(R.string.pair_error_ip)).assertDoesNotExist()
    }

    @Test
    fun nonDigitsCannotBeTypedIntoTheCode() {
        fakes.install()
        compose.setScreen { PairingScreen(it, onBack = {}, onOpenRemote = {}) }
        field(R.string.pair_field_code).performTextInput("12ab")
        compose.onNodeWithText(string(R.string.pair_error_code)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pairingInstructionsAreShown() {
        fakes.install()
        compose.setScreen { PairingScreen(it, onBack = {}, onOpenRemote = {}) }
        compose.onNodeWithText(string(R.string.pair_step_haier_2)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.pair_step_connect_port)).performScrollTo().assertIsDisplayed()
    }
}
