package com.focuslauncher.presentation.navigation

/** Salidas seguras a Ajustes de Android (intents fijos del sistema; nunca se construyen desde datos externos). */
enum class SystemSettingsTarget {
    /** Elegir la app de inicio (volver a otro launcher). */
    HomeSettings,

    /** Información de la app de Focus: Almacenamiento → Borrar datos, o desinstalar. */
    AppDetails,

    /** Ajustes generales de Android. */
    AndroidSettings,
}
