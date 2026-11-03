package com.focuslauncher.presentation.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.VisibleAppsState
import com.focuslauncher.domain.usecase.LaunchApp
import com.focuslauncher.domain.usecase.ObserveVisibleApps
import com.focuslauncher.domain.usecase.SearchIndex
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrawerUiState(
    val query: String = "",
    val content: AppsContent = AppsContent.Loading,
    val notice: Notice? = null,
)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    observeVisibleApps: ObserveVisibleApps,
    private val launchApp: LaunchApp,
) : ViewModel() {

    // La consulta vive solo en memoria (ni SavedStateHandle ni historial persistente).
    private val query = MutableStateFlow("")
    private val notice = MutableStateFlow<Notice?>(null)

    /**
     * La búsqueda deriva SIEMPRE de la lista ya filtrada por la política, nunca del catálogo
     * completo: al ocultar una app con una consulta activa desaparece también de los resultados.
     */
    /** La lista visible con su índice de búsqueda: se reconstruye solo cuando cambia la lista, no por pulsación. */
    private class Indexed(val visible: VisibleAppsState, val index: SearchIndex?)

    private val indexed = observeVisibleApps().map { visible ->
        Indexed(visible, (visible as? VisibleAppsState.Ready)?.let { SearchIndex(it.apps) })
    }

    val state: StateFlow<DrawerUiState> = combine(indexed, query, notice) { indexed, q, notice ->
        val content = when (val visible = indexed.visible) {
            is VisibleAppsState.Ready -> {
                val results = indexed.index!!.filter(q)
                if (results.isEmpty()) AppsContent.NoResults else AppsContent.Apps(results, visible.apps.size)
            }
            else -> indexed.visible.toContent()
        }
        DrawerUiState(q, content, notice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun clearQuery() { query.value = "" }

    fun onAppClick(id: AppId) {
        viewModelScope.launch { notice.value = launchApp(id).toNotice() }
    }

    fun onNoticeShown() { notice.value = null }
}
