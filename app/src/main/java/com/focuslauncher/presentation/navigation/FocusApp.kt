package com.focuslauncher.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslauncher.R
import com.focuslauncher.presentation.drawer.DrawerScreen
import com.focuslauncher.presentation.drawer.DrawerViewModel
import com.focuslauncher.presentation.home.HomeScreen
import com.focuslauncher.presentation.home.HomeViewModel

/** Contenedor de navegación. Settings/Security/Welcome/Help son marcadores hasta T07–T08. */
@Composable
fun FocusApp(
    state: MainUiState,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
    onRequestHomeRole: () -> Unit,
) {
    BackHandler(enabled = state.screen != Screen.Home) { onBack() }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (state.screen) {
                Screen.Home -> {
                    val vm: HomeViewModel = hiltViewModel()
                    val home by vm.state.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = home,
                        roleStatus = state.roleStatus,
                        homeSettingsUnavailable = state.homeSettingsUnavailable,
                        onAppClick = vm::onAppClick,
                        onOpenDrawer = { onNavigate(Screen.Drawer) },
                        onOpenSettings = { onNavigate(Screen.Settings) },
                        onOpenHelp = { onNavigate(Screen.Help) },
                        onRequestHomeRole = onRequestHomeRole,
                        onNoticeShown = vm::onNoticeShown,
                    )
                }
                Screen.Drawer -> {
                    val vm: DrawerViewModel = hiltViewModel()
                    val drawer by vm.state.collectAsStateWithLifecycle()
                    DrawerScreen(
                        state = drawer,
                        onQueryChange = vm::onQueryChange,
                        onAppClick = vm::onAppClick,
                        onBack = onBack,
                        onNoticeShown = vm::onNoticeShown,
                        onLeave = vm::clearQuery,
                    )
                }
                else -> Placeholder(state.screen, onBack)
            }
        }
    }
}

@Composable
private fun Placeholder(screen: Screen, onBack: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(
                when (screen) {
                    Screen.Welcome -> R.string.screen_welcome
                    Screen.Settings -> R.string.screen_settings
                    Screen.Security -> R.string.screen_security
                    else -> R.string.screen_help
                },
            ),
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back_home)) }
    }
}
