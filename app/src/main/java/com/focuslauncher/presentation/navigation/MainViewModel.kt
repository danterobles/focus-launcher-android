package com.focuslauncher.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.core.di.DispatcherProvider
import com.focuslauncher.core.home.HomeRoleController
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.repository.AdminRepository
import com.focuslauncher.domain.repository.AppCatalogRepository
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

    /** El rol no puede solicitarse: abrir los ajustes Home del sistema. */
    data object OpenHomeSettings : MainEvent
}

data class MainUiState(
    val screen: Screen = Screen.Home,
    val roleStatus: HomeRoleStatus = HomeRoleStatus.Unavailable,
    /** Se muestra mientras no se pudo abrir ningún ajuste Home del sistema. */
    val homeSettingsUnavailable: Boolean = false,
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
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Salir del área administrativa revoca la sesión de inmediato. */
    fun navigateTo(screen: Screen) {
        if (_state.value.screen.administrative && !screen.administrative) adminRepository.lock()
        _state.update { it.copy(screen = screen) }
    }

    fun navigateBack() {
        if (_state.value.screen != Screen.Home) navigateTo(Screen.Home)
    }

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
                    _events.send(if (intent != null) MainEvent.LaunchRoleRequest(intent) else MainEvent.OpenHomeSettings)
                }
                HomeRoleStatus.Unavailable -> _events.send(MainEvent.OpenHomeSettings)
            }
        }
    }

    fun onHomeSettingsUnavailable() = _state.update { it.copy(homeSettingsUnavailable = true) }

    fun onHomeSettingsOpened() = _state.update { it.copy(homeSettingsUnavailable = false) }
}
