package com.focuslauncher.presentation.navigation

/** Destinos de la única Activity. Sin deep links ni argumentos externos. */
enum class Screen(val administrative: Boolean = false) {
    Welcome, Home, Drawer, Help,

    /** Editor de visibilidad (T07). */
    Settings(administrative = true),

    /** Crear/cambiar/deshabilitar PIN (T07). */
    Security(administrative = true),
}
