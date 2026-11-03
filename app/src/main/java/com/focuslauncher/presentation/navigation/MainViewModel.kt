package com.focuslauncher.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.core.di.DispatcherProvider
import com.focuslauncher.core.home.HomeRoleController
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.repository.AppCatalogRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

/** Resumen no sensible del catálogo (solo cuenta; nunca la lista sin filtrar). T05 lo sustituye. */
data class CatalogSummary(val count: Int? = null, val error: Boolean = false)

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
    private val stateRepository: FocusStateRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    /** Mientras haya un suscriptor se mantienen registrados los callbacks del catálogo. */
    val catalogSummary: StateFlow<CatalogSummary> = catalogRepository.catalog
        .map { catalog ->
            when (catalog) {
                CatalogState.Loading -> CatalogSummary()
                is CatalogState.Ready -> CatalogSummary(count = catalog.apps.size)
                is CatalogState.Error -> CatalogSummary(error = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogSummary())

    /** Etiqueta no sensible del estado de ajustes (solo depuración); T05 consumirá la política real. */
    val settingsStatus: StateFlow<String> = stateRepository.settings
        .map { state ->
            when (state) {
                SettingsReadState.Loading -> "cargando"
                is SettingsReadState.Ready ->
                    "listo (reglas=${state.settings.visibility.hiddenPackages.size}, pin=${state.settings.pinEnabled}, aviso=${state.settings.onboardingAccepted})"
                is SettingsReadState.ReadError -> "error de lectura: ${state.kind}"
                is SettingsReadState.RecoveryRequired -> "recuperación requerida: ${state.reason}"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "cargando")

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun navigateTo(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun navigateBack() = _state.update {
        if (it.screen == Screen.Home) it else it.copy(screen = Screen.Home)
    }

    /** `onNewIntent` con categoría HOME: vuelve a Home. T06 revocará aquí la sesión administrativa. */
    fun onHomeIntent() = navigateTo(Screen.Home)

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
