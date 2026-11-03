package com.focuslauncher.domain.model

data class VisibilityPolicy(
    val hiddenPackages: Set<AppPackageKey>,
)

data class LauncherSettings(
    val onboardingAccepted: Boolean,
    val visibility: VisibilityPolicy,
    /** Proyección de la configuración del PIN; el verificador nunca sale de data. */
    val pinEnabled: Boolean,
)

/** Fallo de lectura recuperable reintentando (no equivale a «sin reglas» ni a «sin PIN»). */
enum class ReadErrorKind { StorageUnavailable, KeystoreUnavailable }

/** El estado no puede usarse y reintentar no lo arregla; no se restablece automáticamente. */
enum class RecoveryReason { KeyLost, Corrupt, FutureVersion, InvalidState, InterruptedInit }

sealed interface SettingsReadState {
    data object Loading : SettingsReadState
    data class Ready(val settings: LauncherSettings) : SettingsReadState
    data class ReadError(val kind: ReadErrorKind) : SettingsReadState
    data class RecoveryRequired(val reason: RecoveryReason) : SettingsReadState
}
