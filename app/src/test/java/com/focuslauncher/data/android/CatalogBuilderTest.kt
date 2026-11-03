package com.focuslauncher.data.android

import com.focuslauncher.domain.model.UserSerial
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBuilderTest {

    private val own = "com.focuslauncher"
    private val serial = UserSerial(0)
    private val es = Locale.forLanguageTag("es-MX")

    private fun entry(pkg: String, cls: String, label: String? = cls) = LauncherEntry(pkg, cls, label)
    private fun real(pkg: String, cls: String, enabled: Boolean = true, exported: Boolean = true) =
        ResolvedLauncherActivity(pkg, cls, enabled, exported)

    private fun build(entries: List<LauncherEntry>, resolved: List<ResolvedLauncherActivity>) =
        CatalogBuilder.build(entries, resolved, own, serial, es)

    @Test
    fun twoActivitiesOfOnePackageAppearOnceEachWithSharedPackageKey() {
        val apps = build(
            listOf(entry("a.app", "a.app.One", "Uno"), entry("a.app", "a.app.Two", "Dos")),
            listOf(real("a.app", "a.app.One"), real("a.app", "a.app.Two")),
        )
        assertEquals(2, apps.size)
        assertEquals(setOf("a.app.One", "a.app.Two"), apps.map { it.id.activityName }.toSet())
        assertEquals(1, apps.map { it.id.packageKey }.toSet().size)
    }

    @Test
    fun synthesizedEntriesWithoutRealLauncherActivityAreExcluded() {
        val apps = build(
            listOf(entry("b.app", "b.app.Details", "Detalles"), entry("a.app", "a.app.Main")),
            listOf(real("a.app", "a.app.Main")),
        )
        assertEquals(listOf("a.app.Main"), apps.map { it.id.activityName })
    }

    @Test
    fun ownPackageIsExcluded() {
        val apps = build(listOf(entry(own, "$own.MainActivity")), listOf(real(own, "$own.MainActivity")))
        assertTrue(apps.isEmpty())
    }

    @Test
    fun disabledAndNonExportedComponentsAreExcluded() {
        val apps = build(
            listOf(entry("a.app", "a.app.Off"), entry("a.app", "a.app.Private"), entry("a.app", "a.app.Ok")),
            listOf(
                real("a.app", "a.app.Off", enabled = false),
                real("a.app", "a.app.Private", exported = false),
                real("a.app", "a.app.Ok"),
            ),
        )
        assertEquals(listOf("a.app.Ok"), apps.map { it.id.activityName })
    }

    @Test
    fun exactDuplicatesByAppIdAreCollapsed() {
        val apps = build(
            listOf(entry("a.app", "a.app.Main"), entry("a.app", "a.app.Main")),
            listOf(real("a.app", "a.app.Main"), real("a.app", "a.app.Main")),
        )
        assertEquals(1, apps.size)
    }

    @Test
    fun sortsWithLocaleCollationAndBreaksTiesByPackageThenComponent() {
        val entries = listOf(
            entry("z.pkg", "z.pkg.A", "Zeta"),
            entry("p.pkg", "p.pkg.B", "Árbol"),
            entry("q.pkg", "q.pkg.A", "alfa"),
            entry("b.pkg", "b.pkg.Y", "Igual"),
            entry("b.pkg", "b.pkg.X", "Igual"),
            entry("a.pkg", "a.pkg.M", "Igual"),
        )
        val apps = build(entries, entries.map { real(it.packageName, it.className) })
        assertEquals(
            listOf("alfa", "Árbol", "Igual", "Igual", "Igual", "Zeta"),
            apps.map { it.label },
        )
        val equalOnes = apps.filter { it.label == "Igual" }.map { it.id.packageName to it.id.activityName }
        assertEquals(listOf("a.pkg" to "a.pkg.M", "b.pkg" to "b.pkg.X", "b.pkg" to "b.pkg.Y"), equalOnes)
    }

    @Test
    fun missingLabelFallsBackToPackageName() {
        val apps = build(listOf(entry("a.app", "a.app.Main", label = "  ")), listOf(real("a.app", "a.app.Main")))
        assertEquals("a.app", apps.single().label)
    }

    @Test
    fun entriesCarryTheCurrentUserSerial() {
        val apps = CatalogBuilder.build(
            listOf(entry("a.app", "a.app.Main")), listOf(real("a.app", "a.app.Main")), own, UserSerial(7), es,
        )
        assertEquals(UserSerial(7), apps.single().id.userSerial)
    }

    @Test
    fun invalidSerialIsNeverDefaultedToZero() {
        assertNull(CatalogBuilder.validSerialOrNull(-1))
        assertNull(CatalogBuilder.validSerialOrNull(Long.MIN_VALUE))
        assertNotNull(CatalogBuilder.validSerialOrNull(0))
        assertEquals(UserSerial(0), CatalogBuilder.validSerialOrNull(0))
    }
}
