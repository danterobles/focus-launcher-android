package com.focuslauncher.domain.model

@JvmInline
value class UserSerial(val value: Long)

/** Identifica una entrada del launcher: componente completo + serial de usuario. */
data class AppId(
    val packageName: String,
    val activityName: String, // nombre completo de clase, nunca la etiqueta traducida
    val userSerial: UserSerial,
) {
    val packageKey: AppPackageKey get() = AppPackageKey(packageName, userSerial)
}

/** Identifica una regla de ocultación: paquete + serial de usuario. */
data class AppPackageKey(
    val packageName: String,
    val userSerial: UserSerial,
)

data class InstalledApp(
    val id: AppId,
    val label: String,
    val available: Boolean,
)
