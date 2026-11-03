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

    @Test fun theIndexGivesTheSameResultsAsFilteringDirectly() {
        val index = SearchIndex(apps)
        listOf("", "  ", "ca", "CÁMARA", "musica", "ano", "zzz", " re ").forEach { q ->
            assertEquals(q, SearchApps.filter(apps, q).map { it.id }, index.filter(q).map { it.id })
        }
    }

    @Test fun normalizeKeepsLettersAndStripsOnlyDiacritics() {
        assertEquals("camara", SearchApps.normalize("  CÁMARA "))
        assertEquals("ano nuevo", SearchApps.normalize("Año Nuevo"))
        assertEquals("", SearchApps.normalize("   "))
        assertEquals("fotos 2", SearchApps.normalize("Fotos 2"))
    }
}
