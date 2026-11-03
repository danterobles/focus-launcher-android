package com.focuslauncher.domain.repository

import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.LaunchResult
import kotlinx.coroutines.flow.Flow

interface AppCatalogRepository {
    /** Mientras haya un colector el repositorio mantiene registrados sus callbacks. */
    val catalog: Flow<CatalogState>

    /** Solicita una nueva consulta (p. ej. al reanudar). */
    suspend fun refresh()

    /** Abre exactamente el componente de [id] en el perfil actual. No decide política. */
    suspend fun launch(id: AppId): LaunchResult
}
