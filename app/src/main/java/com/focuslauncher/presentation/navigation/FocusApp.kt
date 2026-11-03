package com.focuslauncher.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslauncher.R
import com.focuslauncher.presentation.admin.AdminBlockedScreen
import com.focuslauncher.presentation.common.LoadingIndicator
import com.focuslauncher.presentation.admin.AdminGate
import com.focuslauncher.presentation.admin.AdminGateViewModel
import com.focuslauncher.presentation.admin.PinGateScreen
import com.focuslauncher.presentation.drawer.DrawerScreen
import com.focuslauncher.presentation.help.HelpScreen
import com.focuslauncher.presentation.help.HelpViewModel
import com.focuslauncher.presentation.onboarding.WelcomeScreen
import com.focuslauncher.presentation.drawer.DrawerViewModel
import com.focuslauncher.presentation.home.HomeScreen
import com.focuslauncher.presentation.home.HomeViewModel
import com.focuslauncher.presentation.security.SecurityOutcome
import com.focuslauncher.presentation.security.SecurityScreen
import com.focuslauncher.presentation.security.SecurityViewModel
import com.focuslauncher.presentation.settings.SettingsScreen
import com.focuslauncher.presentation.settings.SettingsViewModel

/** Contenedor de navegación. Settings/Security/Welcome/Help son marcadores hasta T07–T08. */
@Composable
fun FocusApp(
    state: MainUiState,
    needsWelcome: Boolean,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
    onRequestHomeRole: () -> Unit,
    onSecurityFinished: (UiMessage?, Screen) -> Unit,
    onDismissMessage: () -> Unit,
    onAcceptWelcome: (requestHome: Boolean) -> Unit,
    onOpenSystemSettings: (SystemSettingsTarget) -> Unit,
) {
    BackHandler(enabled = state.screen != Screen.Home) { onBack() }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // El aviso de alcance va antes de cualquier configuración o apps; solo la Ayuda lo precede.
            val screen = if (needsWelcome && state.screen != Screen.Help) Screen.Welcome else state.screen
            when (screen) {
                Screen.Welcome -> WelcomeScreen(
                    saving = state.welcomeSaving,
                    error = state.welcomeError,
                    onAcceptAndUseAsHome = { onAcceptWelcome(true) },
                    onAcceptAndTry = { onAcceptWelcome(false) },
                    onOpenHelp = { onNavigate(Screen.Help) },
                )
                Screen.Help -> {
                    val vm: HelpViewModel = hiltViewModel()
                    val status by vm.status.collectAsStateWithLifecycle()
                    HelpScreen(status, state.settingsUnavailable, onOpenSystemSettings, vm::retry, onBack)
                }
                Screen.Home -> {
                    val vm: HomeViewModel = hiltViewModel()
                    val home by vm.state.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = home,
                        roleStatus = state.roleStatus,
                        homeSettingsUnavailable = state.settingsUnavailable == SystemSettingsTarget.HomeSettings,
                        message = state.message,
                        onMessageShown = onDismissMessage,
                        onAppClick = vm::onAppClick,
                        onOpenDrawer = { onNavigate(Screen.Drawer) },
                        onOpenSettings = { onNavigate(Screen.Settings) },
                        onOpenHelp = { onNavigate(Screen.Help) },
                        onRequestHomeRole = onRequestHomeRole,
                        onNoticeShown = vm::onNoticeShown,
                        onRetry = vm::onRetry,
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
                Screen.Settings, Screen.Security -> AdminArea(
                    screen = screen,
                    onNavigate = onNavigate,
                    onBack = onBack,
                    onSecurityFinished = onSecurityFinished,
                )
            }
        }
    }
}

/**
 * Área administrativa: el contenido protegido (editor, seguridad) solo se compone con la puerta
 * abierta. Si la sesión se revoca o caduca, la puerta se cierra y el contenido sale de la
 * composición (y sus ViewModels descartan su estado).
 */
@Composable
private fun AdminArea(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
    onSecurityFinished: (UiMessage?, Screen) -> Unit,
) {
    val gateVm: AdminGateViewModel = hiltViewModel()
    val gate by gateVm.gate.collectAsStateWithLifecycle()
    when (val g = gate) {
        AdminGate.Loading -> LoadingIndicator(Modifier.fillMaxSize().padding(24.dp))
        is AdminGate.Blocked -> AdminBlockedScreen(g, onRetry = gateVm::retry, onOpenHelp = { onNavigate(Screen.Help) }, onBack = onBack)
        is AdminGate.NeedsPin -> PinGateScreen(g, onSubmit = gateVm::submit, onBack = { onNavigate(Screen.Home) }, onShown = gateVm::onGateShown)
        AdminGate.Open -> if (screen == Screen.Settings) {
            val vm: SettingsViewModel = hiltViewModel()
            val settings by vm.state.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose(vm::clear) }
            SettingsScreen(
                state = settings,
                onSetVisible = vm::onSetVisible,
                onOpenSecurity = { onNavigate(Screen.Security) },
                onOpenHelp = { onNavigate(Screen.Help) },
                onBack = onBack,
            )
        } else {
            val vm: SecurityViewModel = hiltViewModel()
            val security by vm.state.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose(vm::clear) }
            LaunchedEffect(vm) {
                vm.outcomes.collect { outcome ->
                    when (outcome) {
                        SecurityOutcome.PinEnabled -> onSecurityFinished(UiMessage.PinEnabled, Screen.Home)
                        SecurityOutcome.PinChanged -> onSecurityFinished(UiMessage.PinChanged, Screen.Home)
                        SecurityOutcome.PinDisabled -> onSecurityFinished(UiMessage.PinDisabled, Screen.Home)
                        SecurityOutcome.ConfigurationReset -> onSecurityFinished(null, Screen.Welcome)
                    }
                }
            }
            SecurityScreen(
                state = security,
                onEnablePin = vm::enablePin,
                onChangePin = vm::changePin,
                onDisablePin = vm::disablePin,
                onReset = vm::resetConfiguration,
                onDismissError = vm::dismissError,
                onBack = onBack,
            )
        }
    }
}
