package com.focuslauncher.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchAppsTest {
    private val apps = listOf(
        app("a", label = "Cámara"), app("b", label = "CALENDARIO"), app("c", label = "Música"),
        app("d", label = "Año Nuevo"), app("e", label = "Reloj"),
    )
    private fun labels(q: String) = SearchApps.filter(apps, q).map { it.label }

    @Test fun ignoresCaseAccentsAndSurroundingSpaces() {
        assertEquals(listOf("Cámara"), labels("camara"))
        assertEquals(listOf("Cámara"), labels("  CÁMARA  "))
        assertEquals(listOf("Música"), labels("musica"))
        assertEquals(listOf("Música"), labels("MÚSICA"))
        assertEquals(listOf("CALENDARIO"), labels("calendario"))
    }

    @Test fun matchesSubstrings() = assertEquals(listOf("Cámara", "CALENDARIO", "Música"), labels("ca"))

    @Test fun blankQueryReturnsEverythingInOriginalOrder() {
        assertEquals(apps.map { it.label }, labels(""))
        assertEquals(apps.map { it.label }, labels("   "))
    }

    @Test fun noMatchesIsEmpty() = assertEquals(emptyList<String>(), labels("zzz"))

    @Test fun enyeMatchesUnaccentedLetter() = assertEquals(listOf("Año Nuevo"), labels("ano"))
}
