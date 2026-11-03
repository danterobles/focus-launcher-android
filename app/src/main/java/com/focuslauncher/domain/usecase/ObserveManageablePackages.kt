package com.focuslauncher.domain.usecase

import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.ManageablePackage
import com.focuslauncher.domain.model.ManageableState
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.repository.AppCatalogRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Lista administrable: catálogo agrupado por paquete + reglas conservadas de paquetes que hoy no
 * están instalados (tombstones, nunca se depuran solos). Una actividad nueva de un paquete oculto
 * aparece oculta porque la regla es del paquete.
 */
class ObserveManageablePackages @Inject constructor(
    private val catalog: AppCatalogRepository,
    private val state: FocusStateRepository,
    private val ownPackage: OwnPackage,
) {
    operator fun invoke(): Flow<ManageableState> = combine(state.settings, catalog.catalog) { settings, apps ->
        when (settings) {
            SettingsReadState.Loading -> ManageableState.Loading
            is SettingsReadState.ReadError -> ManageableState.Blocked(BlockedReason.SettingsReadError(settings.kind))
            is SettingsReadState.RecoveryRequired -> ManageableState.Blocked(BlockedReason.RecoveryRequired(settings.reason))
            is SettingsReadState.Ready -> {
                val hidden = settings.settings.visibility.hiddenPackages
                when (apps) {
                    CatalogState.Loading -> ManageableState.Loading
                    is CatalogState.Error ->
                        ManageableState.Ready(tombstones(hidden, emptySet()), settings.settings.pinEnabled, apps.reason)
                    is CatalogState.Ready -> {
                        val installed = apps.apps.filter { it.available }
                            .groupBy { it.id.packageKey } // conserva el orden del catálogo (por etiqueta)
                            .map { (key, entries) ->
                                ManageablePackage(
                                    key = key,
                                    title = entries.first().label,
                                    activityLabels = entries.map { it.label },
                                    hidden = key in hidden,
                                    installed = true,
                                )
                            }
                        ManageableState.Ready(
                            installed + tombstones(hidden, installed.map { it.key }.toSet()),
                            settings.settings.pinEnabled,
                            catalogError = null,
                        )
                    }
                }
            }
        }
    }

    private fun tombstones(hidden: Set<AppPackageKey>, installed: Set<AppPackageKey>): List<ManageablePackage> =
        hidden.filter { it !in installed && it.packageName != ownPackage.name }
            .sortedWith(compareBy({ it.packageName }, { it.userSerial.value }))
            .map { ManageablePackage(it, it.packageName, emptyList(), hidden = true, installed = false) }
}

/** Nombre del propio paquete (se inyecta para poder probar sin Android). */
class OwnPackage(val name: String)
