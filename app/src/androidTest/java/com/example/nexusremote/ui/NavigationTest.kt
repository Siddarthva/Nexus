package com.example.nexusremote.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.testutil.FakeContainerRule
import com.example.nexusremote.testutil.string
import com.example.nexusremote.ui.navigation.NexusApp
import com.example.nexusremote.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real root (NexusApp: Scaffold, bottom bar, NavHost) with ViewModels created by the
 * production factory from a fake AppContainer. The selected TV is the SECOND saved TV, so
 * a fallback to "first device" would be caught.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule(order = 0) val fakes = FakeContainerRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    private fun tab(labelRes: Int) = compose.onNode(hasText(string(labelRes)) and hasClickAction()).performClick()

    private fun assertSelectedTvShown() {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(TestData.bedroom.friendlyName)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun selectedTvIsKeptAcrossScreens() {
        val env = fakes.install(
            devices = listOf(TestData.livingRoom, TestData.bedroom),
            activeId = TestData.BEDROOM_ID,
            connection = ConnectionState.Connected(TestData.BEDROOM_ID),
        )
        compose.setContent { NexusTheme { NexusApp(pendingDestination = null, onDestinationHandled = {}) } }

        // Home shows the selected TV.
        compose.onNodeWithText(TestData.bedroom.friendlyName).assertIsDisplayed()

        // Home -> Remote (quick link) -> Apps -> Keyboard -> Settings -> Remote (bottom bar).
        tab(R.string.nav_remote)
        assertSelectedTvShown()
        compose.onNodeWithText(string(R.string.status_connected)).assertIsDisplayed()

        tab(R.string.nav_apps)
        compose.onNodeWithText(string(R.string.apps_title)).assertIsDisplayed()
        tab(R.string.nav_keyboard)
        tab(R.string.nav_settings)
        assertSelectedTvShown()
        tab(R.string.nav_remote)
        assertSelectedTvShown()
        compose.onNodeWithText(string(R.string.status_connected)).assertIsDisplayed()

        // Back to Home: still the same TV.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(string(R.string.home_active_tv)).assertIsDisplayed()
        compose.onNodeWithText(TestData.bedroom.friendlyName).assertIsDisplayed()

        assertEquals(TestData.BEDROOM_ID, env.deviceRepo.activeIdState.value)
        assertEquals(ConnectionState.Connected(TestData.BEDROOM_ID), env.client.connectionState.value)
    }

    @Test
    fun tileDestinationOpensRemote() {
        fakes.install(devices = listOf(TestData.livingRoom))
        compose.setContent { NexusTheme { NexusApp(pendingDestination = "remote", onDestinationHandled = {}) } }
        compose.onNodeWithText(string(R.string.remote_disconnected_banner)).assertIsDisplayed()
    }
}
