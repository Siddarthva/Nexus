package com.example.nexusremote.ui.screens.pairing

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexusremote.R
import com.example.nexusremote.ui.AppViewModelProvider
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.ScreenHeader
import com.example.nexusremote.ui.components.SectionCard
import com.example.nexusremote.ui.components.describe
import com.example.nexusremote.ui.components.rememberLocalNetworkGate
import com.example.nexusremote.ui.components.resolve

@Composable
fun PairingScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenRemote: () -> Unit,
    viewModel: PairingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val defaultName = stringResource(R.string.pair_field_name_default)
    val lanGate = rememberLocalNetworkGate(onDenied = viewModel::onLocalNetworkDenied)

    LaunchedEffect(Unit) { viewModel.setDefaultName(defaultName) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
            }
            ScreenHeader(title = stringResource(R.string.pair_title), modifier = Modifier.weight(1f))
        }
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PairingInstructions(brand = state.form.brand, onBrandChange = viewModel::onBrandChange, enabled = !state.busy)
            PairingFields(state = state, viewModel = viewModel)
            PairingStatus(
                state = state,
                onSubmit = { lanGate { viewModel.submit() } },
                onRetryConnect = { lanGate { viewModel.retryConnect() } },
                onOpenRemote = onOpenRemote,
                errorText = { code -> code.describe(context) },
            )
        }
    }
}

@Composable
private fun PairingFields(state: PairingUiState, viewModel: PairingViewModel) {
    val form = state.form
    val errors = state.errors
    val enabled = !state.busy
    SectionCard(title = stringResource(R.string.pair_details_title)) {
        LabeledField(
            value = form.name,
            onValueChange = viewModel::onNameChange,
            label = stringResource(R.string.pair_field_name),
            error = errors.name?.let { stringResource(it) },
            enabled = enabled,
        )
        LabeledField(
            value = form.ip,
            onValueChange = viewModel::onIpChange,
            label = stringResource(R.string.pair_field_ip),
            placeholder = stringResource(R.string.pair_field_ip_hint),
            error = errors.ip?.let { stringResource(it) },
            keyboardType = KeyboardType.Decimal,
            enabled = enabled,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField(
                value = form.pairingPort,
                onValueChange = viewModel::onPairingPortChange,
                label = stringResource(R.string.pair_field_pairing_port),
                error = errors.pairingPort?.let { stringResource(it) },
                keyboardType = KeyboardType.Number,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                value = form.connectPort,
                onValueChange = viewModel::onConnectPortChange,
                label = stringResource(R.string.pair_field_connect_port),
                error = errors.connectPort?.let { stringResource(it) },
                keyboardType = KeyboardType.Number,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.samePortWarning) {
            Text(
                text = stringResource(R.string.pair_warning_same_port),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        LabeledField(
            value = form.code,
            onValueChange = viewModel::onCodeChange,
            label = stringResource(R.string.pair_field_code),
            error = errors.code?.let { stringResource(it) },
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            isSecret = true,
            enabled = enabled,
        )
    }
}

@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isSecret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        modifier = modifier,
    )
}

@Composable
private fun PairingStatus(
    state: PairingUiState,
    onSubmit: () -> Unit,
    onRetryConnect: () -> Unit,
    onOpenRemote: () -> Unit,
    errorText: (com.example.nexusremote.domain.model.ErrorCode) -> String,
) {
    val step = state.step
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when (step) {
            PairingStep.Pairing, PairingStep.Connecting -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text(
                    stringResource(
                        if (step == PairingStep.Pairing) R.string.pair_progress_pairing else R.string.pair_progress_connecting,
                    ),
                )
            }
            is PairingStep.Success -> {
                Text(
                    text = stringResource(R.string.pair_success, step.deviceName),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onOpenRemote, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.pair_open_remote))
                }
            }
            is PairingStep.PairedNotConnected -> {
                BodyText(stringResource(R.string.pair_paired_not_connected))
                Text(errorText(step.errorCode), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetryConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.pair_retry_connect))
                }
            }
            is PairingStep.Failed -> Text(errorText(step.errorCode), color = MaterialTheme.colorScheme.error)
            PairingStep.Idle -> Unit
        }
        if (step !is PairingStep.Success && step !is PairingStep.PairedNotConnected) {
            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.pair_action))
            }
        }
    }
}
