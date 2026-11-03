package com.focuslauncher.domain.usecase

import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.model.VisibleAppsState
import com.focuslauncher.domain.repository.AppCatalogRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Combina catálogo y política. Regla central: no se emite ninguna lista hasta que la política sea
 * válida (`Ready`). Un valor pendiente o un error de política NO equivale a «ningún paquete oculto».
 */
class ObserveVisibleApps @Inject constructor(
    private val catalog: AppCatalogRepository,
    private val state: FocusStateRepository,
) {
    operator fun invoke(): Flow<VisibleAppsState> =
        combine(state.settings, catalog.catalog) { settings, apps ->
            when (settings) {
                SettingsReadState.Loading -> VisibleAppsState.Loading
                is SettingsReadState.ReadError -> VisibleAppsState.Blocked(BlockedReason.SettingsReadError(settings.kind))
                is SettingsReadState.RecoveryRequired -> VisibleAppsState.Blocked(BlockedReason.RecoveryRequired(settings.reason))
                is SettingsReadState.Ready -> when (apps) {
                    CatalogState.Loading -> VisibleAppsState.Loading
                    is CatalogState.Error -> VisibleAppsState.Blocked(BlockedReason.CatalogFailure(apps.reason))
                    is CatalogState.Ready -> {
                        val hidden = settings.settings.visibility.hiddenPackages
                        val visible = apps.apps.filter { it.available && it.id.packageKey !in hidden }
                        if (visible.isEmpty()) VisibleAppsState.Empty else VisibleAppsState.Ready(visible)
                    }
                }
            }
        }.distinctUntilChanged()
}
