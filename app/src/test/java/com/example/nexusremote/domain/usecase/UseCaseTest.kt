package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.fakes.FakeEnvironment
import com.example.nexusremote.fakes.FakeScenario
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCaseTest {

    private fun req(code: String, port: Int = 37000, host: String = "192.0.2.10", id: String = TestData.LIVING_ROOM_ID) =
        PairingRequest(id, host, port, code.toCharArray())

    @Test
    fun pairRejectsWrongLengthNonDigitAndBadPortsWithoutCallingTheClient() = runTest {
        val env = FakeEnvironment(backgroundScope)
        listOf(req("12345"), req("1234567"), req("12a456"), req("123456", port = 0), req("123456", port = 70000),
            req("123456", host = "999.1.1.1"), req("123456", id = "../x"))
            .forEach { request ->
                val result = env.container.pairTv(request)
                assertEquals(ErrorCode.INVALID_INPUT, (result as NexusResult.Failure).errorCode)
                assertTrue("code must be wiped even on rejection", request.codeCopy().all { it == '\u0000' })
            }
        assertTrue(env.client.pairCalls.isEmpty())
    }

    @Test
    fun pairPassesValidRequestToClient() = runTest {
        val env = FakeEnvironment(backgroundScope)
        assertTrue(env.container.pairTv(req("123456")).isSuccess)
        assertEquals(listOf("192.0.2.10" to 37000), env.client.pairCalls)
    }

    @Test
    fun pairWrongCodeIsSurfaced() = runTest {
        val env = FakeEnvironment(backgroundScope)
        env.client.script(FakeScenario.WRONG_CODE)
        val result = env.container.pairTv(req("123456"))
        assertEquals(ErrorCode.PAIRING_CODE_WRONG, (result as NexusResult.Failure).errorCode)
    }

    @Test
    fun connectSavesLastConnectedAndMakesDeviceActive() = runTest {
        val env = FakeEnvironment(backgroundScope, devices = listOf(TestData.livingRoom, TestData.bedroom), activeId = TestData.LIVING_ROOM_ID)
        val result = env.container.connectTv(TestData.bedroom)
        assertTrue(result.isSuccess)
        assertEquals(TestData.BEDROOM_ID, env.deviceRepo.activeIdState.value)
        assertTrue(env.deviceRepo.getDevice(TestData.BEDROOM_ID)!!.lastConnectedAt != null)
        assertEquals(ConnectionState.Connected(TestData.BEDROOM_ID), env.client.connectionState.value)
    }

    @Test
    fun connectUsesDiscoveredPortWhenSavedPortIsStale() = runTest {
        val env = FakeEnvironment(backgroundScope, devices = listOf(TestData.livingRoom))
        // The saved port (40001) is stale after a TV reboot; only the discovered port answers.
        env.client.connectHandler = { device ->
            if (device.connectPort == 45555) NexusResult.unit() else NexusResult.Failure(ErrorCode.WIRELESS_DEBUG_OFF, "x")
        }
        env.discovery.port = 45555
        val result = env.container.connectTv(TestData.livingRoom) as NexusResult.Success
        assertEquals(listOf(40001, 45555), env.client.connectCalls.map { it.connectPort })
        assertEquals(1, env.discovery.calls)
        assertEquals(45555, result.value.connectPort)
        assertEquals(45555, env.deviceRepo.getDevice(TestData.LIVING_ROOM_ID)!!.connectPort)
    }

    @Test
    fun connectDoesNotUseDiscoveryForAuthErrors() = runTest {
        val env = FakeEnvironment(backgroundScope, devices = listOf(TestData.livingRoom))
        env.client.script(FakeScenario.AUTH_REVOKED)
        env.discovery.port = 45555
        val result = env.container.connectTv(TestData.livingRoom)
        assertEquals(ErrorCode.AUTH_REVOKED, (result as NexusResult.Failure).errorCode)
        assertEquals(0, env.discovery.calls)
    }

    @Test
    fun forgetRemovesProfileKeyAndDisconnectsIfConnected() = runTest {
        val env = FakeEnvironment(
            backgroundScope,
            devices = listOf(TestData.livingRoom, TestData.bedroom),
            activeId = TestData.LIVING_ROOM_ID,
            connection = ConnectionState.Connected(TestData.LIVING_ROOM_ID),
        )
        env.credentials.putKey(TestData.LIVING_ROOM_ID)
        env.container.forgetDevice(TestData.LIVING_ROOM_ID)

        assertNull(env.deviceRepo.getDevice(TestData.LIVING_ROOM_ID))
        assertFalse(env.credentials.hasCredentials(TestData.LIVING_ROOM_ID))
        assertEquals(1, env.client.disconnectCalls)
        assertEquals("next saved TV becomes active", TestData.BEDROOM_ID, env.deviceRepo.activeDevice.first()?.id)
    }

    @Test
    fun saveRejectsInvalidProfiles() = runTest {
        val env = FakeEnvironment(backgroundScope)
        listOf(
            TestData.livingRoom.copy(friendlyName = " "),
            TestData.livingRoom.copy(host = "tv.local"),
            TestData.livingRoom.copy(connectPort = 0),
            TestData.livingRoom.copy(id = "not-a-uuid"),
        ).forEach {
            assertEquals(ErrorCode.INVALID_INPUT, (env.container.saveDevice(it) as NexusResult.Failure).errorCode)
        }
        assertTrue(env.deviceRepo.deviceState.value.isEmpty())
    }
}
