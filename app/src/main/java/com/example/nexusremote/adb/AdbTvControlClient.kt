package com.example.nexusremote.adb

import com.example.nexusremote.adb.AdbErrorMapper.Operation
import com.example.nexusremote.data.security.HostKeyStore
import com.example.nexusremote.domain.command.OpenSettingsCommand
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.domain.model.TvApp
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.NexusResult
import com.example.nexusremote.util.SafeLog
import com.example.nexusremote.util.ShellTextEscaper
import com.example.nexusremote.util.retryWithBackoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [TvControlClient] implemented over ADB. Depends only on [AdbTransport] and [HostKeyStore].
 *
 * - All transport work runs on [io] and is serialised by a mutex (one ADB session).
 * - Connects use bounded retries (max 3, exponential backoff); auth failures are never retried.
 * - Commands are sent exactly once: a key event is never re-sent after a failure, because
 *   it may already have reached the TV.
 */
class AdbTvControlClient internal constructor(
    private val transport: AdbTransport,
    private val hostKeys: HostKeyStore,
    private val appScope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TvControlClient {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var currentDevice: TvDevice? = null
    private var monitorJob: Job? = null

    override suspend fun pair(request: PairingRequest): NexusResult<Unit> = withContext(io) {
        mutex.withLock {
            setState(ConnectionState.Pairing)
            val chars = request.codeCopy()
            val codeBytes = ByteArray(chars.size) { chars[it].code.toByte() }
            chars.fill('\u0000')
            try {
                val keyPair = hostKeys.getOrCreateKeyPair(request.deviceId)
                transport.pair(request.host, request.pairingPort, codeBytes, keyPair)
                SafeLog.event(TAG, "pairing succeeded")
                NexusResult.unit()
            } catch (e: AdbTransportException) {
                val code = AdbErrorMapper.map(e.kind, Operation.PAIR, previouslyConnected = false)
                SafeLog.error(TAG, "pair", code, e.cause)
                NexusResult.Failure(code, "Pairing failed")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Keystore failures, or anything the transport did not classify.
                unexpected("pair", t)
            } finally {
                codeBytes.fill(0)
                request.clear()
                // Pairing uses its own socket; restore whatever the session state was.
                restoreIdleState()
            }
        }
    }

    override suspend fun connect(device: TvDevice): NexusResult<Unit> = withContext(io) {
        mutex.withLock { connectLocked(device) }
    }

    private suspend fun connectLocked(device: TvDevice): NexusResult<Unit> {
        setState(ConnectionState.Connecting)
        transport.close()
        val keyPair = try {
            hostKeys.loadKeyPair(device.id)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SafeLog.error(TAG, "load-key", ErrorCode.NOT_PAIRED, t)
            null
        }
        if (keyPair == null) {
            currentDevice = null
            return fail(ErrorCode.NOT_PAIRED, "No host key for device", "connect")
        }

        val result = retryWithBackoff(
            maxAttempts = MAX_CONNECT_ATTEMPTS,
            shouldRetry = { it.errorCode in RETRYABLE_CONNECT_CODES },
        ) { attempt ->
            SafeLog.event(TAG, "connect attempt $attempt")
            try {
                transport.connect(device.host, device.connectPort, keyPair)
                NexusResult.unit()
            } catch (e: AdbTransportException) {
                val code = AdbErrorMapper.map(e.kind, Operation.CONNECT, device.lastConnectedAt != null)
                SafeLog.error(TAG, "connect", code, e.cause)
                NexusResult.Failure(code, "Connect failed")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // UNSUPPORTED / UNKNOWN are never retried.
                unexpected("connect", t)
            }
        }

        return when (result) {
            is NexusResult.Success -> {
                currentDevice = device
                setState(ConnectionState.Connected(device.id))
                startMonitor()
                result
            }
            is NexusResult.Failure -> {
                currentDevice = null
                transport.close()
                setState(ConnectionState.Failed(result.errorCode, result.message))
                result
            }
        }
    }

    override suspend fun sendCommand(command: TvCommand): NexusResult<Unit> =
        runShell(AdbShellCommand.keyEvent(command)).map { }

    override suspend fun sendText(text: String): NexusResult<Unit> =
        when (val outcome = ShellTextEscaper.escape(text)) {
            is ShellTextEscaper.Outcome.Escaped -> runShell(AdbShellCommand.inputText(outcome)).map { }
            ShellTextEscaper.Outcome.Empty -> NexusResult.Failure(ErrorCode.INVALID_INPUT, "Empty text")
            ShellTextEscaper.Outcome.TooLong -> NexusResult.Failure(ErrorCode.INVALID_INPUT, "Text too long")
            is ShellTextEscaper.Outcome.Unsupported ->
                NexusResult.Failure(ErrorCode.INVALID_INPUT, "Unsupported characters")
        }

    // UNVERIFIED-ON-HARDWARE: monkey's exit code / "No activities found" output on the target
    // TV firmware, and whether leanback-only apps launch via the LEANBACK_LAUNCHER category.
    override suspend fun launchPackage(packageName: String): NexusResult<Unit> {
        val leanback = try {
            AdbShellCommand.launchLeanback(packageName)
        } catch (_: IllegalArgumentException) {
            return NexusResult.Failure(ErrorCode.INVALID_INPUT, "Invalid package name")
        }
        val first = runShell(leanback)
        if (first is NexusResult.Success && launchSucceeded(first.value)) return NexusResult.unit()
        if (first is NexusResult.Failure && first.errorCode != ErrorCode.COMMAND_FAILED) return first

        return when (val second = runShell(AdbShellCommand.launchLauncher(packageName))) {
            is NexusResult.Success ->
                if (launchSucceeded(second.value)) NexusResult.unit()
                else NexusResult.Failure(ErrorCode.COMMAND_FAILED, "No launchable activity")
            is NexusResult.Failure -> second
        }
    }

    // UNVERIFIED-ON-HARDWARE: `cmd package query-activities --brief` output format on the TV.
    override suspend fun listInstalledPackages(): NexusResult<List<TvApp>> {
        val leanback = runShell(AdbShellCommand.LIST_LEANBACK_APPS)
        if (leanback is NexusResult.Success && leanback.value.exitCode == 0) {
            val apps = PackageListParser.parseActivityListing(leanback.value.stdout)
            if (apps.isNotEmpty()) return NexusResult.Success(apps)
        }
        if (leanback is NexusResult.Failure && leanback.errorCode != ErrorCode.COMMAND_FAILED) return leanback
        return runShell(AdbShellCommand.LIST_PACKAGES).map { PackageListParser.parsePmListing(it.stdout) }
    }

    /**
     * Sends `am start -a <fixed action>` once. `am` usually exits 0 even when no activity
     * matches, so the output is checked too. A screen that does not open is reported as
     * UNSUPPORTED and is never retried.
     */
    // UNVERIFIED-ON-HARDWARE: which of these actions the Haier TV's settings app handles, and
    // the exact `am start` error text on its firmware.
    override suspend fun openSettings(command: OpenSettingsCommand): NexusResult<Unit> =
        when (val result = runShell(AdbShellCommand.openSettings(command))) {
            is NexusResult.Success ->
                if (activityStarted(result.value)) NexusResult.unit()
                else NexusResult.Failure(ErrorCode.UNSUPPORTED, "Settings screen not available")
            is NexusResult.Failure -> result
        }

    private fun activityStarted(output: ShellOutput): Boolean {
        val text = output.stdout + output.stderr
        return output.exitCode == 0 &&
            !text.contains("Error", ignoreCase = true) &&
            !text.contains("unable to resolve", ignoreCase = true) &&
            !text.contains("Exception", ignoreCase = true)
    }

    override suspend fun disconnect() = withContext(io) {
        mutex.withLock {
            monitorJob?.cancel()
            transport.close()
            currentDevice = null
            setState(ConnectionState.Disconnected)
        }
    }

    /** Runs one shell command once. Reconnects first (bounded) if the session silently dropped. */
    private suspend fun runShell(command: AdbShellCommand): NexusResult<ShellOutput> = withContext(io) {
        mutex.withLock {
            if (!transport.isConnected) {
                val device = currentDevice
                    ?: return@withLock NexusResult.Failure(ErrorCode.COMMAND_FAILED, "Not connected")
                val reconnect = connectLocked(device)
                if (reconnect is NexusResult.Failure) return@withLock reconnect
            }
            try {
                val output = transport.shell(command)
                if (output.exitCode != 0 && command.label.startsWith("keyevent")) {
                    SafeLog.error(TAG, command.label, ErrorCode.COMMAND_FAILED)
                    NexusResult.Failure(ErrorCode.COMMAND_FAILED, "Non-zero exit")
                } else {
                    NexusResult.Success(output)
                }
            } catch (e: AdbTransportException) {
                handleShellFailure(command, e)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                unexpected(command.label, t)
            }
        }
    }

    private fun handleShellFailure(command: AdbShellCommand, e: AdbTransportException): NexusResult.Failure {
        val code = AdbErrorMapper.map(e.kind, Operation.COMMAND, previouslyConnected = true)
        SafeLog.error(TAG, command.label, code, e.cause)
        when (e.kind) {
            AdbTransportException.Kind.NOT_PAIRED, AdbTransportException.Kind.AUTH_FAILED -> {
                // Never loop on auth errors: drop the session and require re-pairing.
                transport.close()
                currentDevice = null
                setState(ConnectionState.Failed(ErrorCode.AUTH_REVOKED, "Authorization revoked"))
            }
            AdbTransportException.Kind.CLOSED, AdbTransportException.Kind.TIMEOUT,
            AdbTransportException.Kind.UNREACHABLE -> {
                transport.close()
                setState(ConnectionState.Disconnected)
            }
            else -> Unit
        }
        return NexusResult.Failure(code, "Command failed")
    }

    /**
     * Last-resort mapping for anything that was not an [AdbTransportException], including
     * Errors (e.g. NoSuchFieldError). Only the exception CLASS name and the stable ErrorCode
     * are logged; the user sees only the ErrorCode's text. Never rethrown, so nothing can reach
     * the main thread's uncaught-exception handler.
     */
    private fun unexpected(operation: String, t: Throwable): NexusResult.Failure {
        val code = if (t is LinkageError || t is ExceptionInInitializerError) ErrorCode.UNSUPPORTED else ErrorCode.UNKNOWN
        SafeLog.error(TAG, operation, code, t)
        return NexusResult.Failure(code, "Operation failed")
    }

    private fun launchSucceeded(output: ShellOutput): Boolean {
        val text = output.stdout + output.stderr
        return output.exitCode == 0 &&
            !text.contains("No activities found", ignoreCase = true) &&
            !text.contains("monkey aborted", ignoreCase = true)
    }

    /** Detects a session that drops while idle (TV asleep, Wi-Fi change) and updates state. */
    // UNVERIFIED-ON-HARDWARE: how quickly adb-kt's AdbClient.isClosed flips when the TV
    // sleeps, reboots or leaves Wi-Fi (a half-open TCP socket may stay "open" for a long time).
    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = appScope.launch(io) {
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)
                val state = _state.value
                if (state is ConnectionState.Connected && !transport.isConnected) {
                    setState(ConnectionState.Disconnected)
                }
                if (state !is ConnectionState.Connected) break
            }
        }
    }

    private fun restoreIdleState() {
        val device = currentDevice
        setState(
            if (device != null && transport.isConnected) ConnectionState.Connected(device.id)
            else ConnectionState.Disconnected,
        )
    }

    private fun fail(code: ErrorCode, message: String, operation: String): NexusResult.Failure {
        SafeLog.error(TAG, operation, code)
        setState(ConnectionState.Failed(code, message))
        return NexusResult.Failure(code, message)
    }

    private fun setState(state: ConnectionState) {
        if (_state.value != state) {
            _state.value = state
            SafeLog.state(TAG, state)
        }
    }

    private companion object {
        const val TAG = "AdbTvControlClient"
        const val MAX_CONNECT_ATTEMPTS = 3
        const val MONITOR_INTERVAL_MS = 3_000L
        val RETRYABLE_CONNECT_CODES = setOf(ErrorCode.CONNECT_TIMEOUT, ErrorCode.NETWORK_UNREACHABLE)
    }
}
