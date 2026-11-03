package com.focuslauncher.domain.usecase

import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.LaunchResult
import com.focuslauncher.domain.model.VisibleAppsState
import com.focuslauncher.domain.repository.AppCatalogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

enum class LaunchOutcome {
    Launched,

    /** La entrada ya no es visible (p. ej. se ocultó tras pintar la pantalla): el toque obsoleto se rechaza. */
    NotVisible,

    /** Desapareció o dejó de estar disponible. */
    Unavailable,
    Failed,

    /** No hay política válida: no se abre nada. */
    SettingsUnavailable,
}

/**
 * Único camino de UI para abrir una app. Revalida política y catálogo justo antes de delegar al
 * sistema; el adaptador Android no se expone a los Composables.
 */
class LaunchApp @Inject constructor(
    private val observeVisibleApps: ObserveVisibleApps,
    private val catalog: AppCatalogRepository,
) {
    suspend operator fun invoke(id: AppId): LaunchOutcome {
        val current = observeVisibleApps().first { it !is VisibleAppsState.Loading }
        return when (current) {
            // `first` ya descartó Loading; por seguridad cualquier otra cosa no abre nada.
            VisibleAppsState.Loading, is VisibleAppsState.Blocked -> LaunchOutcome.SettingsUnavailable
            VisibleAppsState.Empty -> LaunchOutcome.NotVisible
            is VisibleAppsState.Ready -> {
                if (current.apps.none { it.id == id }) {
                    catalog.refresh()
                    return LaunchOutcome.NotVisible
                }
                when (catalog.launch(id)) {
                    LaunchResult.Success -> LaunchOutcome.Launched
                    LaunchResult.Unavailable -> { catalog.refresh(); LaunchOutcome.Unavailable }
                    LaunchResult.Failure -> LaunchOutcome.Failed
                }
            }
        }
    }
}
