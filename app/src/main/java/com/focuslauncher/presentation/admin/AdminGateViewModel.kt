package com.focuslauncher.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.core.di.MonotonicClock
import com.focuslauncher.core.security.PinPolicy
import com.focuslauncher.domain.model.AuthenticationResult
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.repository.AdminRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PinFeedbackKind { Incorrect, Cooldown, CouldNotVerify, StorageFailure, RecoveryRequired }

data class PinFeedback(val kind: PinFeedbackKind, val attemptsLeft: Int = 0)

/** Puerta de las pantallas administrativas. El contenido protegido solo existe en `Open`. */
sealed interface AdminGate {
    data object Loading : AdminGate

    /** La configuración no se puede leer: no se muestra nada protegido. */
    data class Blocked(val readError: ReadErrorKind?, val recovery: RecoveryReason?) : AdminGate

    data class NeedsPin(
        val busy: Boolean = false,
        /** La verificación supera 300 ms: se muestra el indicador de progreso (RNF-04). */
        val slow: Boolean = false,
        val feedback: PinFeedback? = null,
        val cooldownSeconds: Long = 0,
    ) : AdminGate

    data object Open : AdminGate
}

const val SLOW_INDICATOR_MS = 300L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdminGateViewModel @Inject constructor(
    private val stateRepository: FocusStateRepository,
    private val admin: AdminRepository,
    private val clock: MonotonicClock,
) : ViewModel() {

    private data class Entry(
        val busy: Boolean = false,
        val slow: Boolean = false,
        val feedback: PinFeedback? = null,
        val cooldownEndsAt: Long? = null,
    )

    private val entry = MutableStateFlow(Entry())
    private var slowJob: Job? = null

    /** Segundos de espera restantes, derivados del reloj monotónico y actualizados cada segundo. */
    private val cooldownSeconds = entry.map { it.cooldownEndsAt }.distinctUntilChanged().flatMapLatest { end ->
        if (end == null) flowOf(0L) else flow {
            while (true) {
                val remainingMs = end - clock.elapsedRealtimeMillis()
                emit(if (remainingMs <= 0) 0L else (remainingMs + 999) / 1000)
                if (remainingMs <= 0) break
                delay(1_000)
            }
        }
    }

    // replayExpiration = 0: al dejar de observar no se conserva nada (ni estado de PIN) en memoria.
    val gate: StateFlow<AdminGate> = combine(stateRepository.settings, admin.isAuthorized, entry, cooldownSeconds) {
            settings, authorized, e, cooldown ->
        when (settings) {
            SettingsReadState.Loading -> AdminGate.Loading
            is SettingsReadState.ReadError -> AdminGate.Blocked(settings.kind, null)
            is SettingsReadState.RecoveryRequired -> AdminGate.Blocked(null, settings.reason)
            is SettingsReadState.Ready ->
                if (!settings.settings.pinEnabled || authorized) AdminGate.Open
                else AdminGate.NeedsPin(
                    busy = e.busy,
                    slow = e.slow,
                    feedback = e.feedback?.takeUnless { it.kind == PinFeedbackKind.Cooldown && cooldown == 0L },
                    cooldownSeconds = cooldown,
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), AdminGate.Loading)

    /** Al mostrarse la pantalla de PIN: recupera una espera activa (p. ej. tras reiniciar el proceso). */
    fun onGateShown() {
        viewModelScope.launch {
            val remaining = admin.cooldownRemainingMs()
            entry.update {
                if (remaining > 0) it.copy(
                    cooldownEndsAt = clock.elapsedRealtimeMillis() + remaining,
                    feedback = PinFeedback(PinFeedbackKind.Cooldown),
                ) else it
            }
        }
    }

    /** El llamador cede [pin]: el coordinador lo borra tras usarlo. */
    fun submit(pin: CharArray) {
        if (entry.value.busy || !PinPolicy.isValid(pin)) { PinPolicy.wipe(pin); return }
        entry.update { it.copy(busy = true, slow = false, feedback = null) }
        slowJob?.cancel()
        slowJob = viewModelScope.launch {
            delay(SLOW_INDICATOR_MS)
            entry.update { if (it.busy) it.copy(slow = true) else it }
        }
        viewModelScope.launch {
            val result = try {
                admin.verifyPin(pin)
            } finally {
                slowJob?.cancel()
                entry.update { it.copy(busy = false, slow = false) }
            }
            entry.update { current ->
                when (result) {
                    AuthenticationResult.Success, AuthenticationResult.NotRequired -> Entry()
                    AuthenticationResult.InvalidInput -> current.copy(feedback = PinFeedback(PinFeedbackKind.Incorrect))
                    is AuthenticationResult.IncorrectPin ->
                        current.copy(feedback = PinFeedback(PinFeedbackKind.Incorrect, result.attemptsLeftInWindow))
                    is AuthenticationResult.Cooldown -> current.copy(
                        cooldownEndsAt = clock.elapsedRealtimeMillis() + result.remainingMs,
                        feedback = PinFeedback(PinFeedbackKind.Cooldown),
                    )
                    AuthenticationResult.Unavailable -> current.copy(feedback = PinFeedback(PinFeedbackKind.CouldNotVerify))
                    AuthenticationResult.StorageFailure -> current.copy(feedback = PinFeedback(PinFeedbackKind.StorageFailure))
                    AuthenticationResult.RecoveryRequired -> current.copy(feedback = PinFeedback(PinFeedbackKind.RecoveryRequired))
                }
            }
        }
    }

    fun retry() = stateRepository.retry()
}
