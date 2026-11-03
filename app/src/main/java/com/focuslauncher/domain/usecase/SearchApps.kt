package com.focuslauncher.domain.usecase

import com.focuslauncher.domain.model.InstalledApp
import java.text.Normalizer
import java.util.Locale

/**
 * Búsqueda local sobre entradas YA filtradas. Recorta espacios y compara sin distinguir mayúsculas
 * ni acentos. No guarda historial ni genera sugerencias.
 */
object SearchApps {
    private val DIACRITICS = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        DIACRITICS.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    fun filter(visibleApps: List<InstalledApp>, query: String): List<InstalledApp> {
        val q = normalize(query)
        if (q.isEmpty()) return visibleApps
        return visibleApps.filter { q in normalize(it.label) }
    }
}
