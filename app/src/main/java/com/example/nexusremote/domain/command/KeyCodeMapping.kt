package com.example.nexusremote.domain.command

/**
 * Fixed, read-only mapping from [TvCommand] to Android KeyEvent key codes.
 * The `when` is exhaustive, so adding a command without a key code fails to compile.
 */
object KeyCodeMapping {
    const val KEYCODE_HOME = 3
    const val KEYCODE_BACK = 4
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_POWER = 26
    const val KEYCODE_VOLUME_MUTE = 164
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_REWIND = 89
    const val KEYCODE_MEDIA_FAST_FORWARD = 90

    fun keyCodeFor(command: TvCommand): Int = when (command) {
        TvCommand.HOME -> KEYCODE_HOME
        TvCommand.BACK -> KEYCODE_BACK
        TvCommand.UP -> KEYCODE_DPAD_UP
        TvCommand.DOWN -> KEYCODE_DPAD_DOWN
        TvCommand.LEFT -> KEYCODE_DPAD_LEFT
        TvCommand.RIGHT -> KEYCODE_DPAD_RIGHT
        TvCommand.SELECT -> KEYCODE_DPAD_CENTER
        TvCommand.VOLUME_UP -> KEYCODE_VOLUME_UP
        TvCommand.VOLUME_DOWN -> KEYCODE_VOLUME_DOWN
        TvCommand.MUTE -> KEYCODE_VOLUME_MUTE
        TvCommand.POWER -> KEYCODE_POWER
        TvCommand.PLAY_PAUSE -> KEYCODE_MEDIA_PLAY_PAUSE
        TvCommand.REWIND -> KEYCODE_MEDIA_REWIND
        TvCommand.FAST_FORWARD -> KEYCODE_MEDIA_FAST_FORWARD
    }

    /** Commands that may auto-repeat while the button is held. */
    fun isRepeatable(command: TvCommand): Boolean = when (command) {
        TvCommand.UP, TvCommand.DOWN, TvCommand.LEFT, TvCommand.RIGHT,
        TvCommand.VOLUME_UP, TvCommand.VOLUME_DOWN -> true
        else -> false
    }
}
