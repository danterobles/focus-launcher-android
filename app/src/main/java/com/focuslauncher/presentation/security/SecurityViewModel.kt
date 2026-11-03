package com.focuslauncher.presentation.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.repository.AdminRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import com.focuslauncher.presentation.admin.SLOW_INDICATOR_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SecurityError { InvalidPin, SessionExpired, Unavailable, StorageFailure, RecoveryRequired }

/** Qué pasó, para que la pantalla principal lo comunique. */
enum class SecurityOutcome { PinEnabled, PinChanged, PinDisabled, ConfigurationReset }

data class SecurityUiState(
    /** `null` mientras la configuración no está lista. */
    val pinEnabled: Boolean? = null,
    val busy: Boolean = false,
    /** Operación de más de 300 ms (la KDF): se muestra el indicador. */
    val slow: Boolean = false,
    val error: SecurityError? = null,
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    stateRepository: FocusStateRepository,
    private val admin: AdminRepository,
) : ViewModel() {

    private data class Local(val busy: Boolean = false, val slow: Boolean = false, val error: SecurityError? = null)

    private val local = MutableStateFlow(Local())
    private val _outcomes = Channel<SecurityOutcome>(Channel.BUFFERED)
    val outcomes = _outcomes.receiveAsFlow()
    private var slowJob: Job? = null

    val state: StateFlow<SecurityUiState> = combine(stateRepository.settings, local) { settings, local ->
        SecurityUiState(
            pinEnabled = (settings as? SettingsReadState.Ready)?.settings?.pinEnabled,
            busy = local.busy, slow = local.slow, error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), SecurityUiState())

    fun enablePin(pin: CharArray, confirmation: CharArray) =
        run(SecurityOutcome.PinEnabled) { admin.enablePin(pin, confirmation) }

    fun changePin(pin: CharArray, confirmation: CharArray) =
        run(SecurityOutcome.PinChanged) { admin.changePin(pin, confirmation) }

    /** La confirmación explícita la pide la pantalla antes de llamar aquí. */
    fun disablePin() = run(SecurityOutcome.PinDisabled) { admin.disablePin() }

    fun resetConfiguration() = run(SecurityOutcome.ConfigurationReset) { admin.resetConfiguration() }

    fun dismissError() = local.update { it.copy(error = null) }
    fun clear() { slowJob?.cancel(); local.value = Local() }

    private fun run(outcome: SecurityOutcome, block: suspend () -> MutationResult) {
        if (local.value.busy) return
        local.value = Local(busy = true)
        slowJob?.cancel()
        slowJob = viewModelScope.launch {
            delay(SLOW_INDICATOR_MS)
            local.update { if (it.busy) it.copy(slow = true) else it }
        }
        viewModelScope.launch {
            val result = try { block() } finally { slowJob?.cancel() }
            local.value = when (result) {
                MutationResult.Success -> { _outcomes.send(outcome); Local() }
                is MutationResult.InvalidInput -> Local(error = SecurityError.InvalidPin)
                MutationResult.AuthenticationRequired -> Local(error = SecurityError.SessionExpired)
                MutationResult.Unavailable -> Local(error = SecurityError.Unavailable)
                MutationResult.StorageFailure -> Local(error = SecurityError.StorageFailure)
                MutationResult.RecoveryRequired -> Local(error = SecurityError.RecoveryRequired)
            }
        }
    }
}
