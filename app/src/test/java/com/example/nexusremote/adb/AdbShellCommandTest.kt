package com.example.nexusremote.adb

import com.example.nexusremote.domain.command.KeyCodeMapping
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.util.ShellTextEscaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbShellCommandTest {

    @Test
    fun keyEventsUseOnlyTheFixedKeycode() {
        TvCommand.entries.forEach { command ->
            val cmd = AdbShellCommand.keyEvent(command)
            assertEquals("input keyevent ${KeyCodeMapping.keyCodeFor(command)}", cmd.shell)
        }
    }

    @Test
    fun inputTextUsesTheEscapedArgument() {
        val escaped = ShellTextEscaper.escape("hi there") as ShellTextEscaper.Outcome.Escaped
        assertEquals("input text 'hi%sthere'", AdbShellCommand.inputText(escaped).shell)
    }

    @Test
    fun launchRejectsInvalidPackageNames() {
        assertThrows(IllegalArgumentException::class.java) { AdbShellCommand.launchLeanback("com.evil;reboot") }
        assertThrows(IllegalArgumentException::class.java) { AdbShellCommand.launchLauncher("") }
    }

    @Test
    fun launchCommandsAreFixedMonkeyInvocations() {
        assertEquals(
            "monkey -p com.netflix.ninja -c android.intent.category.LEANBACK_LAUNCHER 1",
            AdbShellCommand.launchLeanback("com.netflix.ninja").shell,
        )
        assertEquals(
            "monkey -p com.netflix.ninja -c android.intent.category.LAUNCHER 1",
            AdbShellCommand.launchLauncher("com.netflix.ninja").shell,
        )
    }

    @Test
    fun toStringAndLabelNeverContainThePayload() {
        val escaped = ShellTextEscaper.escape("secret text") as ShellTextEscaper.Outcome.Escaped
        val cmd = AdbShellCommand.inputText(escaped)
        assertFalse(cmd.toString().contains("secret"))
        assertFalse(cmd.label.contains("secret"))
        val launch = AdbShellCommand.launchLeanback("com.netflix.ninja")
        assertFalse(launch.label.contains("netflix"))
        assertTrue(AdbShellCommand.keyEvent(TvCommand.HOME).label.contains("HOME"))
    }

    @Test
    fun packageListingParsersRevalidateNames() {
        val listing = """
            com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity
            com.evil;reboot/x.Y
            com.netflix.ninja/.MainActivity
            3 activities found
        """.trimIndent()
        val apps = PackageListParser.parseActivityListing(listing)
        assertEquals(listOf("com.google.android.youtube.tv", "com.netflix.ninja"), apps.map { it.packageName })
        assertEquals("YouTube", apps.first().label)

        val pm = "package:com.netflix.ninja\npackage:bad name\npackage:org.videolan.vlc\n"
        assertEquals(listOf("com.netflix.ninja", "org.videolan.vlc"), PackageListParser.parsePmListing(pm).map { it.packageName })
    }
}
