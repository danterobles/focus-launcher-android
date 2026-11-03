package com.focuslauncher.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.core.di.DispatcherProvider
import com.focuslauncher.core.home.HomeRoleController
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.repository.AdminRepository
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.repository.AppCatalogRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import com.focuslauncher.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Intent

/** Eventos de una sola entrega hacia la Activity. */
sealed interface MainEvent {
    /** Lanzar el intent oficial de solicitud de `ROLE_HOME`. */
    data class LaunchRoleRequest(val intent: Intent) : MainEvent

    /** Salida segura a una pantalla de Ajustes de Android. */
    data class OpenSystemSettings(val target: SystemSettingsTarget) : MainEvent
}

/** Aviso de una sola lectura mostrado en Inicio tras una operación administrativa. */
enum class UiMessage { PinEnabled, PinChanged, PinDisabled }

data class MainUiState(
    val screen: Screen = Screen.Home,
    val message: UiMessage? = null,
    val roleStatus: HomeRoleStatus = HomeRoleStatus.Unavailable,
    /** Última salida a Ajustes de Android que no se pudo abrir (se muestran instrucciones manuales). */
    val settingsUnavailable: SystemSettingsTarget? = null,
    /** Guardando la aceptación del aviso / fallo al guardarla. */
    val welcomeSaving: Boolean = false,
    val welcomeError: Boolean = false,
)

/**
 * Navegación y estado del rol Home. El estado vive solo en memoria: tras la muerte del proceso
 * siempre se vuelve a Home, de modo que ninguna pantalla administrativa se restaura sola.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val roleController: HomeRoleController,
    private val catalogRepository: AppCatalogRepository,
    private val adminRepository: AdminRepository,
    private val stateRepository: FocusStateRepository,
    private val onboarding: OnboardingRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    /**
     * `true` solo con la configuración legible y el aviso de alcance sin aceptar (instalación nueva o
     * tras restablecer). Mientras carga o falla NO se muestra la Bienvenida ni apps (Inicio decide).
     */
    val needsWelcome: StateFlow<Boolean> = stateRepository.settings
        .map { it is SettingsReadState.Ready && !it.settings.onboardingAccepted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Salir del área administrativa revoca la sesión de inmediato. */
    fun navigateTo(screen: Screen) {
        if (_state.value.screen.administrative && !screen.administrative) adminRepository.lock()
        _state.update { it.copy(screen = screen) }
    }

    /** Seguridad vuelve a Configuración (sigue dentro de administración); el resto, a Inicio. */
    fun navigateBack() {
        when (_state.value.screen) {
            Screen.Home -> Unit
            Screen.Security -> navigateTo(Screen.Settings)
            else -> navigateTo(Screen.Home)
        }
    }

    /** Operación administrativa terminada: se sale de administración (la sesión se revoca) con un aviso. */
    fun onSecurityFinished(message: UiMessage?, next: Screen = Screen.Home) {
        navigateTo(next)
        _state.update { it.copy(message = message) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** `onNewIntent` con categoría HOME: cierra administración, revoca la sesión y vuelve a Inicio. */
    fun onHomeIntent() {
        adminRepository.lock()
        navigateTo(Screen.Home)
    }

    /** Segundo plano (no por cambio de configuración): revoca y no deja contenido administrativo. */
    fun onActivityStopped() {
        adminRepository.lock()
        _state.update { if (it.screen.administrative) it.copy(screen = Screen.Home) else it }
    }

    /** Vuelve a consultar el rol (al reanudar y al recibir el resultado de la solicitud). */
    fun refreshRoleStatus() {
        viewModelScope.launch(dispatchers.io) {
            val status = roleController.status()
            _state.update { it.copy(roleStatus = status) }
        }
    }

    /** Reconsulta el catálogo (al reanudar, para cubrir eventos perdidos). */
    fun refreshCatalog() {
        viewModelScope.launch { catalogRepository.refresh() }
    }

    /** Solo se invoca por una acción explícita del usuario; nunca de forma automática. */
    fun onRequestHomeRole() {
        viewModelScope.launch(dispatchers.io) {
            val status = roleController.status()
            _state.update { it.copy(roleStatus = status) }
            when (status) {
                HomeRoleStatus.Held -> Unit
                HomeRoleStatus.NotHeld -> {
                    val intent = roleController.createRequestIntent()
                    _events.send(
                        if (intent != null) MainEvent.LaunchRoleRequest(intent)
                        else MainEvent.OpenSystemSettings(SystemSettingsTarget.HomeSettings),
                    )
                }
                HomeRoleStatus.Unavailable -> _events.send(MainEvent.OpenSystemSettings(SystemSettingsTarget.HomeSettings))
            }
        }
    }

    fun onOpenSystemSettings(target: SystemSettingsTarget) {
        viewModelScope.launch { _events.send(MainEvent.OpenSystemSettings(target)) }
    }

    fun onSystemSettingsUnavailable(target: SystemSettingsTarget) = _state.update { it.copy(settingsUnavailable = target) }

    fun onSystemSettingsOpened() = _state.update { it.copy(settingsUnavailable = null) }

    /**
     * Acepta el aviso de alcance. Se confirma solo tras persistirlo; si falla se queda en la
     * Bienvenida con un error. La solicitud de Home es opcional y nunca bloquea probar Focus.
     */
    fun onAcceptWelcome(requestHome: Boolean) {
        if (_state.value.welcomeSaving) return
        _state.update { it.copy(welcomeSaving = true, welcomeError = false) }
        viewModelScope.launch {
            val result = onboarding.acceptOnboarding()
            _state.update { it.copy(welcomeSaving = false, welcomeError = result != MutationResult.Success) }
            if (result == MutationResult.Success) {
                navigateTo(Screen.Home)
                if (requestHome) onRequestHomeRole()
            }
        }
    }

    fun retryState() = stateRepository.retry()
}
