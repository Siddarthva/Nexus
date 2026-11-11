package com.example.nexusremote.domain.command

/**
 * The complete, closed set of remote actions the UI may request.
 * The UI never builds shell strings; it only picks one of these entries.
 */
sealed class TvCommand(val name: String) {
    data object HOME : TvCommand("HOME")
    data object BACK : TvCommand("BACK")
    data object UP : TvCommand("UP")
    data object DOWN : TvCommand("DOWN")
    data object LEFT : TvCommand("LEFT")
    data object RIGHT : TvCommand("RIGHT")
    data object SELECT : TvCommand("SELECT")
    data object VOLUME_UP : TvCommand("VOLUME_UP")
    data object VOLUME_DOWN : TvCommand("VOLUME_DOWN")
    data object MUTE : TvCommand("MUTE")
    data object POWER : TvCommand("POWER")
    data object PLAY_PAUSE : TvCommand("PLAY_PAUSE")
    data object REWIND : TvCommand("REWIND")
    data object FAST_FORWARD : TvCommand("FAST_FORWARD")

    override fun toString(): String = name

    companion object {
        val entries: List<TvCommand> by lazy {
            listOf(
                HOME, BACK, UP, DOWN, LEFT, RIGHT, SELECT,
                VOLUME_UP, VOLUME_DOWN, MUTE, POWER,
                PLAY_PAUSE, REWIND, FAST_FORWARD,
            )
        }
    }
}
