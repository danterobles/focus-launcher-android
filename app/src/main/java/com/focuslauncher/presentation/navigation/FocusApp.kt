package com.focuslauncher.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.core.home.HomeRoleStatus

/** Contenedor de navegación. Las pantallas reales llegan en T05–T08; aquí solo hay marcadores. */
@Composable
fun FocusApp(
    state: MainUiState,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
    onRequestHomeRole: () -> Unit,
) {
    BackHandler(enabled = state.screen != Screen.Home) { onBack() }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            Text(stringResource(screenTitle(state.screen)), style = MaterialTheme.typography.headlineMedium)
            if (state.screen == Screen.Home) {
                HomePlaceholder(state, onNavigate, onRequestHomeRole)
            } else {
                Button(onClick = onBack) { Text(stringResource(R.string.action_back_home)) }
            }
        }
    }
}

@Composable
private fun HomePlaceholder(
    state: MainUiState,
    onNavigate: (Screen) -> Unit,
    onRequestHomeRole: () -> Unit,
) {
    Text(
        stringResource(
            when (state.roleStatus) {
                HomeRoleStatus.Held -> R.string.role_status_held
                HomeRoleStatus.NotHeld -> R.string.role_status_not_held
                HomeRoleStatus.Unavailable -> R.string.role_status_unavailable
            },
        ),
    )
    if (state.roleStatus != HomeRoleStatus.Held) {
        Button(onClick = onRequestHomeRole) { Text(stringResource(R.string.action_request_home)) }
    }
    if (state.homeSettingsUnavailable) Text(stringResource(R.string.home_settings_unavailable))
    Button(onClick = { onNavigate(Screen.Drawer) }) { Text(stringResource(R.string.screen_drawer)) }
    Button(onClick = { onNavigate(Screen.Settings) }) { Text(stringResource(R.string.screen_settings)) }
    Button(onClick = { onNavigate(Screen.Help) }) { Text(stringResource(R.string.screen_help)) }
    Button(onClick = { onNavigate(Screen.Welcome) }) { Text(stringResource(R.string.screen_welcome)) }
}

private fun screenTitle(screen: Screen): Int = when (screen) {
    Screen.Welcome -> R.string.screen_welcome
    Screen.Home -> R.string.screen_home
    Screen.Drawer -> R.string.screen_drawer
    Screen.Settings -> R.string.screen_settings
    Screen.Help -> R.string.screen_help
}
