package com.focuslauncher.presentation.home

import androidx.lifecycle.ViewModel
import com.focuslauncher.core.di.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Marcador de T01: demuestra inyección Hilt de ViewModel. Se sustituye en T05. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @Suppress("unused") private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
}
