package com.example.nexusremote.ui.screens.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.TvApp
import com.example.nexusremote.ui.AppViewModelProvider
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.ScreenHeader
import com.example.nexusremote.ui.components.describe
import com.example.nexusremote.ui.components.resolve

@Composable
fun AppsScreen(
    snackbarHostState: SnackbarHostState,
    onBack: (() -> Unit)? = null,
    viewModel: AppsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.apps_title), onBack = onBack) {
            IconButton(onClick = viewModel::refresh, enabled = state.connected && !state.loading) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.cd_refresh_apps))
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.apps_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )

        when {
            !state.connected && !state.loadedOnce -> CenteredMessage(stringResource(R.string.apps_not_connected))
            state.loading && !state.loadedOnce -> CenteredLoading()
            state.loadError != null && !state.loadedOnce -> CenteredMessage(state.loadError!!.describe(context))
            state.favorites.isEmpty() && state.others.isEmpty() -> CenteredMessage(stringResource(R.string.apps_empty))
            else -> AppGrid(
                state = state,
                enabled = state.connected,
                onLaunch = viewModel::launch,
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }
}

@Composable
private fun AppGrid(
    state: AppsUiState,
    enabled: Boolean,
    onLaunch: (TvApp) -> Unit,
    onToggleFavorite: (TvApp) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.favorites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.apps_favorites)) }
            items(state.favorites, key = { "fav-" + it.packageName }) { app ->
                AppTile(app, isFavorite = true, enabled = enabled, onLaunch = onLaunch, onToggleFavorite = onToggleFavorite)
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.apps_all)) }
        }
        items(state.others, key = { it.packageName }) { app ->
            AppTile(app, isFavorite = false, enabled = enabled, onLaunch = onLaunch, onToggleFavorite = onToggleFavorite)
        }
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    isFavorite: Boolean,
    enabled: Boolean,
    onLaunch: (TvApp) -> Unit,
    onToggleFavorite: (TvApp) -> Unit,
) {
    val openDescription = stringResource(R.string.cd_open_app, app.label)
    Card(
        onClick = { onLaunch(app) },
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clearAndSetSemantics { contentDescription = openDescription },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LetterAvatar(app.label)
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onToggleFavorite(app) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.cd_favorite_remove else R.string.cd_favorite_add,
                        app.label,
                    ),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LetterAvatar(label: String) {
    Box(
        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        BodyText(text)
    }
}

@Composable
private fun CenteredLoading() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        BodyText(stringResource(R.string.apps_loading), Modifier.padding(top = 12.dp))
    }
}
