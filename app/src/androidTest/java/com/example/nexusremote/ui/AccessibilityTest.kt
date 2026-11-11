package com.example.nexusremote.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.testutil.FakeContainerRule
import com.example.nexusremote.testutil.setScreen
import com.example.nexusremote.ui.screens.apps.AppsScreen
import com.example.nexusremote.ui.screens.home.HomeScreen
import com.example.nexusremote.ui.screens.keyboard.KeyboardScreen
import com.example.nexusremote.ui.screens.pairing.PairingScreen
import com.example.nexusremote.ui.screens.remote.RemoteScreen
import com.example.nexusremote.ui.screens.settings.SettingsScreen
import com.example.nexusremote.util.NexusResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the merged semantics tree of every screen and checks each interactive (clickable)
 * node has an accessible name and a touch target of at least 48dp.
 *
 * "Accessible name" means a contentDescription, or, for buttons that show a text label,
 * that label. Icon-only controls have no text, so for them this requires a contentDescription.
 * Adding a contentDescription on top of visible text makes TalkBack read the label twice,
 * so the test does not demand it for text buttons.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule val fakes = FakeContainerRule()
    @get:Rule val compose = createComposeRule()

    private val connected = ConnectionState.Connected(TestData.LIVING_ROOM_ID)

    private fun SemanticsNode.accessibleName(): String? {
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        return listOfNotNull(description, text).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun checkScreen(name: String, content: @Composable (SnackbarHostState) -> Unit) {
        compose.setScreen(content)
        compose.waitForIdle()
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("$name: expected interactive nodes", nodes.isNotEmpty())

        val unnamed = nodes.filter { it.accessibleName() == null }
        assertTrue(
            "$name: interactive nodes without an accessible name: " +
                unnamed.joinToString { "id=${it.id} bounds=${it.boundsInRoot} role=${it.config.getOrNull(SemanticsProperties.Role)}" },
            unnamed.isEmpty(),
        )

        val minPx = with(compose.density) { 48.dp.toPx() } - 1f
        val small = nodes.filter { it.touchBoundsInRoot.width < minPx || it.touchBoundsInRoot.height < minPx }
        assertTrue(
            "$name: touch targets smaller than 48dp: " +
                small.joinToString { "'${it.accessibleName()}' ${it.touchBoundsInRoot.width}x${it.touchBoundsInRoot.height}px" },
            small.isEmpty(),
        )
    }

    @Test
    fun homeEmpty() {
        fakes.install()
        checkScreen("Home (empty)") { HomeScreen(it, onNavigate = {}) }
    }

    @Test
    fun homeWithTv() {
        fakes.install(devices = listOf(TestData.livingRoom))
        checkScreen("Home") { HomeScreen(it, onNavigate = {}) }
    }

    @Test
    fun pairing() {
        fakes.install()
        checkScreen("Pairing") { PairingScreen(it, onBack = {}, onOpenRemote = {}) }
    }

    @Test
    fun remoteConnected() {
        fakes.install(devices = listOf(TestData.livingRoom), connection = connected)
        checkScreen("Remote") { RemoteScreen(it, onOpenApps = {}, onNavigate = {}) }
    }

    @Test
    fun remoteDisconnected() {
        fakes.install(devices = listOf(TestData.livingRoom))
        checkScreen("Remote (disconnected)") { RemoteScreen(it, onOpenApps = {}, onNavigate = {}) }
    }

    @Test
    fun apps() {
        val env = fakes.install(devices = listOf(TestData.livingRoom), connection = connected)
        env.client.appsResult = NexusResult.Success(TestData.apps)
        checkScreen("Apps") { AppsScreen(it) }
    }

    @Test
    fun keyboard() {
        fakes.install(devices = listOf(TestData.livingRoom), connection = connected)
        checkScreen("Keyboard") { KeyboardScreen(it) }
    }

    @Test
    fun settings() {
        fakes.install(devices = listOf(TestData.livingRoom, TestData.bedroom))
        checkScreen("Settings") { SettingsScreen(it, onNavigate = {}) }
    }
}
