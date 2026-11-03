package com.focuslauncher.domain.model

/** Un paquete tal como lo ve el editor: todas sus actividades comparten una sola regla. */
data class ManageablePackage(
    val key: AppPackageKey,
    /** Etiqueta de su primera actividad; el nombre del paquete si ya no está instalado. */
    val title: String,
    val activityLabels: List<String>,
    val hidden: Boolean,
    /** `false` = regla conservada (tombstone) de un paquete que hoy no está en el catálogo. */
    val installed: Boolean,
)

/** Estado del editor. Contiene apps ocultas: solo se muestra con administración autorizada. */
sealed interface ManageableState {
    data object Loading : ManageableState
    data class Ready(
        val packages: List<ManageablePackage>,
        val pinEnabled: Boolean,
        /** El catálogo falló: solo se pueden ver/quitar reglas existentes. */
        val catalogError: CatalogError?,
    ) : ManageableState
    data class Blocked(val reason: BlockedReason) : ManageableState
}
