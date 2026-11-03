package com.focuslauncher.domain.model

/** Estado del catálogo. Una lista vacía (`Ready`) es distinta de un error. */
sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val apps: List<InstalledApp>) : CatalogState
    data class Error(val reason: CatalogError) : CatalogState
}

enum class CatalogError {
    /** No se pudo obtener un serial de usuario válido; no se usa un serial por defecto. */
    InvalidUserSerial,

    /** Fallo de lectura de LauncherApps / PackageManager. */
    QueryFailed,
}

sealed interface LaunchResult {
    data object Success : LaunchResult

    /** La entrada ya no existe, está deshabilitada o su perfil no está disponible. */
    data object Unavailable : LaunchResult

    /** Rechazo del sistema (permisos, componente no exportado…). */
    data object Failure : LaunchResult
}
