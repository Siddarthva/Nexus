package com.example.nexusremote.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorsTest {

    // ---- Package names --------------------------------------------------------------------

    @Test
    fun validPackageNamesAreAccepted() {
        listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "in.startv.hotstar",
            "org.videolan.vlc",
            "a.b",
            "com.example_app.Tv_2",
        ).forEach { assertTrue("$it should be valid", InputValidators.isValidPackageName(it)) }
    }

    @Test
    fun shellMetacharactersInPackageNamesAreRejected() {
        listOf(
            "com.evil;reboot", "com.evil&&rm", "com.evil|sh", "com.evil`id`", "com.evil\$(id)",
            "com.evil>out", "com.evil<in", "com.evil'x", "com.evil\"x", "com.evil\\x",
            "com.evil\nreboot", "com.evil*", "com.evil-x", "com/evil",
        ).forEach { assertFalse("$it must be rejected", InputValidators.isValidPackageName(it)) }
    }

    @Test
    fun spacesAndEmptyPackageNamesAreRejected() {
        listOf("", " ", "com.example app", " com.example", "com.example ", "com .example")
            .forEach { assertFalse("<$it> must be rejected", InputValidators.isValidPackageName(it)) }
    }

    @Test
    fun packageNamesStartingWithADigitAreRejected() {
        listOf("1com.example", "com.1example", "9.a").forEach {
            assertFalse("$it must be rejected", InputValidators.isValidPackageName(it))
        }
    }

    @Test
    fun malformedPackageStructureIsRejected() {
        listOf("example", "com.", ".com", "com..example", "_com.example")
            .forEach { assertFalse("$it must be rejected", InputValidators.isValidPackageName(it)) }
    }

    @Test
    fun overlongPackageNamesAreRejected() {
        val long = "a." + "b".repeat(InputValidators.MAX_PACKAGE_NAME_LENGTH)
        assertFalse(InputValidators.isValidPackageName(long))
    }

    // ---- Pairing code ----------------------------------------------------------------------

    @Test
    fun sixDigitCodeIsAccepted() {
        assertTrue(InputValidators.isValidPairingCode("123456"))
        assertTrue(InputValidators.isValidPairingCode("000000"))
    }

    @Test
    fun wrongLengthCodesAreRejected() {
        listOf("", "1", "12345", "1234567", "123456789").forEach {
            assertFalse("<$it> must be rejected", InputValidators.isValidPairingCode(it))
        }
    }

    @Test
    fun nonDigitCodesAreRejected() {
        listOf("12345a", "abcdef", "12 456", "12-456", "١٢٣٤٥٦", "12345 ", " 23456", "+12345").forEach {
            assertFalse("<$it> must be rejected", InputValidators.isValidPairingCode(it))
        }
    }

    // ---- Ports -----------------------------------------------------------------------------

    @Test
    fun portsInRangeAreAccepted() {
        assertEquals(1, InputValidators.parsePort("1"))
        assertEquals(37625, InputValidators.parsePort("37625"))
        assertEquals(65535, InputValidators.parsePort("65535"))
        assertEquals(41000, InputValidators.parsePort(" 41000 "))
    }

    @Test
    fun outOfRangeOrMalformedPortsAreRejected() {
        listOf("0", "65536", "99999", "-1", "", "abc", "12a", "123456", "1.5", "+80").forEach {
            assertNull("<$it> must be rejected", InputValidators.parsePort(it))
        }
        assertFalse(InputValidators.isValidPort(0))
        assertFalse(InputValidators.isValidPort(65536))
        assertFalse(InputValidators.isValidPort(null))
    }

    // ---- IPv4 / names / ids ----------------------------------------------------------------

    @Test
    fun ipv4Validation() {
        listOf("192.168.1.20", "10.0.0.1", "0.0.0.0", "255.255.255.255").forEach {
            assertTrue("$it should be valid", InputValidators.isValidIpv4(it))
        }
        listOf("256.1.1.1", "1.2.3", "1.2.3.4.5", "tv.local", "01.2.3.4", "1.2.3.-4", "", "::1", "1.2.3.4;id")
            .forEach { assertFalse("$it must be rejected", InputValidators.isValidIpv4(it)) }
    }

    @Test
    fun friendlyNameValidation() {
        assertTrue(InputValidators.isValidFriendlyName("Living room TV"))
        assertFalse(InputValidators.isValidFriendlyName("   "))
        assertFalse(InputValidators.isValidFriendlyName("x".repeat(InputValidators.MAX_FRIENDLY_NAME_LENGTH + 1)))
    }

    @Test
    fun deviceIdsMustBeCanonicalUuids() {
        assertTrue(InputValidators.isValidDeviceId("11111111-1111-4111-8111-111111111111"))
        assertFalse(InputValidators.isValidDeviceId("../../etc/passwd"))
        assertFalse(InputValidators.isValidDeviceId("1-1-1-1-1"))
        assertFalse(InputValidators.isValidDeviceId(""))
    }
}
