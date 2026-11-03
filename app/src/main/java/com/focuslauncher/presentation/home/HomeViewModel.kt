package com.focuslauncher.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.repository.AppCatalogRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import com.focuslauncher.domain.usecase.LaunchApp
import com.focuslauncher.domain.usecase.ObserveVisibleApps
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.common.Notice
import com.focuslauncher.presentation.common.toContent
import com.focuslauncher.presentation.common.toNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val HOME_APP_LIMIT = 8

data class HomeUiState(
    val content: AppsContent = AppsContent.Loading,
    val notice: Notice? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeVisibleApps: ObserveVisibleApps,
    private val launchApp: LaunchApp,
    private val stateRepository: FocusStateRepository,
    private val catalog: AppCatalogRepository,
) : ViewModel() {

    private val notice = MutableStateFlow<Notice?>(null)

    /** Arranca en `Loading`: nunca se emite una lista antes de tener política válida. */
    val state: StateFlow<HomeUiState> = combine(observeVisibleApps(), notice) { visible, notice ->
        HomeUiState(visible.toContent(limit = HOME_APP_LIMIT), notice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onAppClick(id: AppId) {
        viewModelScope.launch { notice.value = launchApp(id).toNotice() }
    }

    fun onNoticeShown() { notice.value = null }

    /** Reintento tras un error temporal de lectura (política o catálogo). */
    fun onRetry() {
        stateRepository.retry()
        viewModelScope.launch { catalog.refresh() }
    }
}
