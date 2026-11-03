package com.focuslauncher.presentation.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.repository.FocusStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Estado público de la configuración, sin datos sensibles: solo decide qué avisos mostrar en Ayuda. */
sealed interface HelpStatus {
    data object Unknown : HelpStatus
    data object Healthy : HelpStatus
    data class ReadError(val kind: ReadErrorKind) : HelpStatus
    data class RecoveryRequired(val reason: RecoveryReason) : HelpStatus
}

@HiltViewModel
class HelpViewModel @Inject constructor(
    private val stateRepository: FocusStateRepository,
) : ViewModel() {

    val status: StateFlow<HelpStatus> = stateRepository.settings.map {
        when (it) {
            SettingsReadState.Loading -> HelpStatus.Unknown
            is SettingsReadState.Ready -> HelpStatus.Healthy
            is SettingsReadState.ReadError -> HelpStatus.ReadError(it.kind)
            is SettingsReadState.RecoveryRequired -> HelpStatus.RecoveryRequired(it.reason)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HelpStatus.Unknown)

    fun retry() = stateRepository.retry()
}
