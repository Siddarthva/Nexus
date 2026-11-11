package com.example.nexusremote.ui.screens.paircode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexusremote.R
import com.example.nexusremote.ui.AppViewModelProvider
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.ScreenHeader
import com.example.nexusremote.ui.components.SectionCard
import com.example.nexusremote.ui.components.describe

/**
 * "Enter the six-digit code shown on your TV." The code is the only input: the TV's IP and
 * ports come from discovery and are never shown or editable here.
 */
@Composable
fun PairCodeScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: PairCodeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tv = state.tv
    val displayName = state.savedName
        ?: tv?.mdnsId?.let { stringResource(R.string.tv_unnamed, it.take(16)) }
        ?: stringResource(R.string.tv_unnamed_generic)

    DisposableEffect(viewModel) {
        viewModel.startScanning()
        onDispose { viewModel.stopScanning() }
    }
    LaunchedEffect(viewModel) { viewModel.connected.collect { onConnected() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.pair_title), onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(displayName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

            when {
                tv == null && !state.paired -> SectionCard { BodyText(stringResource(R.string.paircode_tv_gone)) }
                state.waitingForPairingScreen -> SectionCard(title = stringResource(R.string.paircode_waiting_title)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        BodyText(stringResource(R.string.paircode_waiting_body), Modifier.weight(1f))
                    }
                }
            }

            if (!state.paired) {
                Text(stringResource(R.string.paircode_instruction), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCodeChange,
                    label = { Text(stringResource(R.string.paircode_field)) },
                    singleLine = true,
                    enabled = !state.busy,
                    textStyle = TextStyle(fontSize = 32.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (state.canPair) viewModel.pair(displayName) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                when (val step = state.step) {
                    PairCodeStep.Pairing, PairCodeStep.Connecting -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Text(
                            stringResource(
                                if (step == PairCodeStep.Pairing) R.string.pair_progress_pairing else R.string.pair_progress_connecting,
                            ),
                        )
                    }
                    PairCodeStep.WaitingForConnectPort -> BodyText(stringResource(R.string.paircode_wait_connect))
                    is PairCodeStep.Failed -> Text(step.errorCode.describe(context), color = MaterialTheme.colorScheme.error)
                    PairCodeStep.Idle -> Unit
                }
            }

            if (!state.paired) {
                Button(
                    onClick = { viewModel.pair(displayName) },
                    enabled = state.canPair,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.paircode_pair)) }
            } else {
                Button(
                    onClick = viewModel::connect,
                    enabled = state.canConnect,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.paircode_connect)) }
            }
        }
    }
}
