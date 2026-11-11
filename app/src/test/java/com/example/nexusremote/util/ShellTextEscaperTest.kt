package com.example.nexusremote.util

import com.example.nexusremote.util.ShellTextEscaper.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellTextEscaperTest {

    private fun escaped(text: String): String {
        val outcome = ShellTextEscaper.escape(text)
        assertTrue("Expected Escaped for <$text> but was $outcome", outcome is Outcome.Escaped)
        return (outcome as Outcome.Escaped).argument
    }

    @Test
    fun lettersAndDigitsAreWrappedInSingleQuotes() {
        assertEquals("'abcXYZ019'", escaped("abcXYZ019"))
    }

    @Test
    fun spaceBecomesPercentS() {
        assertEquals("'hello%sworld'", escaped("hello world"))
        assertEquals("'%s%s'", escaped("  "))
    }

    @Test
    fun singleQuoteIsClosedEscapedAndReopened() {
        assertEquals("'it'\\''s'", escaped("it's"))
    }

    @Test
    fun everyAllowedPunctuationCharacterIsAccepted() {
        ShellTextEscaper.ALLOWED_PUNCTUATION.forEach { c ->
            assertTrue("'$c' should be supported", ShellTextEscaper.isSupported(c))
            assertTrue(ShellTextEscaper.escape("a${c}b") is Outcome.Escaped)
        }
    }

    @Test
    fun shellMetacharactersStayLiteralInsideQuotes() {
        // & = ( ) " # * are allowed; inside single quotes the shell does not interpret them.
        assertEquals("'a&b=c%s(1+2)%s\"x\"#*'", escaped("a&b=c (1+2) \"x\"#*"))
    }

    @Test
    fun escapedOutputNeverContainsAnUnquotedSegmentExceptTheQuoteEscape() {
        val out = escaped("it's a 'test'")
        // Removing every '\'' sequence must leave a single quoted string with no other quotes inside.
        val stripped = out.replace("'\\''", "")
        assertTrue(stripped.startsWith("'") && stripped.endsWith("'"))
        assertFalse(stripped.substring(1, stripped.length - 1).contains('\''))
    }

    @Test
    fun dangerousCharactersAreRejected() {
        val rejected = listOf('%', '\\', '`', '$', ';', '|', '<', '>', '\n', '\r', '\t', '~', '{', '}', '[', ']', '^')
        rejected.forEach { c ->
            assertFalse("'$c' must not be supported", ShellTextEscaper.isSupported(c))
            val outcome = ShellTextEscaper.escape("ok${c}ok")
            assertTrue("Expected Unsupported for ${c.code}", outcome is Outcome.Unsupported)
            assertEquals(listOf(c), (outcome as Outcome.Unsupported).rejected)
        }
    }

    @Test
    fun nonAsciiIsRejected() {
        listOf("café", "日本", "emoji 😀", "ñ", "Ä").forEach { text ->
            assertTrue("Expected Unsupported for <$text>", ShellTextEscaper.escape(text) is Outcome.Unsupported)
        }
    }

    @Test
    fun unsupportedCharactersAreReportedOnceInOrder() {
        val outcome = ShellTextEscaper.escape("a\$b;c\$d;") as Outcome.Unsupported
        assertEquals(listOf('$', ';'), outcome.rejected)
    }

    @Test
    fun emptyInputIsRejected() {
        assertEquals(Outcome.Empty, ShellTextEscaper.escape(""))
    }

    @Test
    fun maximumLengthIsAcceptedAndOneMoreIsRejected() {
        assertEquals(300, ShellTextEscaper.MAX_LENGTH)
        assertTrue(ShellTextEscaper.escape("a".repeat(ShellTextEscaper.MAX_LENGTH)) is Outcome.Escaped)
        assertEquals(Outcome.TooLong, ShellTextEscaper.escape("a".repeat(ShellTextEscaper.MAX_LENGTH + 1)))
    }

    @Test
    fun lengthIsCheckedBeforeCharacterSet() {
        assertEquals(Outcome.TooLong, ShellTextEscaper.escape("$".repeat(ShellTextEscaper.MAX_LENGTH + 1)))
    }
}
