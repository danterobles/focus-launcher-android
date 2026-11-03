package com.focuslauncher.domain.model

/** Por qué no se pueden mostrar apps. En ningún caso se degrada a «mostrar todo». */
sealed interface BlockedReason {
    data class SettingsReadError(val kind: ReadErrorKind) : BlockedReason
    data class RecoveryRequired(val reason: com.focuslauncher.domain.model.RecoveryReason) : BlockedReason
    data class CatalogFailure(val error: CatalogError) : BlockedReason
}

/** Entradas ya filtradas por la política. Es la única fuente de apps para Home, cajón y búsqueda. */
sealed interface VisibleAppsState {
    /** Falta la política o el catálogo: no se muestra ninguna app. */
    data object Loading : VisibleAppsState
    data class Ready(val apps: List<InstalledApp>) : VisibleAppsState

    /** Lectura correcta, pero no hay entradas visibles (todo oculto o catálogo vacío). */
    data object Empty : VisibleAppsState
    data class Blocked(val reason: BlockedReason) : VisibleAppsState
}
