package com.example.nexusremote.adb

import com.example.nexusremote.domain.command.KeyCodeMapping
import com.example.nexusremote.domain.command.OpenSettingsCommand
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.util.InputValidators
import com.example.nexusremote.util.ShellTextEscaper

/**
 * The ONLY shell commands NEXUS can run. The constructor is private, so a command can be
 * created only through these factories, each of which builds a fixed string from validated
 * input. [toString] is redacted so payloads (e.g. typed text) cannot end up in logs.
 */
internal class AdbShellCommand private constructor(
    val shell: String,
    /** Safe, payload-free label for logs. */
    val label: String,
) {
    override fun toString(): String = "AdbShellCommand($label)"

    companion object {
        fun keyEvent(command: TvCommand): AdbShellCommand =
            AdbShellCommand("input keyevent ${KeyCodeMapping.keyCodeFor(command)}", "keyevent:${command.name}")

        /** [escapedArgument] must come from [ShellTextEscaper.Outcome.Escaped]. */
        fun inputText(escapedArgument: ShellTextEscaper.Outcome.Escaped): AdbShellCommand =
            AdbShellCommand("input text ${escapedArgument.argument}", "input-text")

        /** Lists launchable TV (leanback) activities as `package/class` lines. */
        val LIST_LEANBACK_APPS = AdbShellCommand(
            "cmd package query-activities --brief -a android.intent.action.MAIN " +
                "-c android.intent.category.LEANBACK_LAUNCHER",
            "list-leanback-apps",
        )

        /** Fallback listing via the package manager shell service (`package:<name>` lines). */
        val LIST_PACKAGES = AdbShellCommand("pm list packages", "list-packages")

        /**
         * Opens one fixed TV settings page. The action comes only from the closed
         * [OpenSettingsCommand] enum. No UI string is involved, and nothing is written to the TV.
         */
        fun openSettings(command: OpenSettingsCommand): AdbShellCommand =
            AdbShellCommand("am start -a ${command.action}", "settings:${command.name}")

        fun launchLeanback(packageName: String): AdbShellCommand =
            launch(packageName, "android.intent.category.LEANBACK_LAUNCHER", "launch-leanback")

        fun launchLauncher(packageName: String): AdbShellCommand =
            launch(packageName, "android.intent.category.LAUNCHER", "launch-launcher")

        private fun launch(packageName: String, category: String, label: String): AdbShellCommand {
            require(InputValidators.isValidPackageName(packageName)) { "Invalid package name" }
            return AdbShellCommand("monkey -p $packageName -c $category 1", label)
        }
    }
}
