package com.example.nexusremote.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nexusremote.ui.screens.apps.AppsScreen
import com.example.nexusremote.ui.screens.home.HomeScreen
import com.example.nexusremote.ui.screens.keyboard.KeyboardScreen
import com.example.nexusremote.ui.screens.pairing.PairingScreen
import com.example.nexusremote.ui.screens.paircode.PairCodeScreen
import com.example.nexusremote.ui.screens.remote.RemoteScreen
import com.example.nexusremote.ui.screens.settings.SettingsScreen
import com.example.nexusremote.ui.screens.tvsettings.TvSettingsScreen

/**
 * Root of the UI: one Scaffold (snackbars + bottom bar) around the navigation graph.
 * The bottom bar (TVs, Remote, Settings) is shown on those three screens.
 *
 * @param pendingDestination a route requested from outside (Quick Settings tile), or null.
 */
@Composable
fun NexusApp(pendingDestination: String?, onDestinationHandled: () -> Unit) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = BottomDestination.entries.any { it.route == currentRoute }

    LaunchedEffect(pendingDestination) {
        if (pendingDestination != null) {
            navController.navigateToTab(pendingDestination)
            onDestinationHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    BottomDestination.entries.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateToTab(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            // Gentle cross-fade between screens.
            enterTransition = { fadeIn(tween(FADE_MS)) },
            exitTransition = { fadeOut(tween(FADE_MS)) },
            popEnterTransition = { fadeIn(tween(FADE_MS)) },
            popExitTransition = { fadeOut(tween(FADE_MS)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    snackbarHostState = snackbarHostState,
                    onNavigate = { route -> navController.go(route) },
                )
            }
            composable(
                route = Routes.PAIR_CODE_PATTERN,
                arguments = listOf(
                    navArgument(Routes.PAIR_CODE_ARG_HOST) { type = NavType.StringType },
                    navArgument(Routes.PAIR_ARG_DEVICE_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                PairCodeScreen(
                    onBack = { navController.popBackStack() },
                    onConnected = { navController.openRemoteAfterPairing() },
                )
            }
            composable(
                route = Routes.PAIR_PATTERN,
                arguments = listOf(
                    navArgument(Routes.PAIR_ARG_DEVICE_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                PairingScreen(
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                    onOpenRemote = { navController.openRemoteAfterPairing() },
                )
            }
            composable(Routes.REMOTE) {
                RemoteScreen(
                    snackbarHostState = snackbarHostState,
                    onOpenApps = { navController.navigate(Routes.APPS) { launchSingleTop = true } },
                    onOpenKeyboard = { navController.navigate(Routes.KEYBOARD) { launchSingleTop = true } },
                    onOpenTvSettings = { navController.navigate(Routes.TV_SETTINGS) { launchSingleTop = true } },
                    onNavigate = { route -> navController.go(route) },
                )
            }
            // Reached only from the Remote "More" menu; not part of the bottom bar.
            composable(Routes.APPS) {
                AppsScreen(snackbarHostState = snackbarHostState, onBack = { navController.popBackStack() })
            }
            composable(Routes.KEYBOARD) {
                KeyboardScreen(snackbarHostState = snackbarHostState, onBack = { navController.popBackStack() })
            }
            composable(Routes.TV_SETTINGS) {
                TvSettingsScreen(snackbarHostState = snackbarHostState, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    snackbarHostState = snackbarHostState,
                    onNavigate = { route -> navController.go(route) },
                )
            }
        }
    }
}

/** Bottom-bar routes use tab navigation; everything else is pushed on the stack. */
private fun NavHostController.go(route: String) {
    if (BottomDestination.entries.any { it.route == route }) navigateToTab(route) else navigate(route)
}

/** After pairing: show the remote and drop the pairing screen from the back stack. */
private fun NavHostController.openRemoteAfterPairing() {
    navigate(Routes.REMOTE) {
        popUpTo(Routes.HOME)
        launchSingleTop = true
    }
}

/** Tab-style navigation: one copy of each tab, the TV list stays at the bottom of the stack. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private const val FADE_MS = 220
