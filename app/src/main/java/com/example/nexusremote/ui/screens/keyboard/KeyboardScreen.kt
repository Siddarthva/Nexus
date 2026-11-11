package com.example.nexusremote.ui.screens.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexusremote.R
import com.example.nexusremote.ui.AppViewModelProvider
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.ScreenHeader
import com.example.nexusremote.ui.components.SectionCard
import com.example.nexusremote.ui.components.resolve
import com.example.nexusremote.util.ShellTextEscaper

@Composable
fun KeyboardScreen(
    snackbarHostState: SnackbarHostState,
    onBack: (() -> Unit)? = null,
    viewModel: KeyboardViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.keyboard_title), onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BodyText(stringResource(R.string.keyboard_hint))
            if (!state.connected) {
                Text(stringResource(R.string.apps_not_connected), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                label = { Text(stringResource(R.string.keyboard_field)) },
                isError = state.unsupported.isNotEmpty() || state.text.length > ShellTextEscaper.MAX_LENGTH,
                supportingText = {
                    when {
                        state.unsupported.isNotEmpty() ->
                            Text(stringResource(R.string.keyboard_unsupported, state.unsupported.joinToString(" ")))
                        else -> Text(stringResource(R.string.keyboard_counter, state.text.length, ShellTextEscaper.MAX_LENGTH))
                    }
                },
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (state.canSend) viewModel.send() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::clear,
                    enabled = state.text.isNotEmpty() && !state.sending,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Rounded.Clear, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.keyboard_clear))
                }
                Button(
                    onClick = viewModel::send,
                    enabled = state.canSend,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.keyboard_send))
                }
            }
            SectionCard { BodyText(stringResource(R.string.keyboard_supported)) }
        }
    }
}
