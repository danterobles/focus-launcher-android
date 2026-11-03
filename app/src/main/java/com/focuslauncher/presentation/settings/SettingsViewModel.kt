package com.focuslauncher.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.CatalogError
import com.focuslauncher.domain.model.ManageablePackage
import com.focuslauncher.domain.model.ManageableState
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.repository.AdminRepository
import com.focuslauncher.domain.usecase.ObserveManageablePackages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Indicador de guardado: nunca muestra «guardado» antes de la confirmación del coordinador. */
sealed interface SaveIndicator {
    data object Idle : SaveIndicator
    data object Saving : SaveIndicator
    data object Saved : SaveIndicator
    data class Failed(val reason: SaveFailure) : SaveIndicator
}

enum class SaveFailure { SessionExpired, StorageFailure, Unavailable, RecoveryRequired, Rejected }

sealed interface EditorContent {
    data object Loading : EditorContent

    /** Con PIN habilitado y sin sesión vigente: no hay lista (ni etiquetas de apps ocultas). */
    data object Locked : EditorContent
    data class Blocked(val reason: BlockedReason) : EditorContent
    data class Ready(
        val packages: List<ManageablePackage>,
        val catalogError: CatalogError?,
        /** Paquetes con un guardado en curso: su control queda deshabilitado. */
        val saving: Set<AppPackageKey>,
        val failed: Map<AppPackageKey, SaveFailure>,
    ) : EditorContent
}

data class SettingsUiState(
    val content: EditorContent = EditorContent.Loading,
    val indicator: SaveIndicator = SaveIndicator.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeManageablePackages: ObserveManageablePackages,
    private val admin: AdminRepository,
) : ViewModel() {

    private val saving = MutableStateFlow<Set<AppPackageKey>>(emptySet())
    private val failed = MutableStateFlow<Map<AppPackageKey, SaveFailure>>(emptyMap())
    private val indicator = MutableStateFlow<SaveIndicator>(SaveIndicator.Idle)
    private var fadeJob: Job? = null

    /**
     * La lista (con apps ocultas) solo existe mientras la administración está autorizada. Sin
     * suscriptores no se conserva nada: `replayExpiration = 0` descarta el último valor.
     */
    val state: StateFlow<SettingsUiState> = combine(
        observeManageablePackages(), admin.isAuthorized, saving, failed, indicator,
    ) { manageable, authorized, saving, failed, indicator ->
        val content = when (manageable) {
            ManageableState.Loading -> EditorContent.Loading
            is ManageableState.Blocked -> EditorContent.Blocked(manageable.reason)
            is ManageableState.Ready ->
                if (manageable.pinEnabled && !authorized) EditorContent.Locked
                else EditorContent.Ready(manageable.packages, manageable.catalogError, saving, failed)
        }
        SettingsUiState(content, indicator)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), SettingsUiState())

    /** `visible` es el valor deseado. El valor mostrado solo cambia cuando el guardado se confirma. */
    fun onSetVisible(key: AppPackageKey, visible: Boolean) {
        if (key in saving.value) return
        saving.update { it + key }
        failed.update { it - key }
        fadeJob?.cancel()
        indicator.value = SaveIndicator.Saving
        viewModelScope.launch {
            val result = admin.setPackageHidden(key, hidden = !visible)
            saving.update { it - key }
            when (result) {
                MutationResult.Success -> {
                    indicator.value = SaveIndicator.Saved
                    fadeJob = launch { delay(2_500); if (saving.value.isEmpty()) indicator.value = SaveIndicator.Idle }
                }
                else -> {
                    val reason = result.toFailure()
                    failed.update { it + (key to reason) }
                    indicator.value = SaveIndicator.Failed(reason)
                }
            }
        }
    }

    /** Se invoca al salir de la pantalla: nada de lo editado se conserva. */
    fun clear() {
        fadeJob?.cancel()
        saving.value = emptySet(); failed.value = emptyMap(); indicator.value = SaveIndicator.Idle
    }

    private fun MutationResult.toFailure(): SaveFailure = when (this) {
        MutationResult.AuthenticationRequired -> SaveFailure.SessionExpired
        MutationResult.StorageFailure -> SaveFailure.StorageFailure
        MutationResult.Unavailable -> SaveFailure.Unavailable
        MutationResult.RecoveryRequired -> SaveFailure.RecoveryRequired
        is MutationResult.InvalidInput -> SaveFailure.Rejected
        MutationResult.Success -> error("no es un fallo")
    }
}
