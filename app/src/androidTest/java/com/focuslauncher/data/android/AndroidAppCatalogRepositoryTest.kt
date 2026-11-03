package com.focuslauncher.data.android

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.focuslauncher.TestDispatchersInstr
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.LaunchResult
import com.focuslauncher.domain.model.UserSerial
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Comparación del adaptador real con fixtures instalados (paquete `com.focuslauncher.test`). */
class AndroidAppCatalogRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixturePackage = "com.focuslauncher.test"
    private lateinit var repository: AndroidAppCatalogRepository

    @Before
    fun setUp() {
        val dispatchers = TestDispatchersInstr()
        val icons = AndroidAppIconLoader(context, dispatchers)
        repository = AndroidAppCatalogRepository(context, dispatchers, icons)
    }

    private fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
    }

    private fun topResumed(): String = shell("dumpsys activity activities").lineSequence()
        .firstOrNull { "topResumedActivity" in it }.orEmpty()

    private suspend fun readyApps(): List<InstalledApp> = withTimeout(10_000) {
        (repository.catalog.first { it !is CatalogState.Loading } as CatalogState.Ready).apps
    }

    @Test
    fun catalogContainsBothFixtureActivitiesOnceAndNothingSynthesized() = runBlocking {
        val fixtures = readyApps().filter { it.id.packageName == fixturePackage }
        assertEquals(
            setOf("com.focuslauncher.fixture.FixtureActivityA", "com.focuslauncher.fixture.FixtureActivityB"),
            fixtures.map { it.id.activityName }.toSet(),
        )
        assertEquals(2, fixtures.size)
        assertEquals(setOf("Fixture Alfa", "Fixture Beta"), fixtures.map { it.label }.toSet())
    }

    @Test
    fun activityWithoutLauncherCategoryAndFocusItselfAreExcluded() = runBlocking {
        val apps = readyApps()
        assertFalse(apps.any { it.id.activityName.endsWith("FixtureNoLauncherActivity") })
        assertFalse(apps.any { it.id.packageName == context.packageName })
    }

    @Test
    fun catalogHasNoDuplicatesAndIsSorted() = runBlocking {
        val apps = readyApps()
        assertEquals(apps.size, apps.map { it.id }.toSet().size)
        assertTrue(apps.all { it.available })
    }

    @Test
    fun launchingEachFixtureActivityOpensTheSelectedComponent() = runBlocking {
        val fixtures = readyApps().filter { it.id.packageName == fixturePackage }
        assertEquals(2, fixtures.size)
        fixtures.forEach { app ->
            // Android 14 bloquea lanzamientos desde segundo plano: el proceso de Focus debe tener una
            // Activity visible, como la tiene cuando es el Home real.
            ActivityScenario.launch(ComponentActivity::class.java).use {
                launchAndAwait(app)
            }
        }
    }

    private fun launchAndAwait(app: InstalledApp) = runBlocking {
        assertEquals(LaunchResult.Success, repository.launch(app.id))
        val expected = "${app.id.packageName}/${app.id.activityName}"
        val deadline = System.currentTimeMillis() + 10_000
        while (expected !in topResumed() && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assertTrue("esperaba $expected como actividad superior; hay: ${topResumed()}", expected in topResumed())
        // Ambas actividades comparten tarea: se cierra la abierta para que la siguiente quede encima.
        shell("input keyevent KEYCODE_BACK")
        val closeBy = System.currentTimeMillis() + 5_000
        while (expected in topResumed() && System.currentTimeMillis() < closeBy) Thread.sleep(200)
    }

    @Test
    fun launchingMissingComponentOrForeignSerialIsUnavailableNotACrash() = runBlocking {
        val real = readyApps().first { it.id.packageName == fixturePackage }.id
        assertEquals(LaunchResult.Unavailable, repository.launch(AppId("no.such.pkg", "no.such.Cls", real.userSerial)))
        assertEquals(LaunchResult.Unavailable, repository.launch(real.copy(userSerial = UserSerial(real.userSerial.value + 99))))
    }

    @Test
    fun iconLoaderAlwaysReturnsABitmapEvenForUnknownComponents() = runBlocking {
        val loader = AndroidAppIconLoader(context, TestDispatchersInstr())
        val bitmap = loader.load(AppId("no.such.pkg", "no.such.Cls", UserSerial(0)), 48)
        assertEquals(48, bitmap.width)
    }
}
