package com.example.nexusremote.domain.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyCodeMappingTest {

    /** The specification table, written out independently of the production constants. */
    private val spec = mapOf(
        TvCommand.HOME to 3,
        TvCommand.BACK to 4,
        TvCommand.UP to 19,
        TvCommand.DOWN to 20,
        TvCommand.LEFT to 21,
        TvCommand.RIGHT to 22,
        TvCommand.SELECT to 23,
        TvCommand.VOLUME_UP to 24,
        TvCommand.VOLUME_DOWN to 25,
        TvCommand.POWER to 26,
        TvCommand.MUTE to 164,
        TvCommand.PLAY_PAUSE to 85,
        TvCommand.REWIND to 89,
        TvCommand.FAST_FORWARD to 90,
    )

    @Test
    fun everyCommandMapsToTheSpecifiedKeycode() {
        spec.forEach { (command, expected) ->
            assertEquals("Keycode for $command", expected, KeyCodeMapping.keyCodeFor(command))
        }
    }

    @Test
    fun specCoversEveryCommandExactlyOnce() {
        assertEquals(14, TvCommand.entries.size)
        assertEquals(TvCommand.entries.toSet(), spec.keys)
        assertEquals(TvCommand.entries.size, TvCommand.entries.toSet().size)
    }

    @Test
    fun keycodesAreUnique() {
        val codes = TvCommand.entries.map(KeyCodeMapping::keyCodeFor)
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun onlyDpadArrowsAndVolumeRepeat() {
        val repeatable = TvCommand.entries.filter(KeyCodeMapping::isRepeatable).toSet()
        assertEquals(
            setOf(TvCommand.UP, TvCommand.DOWN, TvCommand.LEFT, TvCommand.RIGHT, TvCommand.VOLUME_UP, TvCommand.VOLUME_DOWN),
            repeatable,
        )
        assertFalse(KeyCodeMapping.isRepeatable(TvCommand.POWER))
        assertFalse(KeyCodeMapping.isRepeatable(TvCommand.SELECT))
        assertTrue(KeyCodeMapping.isRepeatable(TvCommand.VOLUME_UP))
    }
}
