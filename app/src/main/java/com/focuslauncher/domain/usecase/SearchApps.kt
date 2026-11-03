package com.focuslauncher.domain.usecase

import com.focuslauncher.domain.model.InstalledApp
import java.text.Normalizer
import java.util.Locale

/**
 * Búsqueda local sobre entradas YA filtradas. Recorta espacios y compara sin distinguir mayúsculas
 * ni acentos. No guarda historial ni genera sugerencias.
 */
object SearchApps {
    /** Minúsculas sin marcas diacríticas ni espacios sobrantes (sin expresiones regulares: ruta caliente). */
    fun normalize(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        for (c in decomposed) if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) out.append(c)
        return out.toString().lowercase(Locale.ROOT)
    }

    fun filter(visibleApps: List<InstalledApp>, query: String): List<InstalledApp> =
        SearchIndex(visibleApps).filter(query)
}

/**
 * Índice de una lista visible: los nombres se normalizan UNA vez al crearlo, de modo que cada
 * pulsación solo normaliza la consulta y busca subcadenas (RNF-04: búsqueda ≤ 150 ms con 200 entradas).
 */
class SearchIndex(private val apps: List<InstalledApp>) {
    private val keys: List<String> = apps.map { SearchApps.normalize(it.label) }

    fun filter(query: String): List<InstalledApp> {
        val q = SearchApps.normalize(query)
        if (q.isEmpty()) return apps
        val result = ArrayList<InstalledApp>()
        for (i in apps.indices) if (q in keys[i]) result += apps[i]
        return result
    }
}
