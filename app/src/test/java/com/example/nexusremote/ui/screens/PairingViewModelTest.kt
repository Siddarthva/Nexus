package com.example.nexusremote.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.fakes.FakeEnvironment
import com.example.nexusremote.fakes.FakeScenario
import com.example.nexusremote.fakes.TestData
import com.example.nexusremote.testutil.MainDispatcherRule
import com.example.nexusremote.ui.navigation.Routes
import com.example.nexusremote.ui.screens.pairing.PairingForm
import com.example.nexusremote.ui.screens.pairing.PairingStep
import com.example.nexusremote.ui.screens.pairing.PairingValidation
import com.example.nexusremote.ui.screens.pairing.PairingViewModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PairingViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun TestScope.vm(env: FakeEnvironment = FakeEnvironment(backgroundScope), deviceId: String? = null) =
        PairingViewModel(SavedStateHandle(deviceId?.let { mapOf(Routes.PAIR_ARG_DEVICE_ID to it) } ?: emptyMap()), env.container)

    private fun PairingViewModel.fillValid(code: String = "123456") {
        onNameChange("Living room TV")
        onIpChange("192.168.1.20")
        onPairingPortChange("37000")
        onConnectPortChange("41000")
        onCodeChange(code)
    }

    // ---- Validation ---------------------------------------------------------------------------

    @Test
    fun wrongLengthCodeIsRejected() {
        val base = PairingForm("TV", "192.168.1.20", "37000", "41000", "12345")
        assertEquals(R.string.pair_error_code, PairingValidation.validate(base, showEmpty = true).code)
        assertEquals(R.string.pair_error_code, PairingValidation.validate(base.copy(code = "1234567"), true).code)
    }

    @Test
    fun nonDigitCodeIsRejected() {
        val base = PairingForm("TV", "192.168.1.20", "37000", "41000", "12a456")
        assertEquals(R.string.pair_error_code, PairingValidation.validate(base, showEmpty = true).code)
    }

    @Test
    fun outOfRangePortsAreRejected() {
        val base = PairingForm("TV", "192.168.1.20", "0", "65536", "123456")
        val errors = PairingValidation.validate(base, showEmpty = true)
        assertEquals(R.string.pair_error_port, errors.pairingPort)
        assertEquals(R.string.pair_error_port, errors.connectPort)
    }

    @Test
    fun liveValidationIgnoresUntouchedEmptyFieldsButSubmitDoesNot() {
        val empty = PairingForm()
        assertFalse(PairingValidation.validate(empty, showEmpty = false).hasAny)
        assertTrue(PairingValidation.validate(empty, showEmpty = true).hasAny)
    }

    @Test
    fun inputFiltersStripNonDigitsAndLimitLength() = runTest {
        val vm = vm()
        vm.onCodeChange("12a3b4567")
        assertEquals("123456", vm.uiState.value.form.code)
        vm.onPairingPortChange("3x70001")
        assertEquals("37000", vm.uiState.value.form.pairingPort)
    }

    @Test
    fun pairButtonDisabledUntilFormIsValid() = runTest {
        val vm = vm()
        assertFalse(vm.uiState.value.canSubmit)
        vm.onNameChange("TV")
        vm.onIpChange("192.168.1.20")
        vm.onPairingPortChange("37000")
        vm.onConnectPortChange("41000")
        assertFalse("code missing", vm.uiState.value.canSubmit)
        vm.onCodeChange("12345")
        assertFalse("code too short", vm.uiState.value.canSubmit)
        assertEquals(R.string.pair_error_code, vm.uiState.value.errors.code)
        vm.onCodeChange("123456")
        assertTrue(vm.uiState.value.canSubmit)
        vm.onIpChange("999.1.1.1")
        assertFalse(vm.uiState.value.canSubmit)
        assertEquals(R.string.pair_error_ip, vm.uiState.value.errors.ip)
    }

    // ---- Flow ---------------------------------------------------------------------------------

    @Test
    fun successfulPairSavesConnectsAndClearsCode() = runTest {
        val env = FakeEnvironment(backgroundScope)
        val vm = vm(env)
        vm.fillValid()
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.step is PairingStep.Success)
        assertEquals("", vm.uiState.value.form.code)
        assertEquals(listOf("192.168.1.20" to 37000), env.client.pairCalls)
        val saved = env.deviceRepo.deviceState.value.single()
        assertEquals(41000, saved.connectPort)
        assertNull("pairing port never persisted", saved.pairingPort)
        assertEquals(saved.id, env.deviceRepo.activeIdState.value)
        assertEquals(ConnectionState.Connected(saved.id), env.client.connectionState.value)
    }

    @Test
    fun wrongCodeShowsFailedStepAndSavesNothing() = runTest {
        val env = FakeEnvironment(backgroundScope)
        env.client.script(FakeScenario.WRONG_CODE)
        val vm = vm(env)
        vm.fillValid()
        vm.submit()
        advanceUntilIdle()
        assertEquals(PairingStep.Failed(ErrorCode.PAIRING_CODE_WRONG), vm.uiState.value.step)
        assertEquals("", vm.uiState.value.form.code)
        assertTrue(env.deviceRepo.deviceState.value.isEmpty())
        assertFalse("must re-enter the code before retrying", vm.uiState.value.canSubmit)
    }

    @Test
    fun pairedButConnectFailureAllowsRetryWithNewPort() = runTest {
        val env = FakeEnvironment(backgroundScope)
        env.client.script(FakeScenario.TIMEOUT)
        env.client.pairResult = com.example.nexusremote.util.NexusResult.unit()
        val vm = vm(env)
        vm.fillValid()
        vm.submit()
        advanceUntilIdle()
        assertEquals(PairingStep.PairedNotConnected(ErrorCode.CONNECT_TIMEOUT), vm.uiState.value.step)

        env.client.script(FakeScenario.SUCCESS)
        vm.onConnectPortChange("42000")
        vm.retryConnect()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.step is PairingStep.Success)
        assertEquals(42000, env.client.connectCalls.last().connectPort)
    }

    @Test
    fun invalidSubmitDoesNotCallClient() = runTest {
        val env = FakeEnvironment(backgroundScope)
        val vm = vm(env)
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errors.hasAny)
        assertTrue(env.client.pairCalls.isEmpty())
    }

    @Test
    fun repairPrefillsExistingDevice() = runTest {
        val env = FakeEnvironment(backgroundScope, devices = listOf(TestData.livingRoom))
        val vm = vm(env, deviceId = TestData.LIVING_ROOM_ID)
        advanceUntilIdle()
        val form = vm.uiState.value.form
        assertEquals(TestData.livingRoom.friendlyName, form.name)
        assertEquals(TestData.livingRoom.host, form.ip)
        assertEquals(TestData.livingRoom.connectPort.toString(), form.connectPort)
    }
}
