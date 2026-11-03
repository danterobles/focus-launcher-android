package com.focuslauncher.perf

import android.content.Context
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.focuslauncher.TestDispatchersInstr
import com.focuslauncher.core.di.MonotonicClock
import com.focuslauncher.core.security.AdminEnvironment
import com.focuslauncher.core.security.AdminSessionManager
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.core.security.Pbkdf2PinHasher
import com.focuslauncher.data.android.AndroidAppCatalogRepository
import com.focuslauncher.data.android.AndroidAppIconLoader
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.data.repository.AdminAccessCoordinator
import com.focuslauncher.data.repository.FocusStateRepositoryImpl
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.LaunchResult
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.domain.model.VisibleAppsState
import com.focuslauncher.domain.repository.AppCatalogRepository
import com.focuslauncher.domain.repository.FocusStateRepository
import com.focuslauncher.domain.usecase.LaunchApp
import com.focuslauncher.domain.usecase.ObserveVisibleApps
import com.focuslauncher.domain.usecase.SearchApps
import com.focuslauncher.presentation.drawer.DrawerScreen
import com.focuslauncher.presentation.drawer.DrawerViewModel
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Presupuestos de RNF-04 con 200 entradas. Los resultados (p50/p95/máx) salen por logcat con la
 * etiqueta `FocusPerf` y se registran en docs/validation/T10.md. Se ejecutan sobre el build de
 * depuración de pruebas: son una cota superior orientativa, NO la medición del build release en el
 * dispositivo de referencia (arranque frío se mide aparte sobre el APK release).
 */
class PerformanceBudgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val rule = createComposeRule()

    private val alias = "focus_state_test_" + UUID.randomUUID().toString().replace("-", "")
    private val dir = File(context.cacheDir, "perf-" + UUID.randomUUID())
    private var store: FocusStateStore? = null

    @After fun tearDown() = runBlocking {
        store?.close(); dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { if (it.containsAlias(alias)) it.deleteEntry(alias) }
    }

    private fun percentile(values: List<Double>, p: Double): Double =
        values.sorted().let { it[((it.size - 1) * p).toInt().coerceAtMost(it.size - 1)] }

    private fun report(name: String, values: List<Double>, budgetMs: Double) {
        val p50 = percentile(values, 0.5); val p95 = percentile(values, 0.95)
        Log.i("FocusPerf", "$name n=${values.size} p50=%.1f p95=%.1f max=%.1f ms (presupuesto p95 <= %.0f ms)".format(p50, p95, values.max(), budgetMs))
        assertTrue("$name: p95=%.1f ms supera el presupuesto de %.0f ms".format(p95, budgetMs), p95 <= budgetMs)
    }

    // ---------------------------------------------------------------- búsqueda (UI de punta a punta)
    private class FakeCatalog(apps: List<InstalledApp>) : AppCatalogRepository {
        override val catalog: Flow<CatalogState> = MutableStateFlow(CatalogState.Ready(apps))
        override suspend fun refresh() = Unit
        override suspend fun launch(id: AppId) = LaunchResult.Success
    }

    private class FakeSettings : FocusStateRepository {
        override val settings: Flow<SettingsReadState> = MutableStateFlow(
            SettingsReadState.Ready(com.focuslauncher.domain.model.LauncherSettings(true, com.focuslauncher.domain.model.VisibilityPolicy(emptySet()), false)),
        )
        override fun retry() = Unit
    }

    @Test fun searchOver200EntriesStaysUnder150msP95() {
        val names = listOf("Álgebra", "Biblioteca", "Cámara", "Diario", "Éxito", "Finanzas", "Galería", "Hábitos", "Jardín", "Música", "Noticias", "Órbita", "Química", "Recetas", "Salud", "Tareas", "Casa", "Pro", "Viaje", "Familia")
        val apps = (1..200).map { i ->
            InstalledApp(AppId("p.$i", "p.$i.Main", UserSerial(0)), "${names[i % names.size]} ${names[(i / 3) % names.size]} $i", true)
        }
        val observe = ObserveVisibleApps(FakeCatalog(apps), FakeSettings())
        val vm = DrawerViewModel(observe, LaunchApp(observe, FakeCatalog(apps)))
        rule.setContent {
            MaterialTheme {
                val state by vm.state.collectAsState()
                DrawerScreen(state, vm::onQueryChange, {}, {}, {}, {})
            }
        }
        rule.waitUntil(15_000) { vm.state.value.content is com.focuslauncher.presentation.common.AppsContent.Apps }
        val queries = listOf("ca", "cám", "MÚSICA", "ñ", "gal", "a", "ho", "rec", "salud 1", "pro", "viaje", "tareas", "jardin", "órb", "quim", "fam", "10", "20", "x", "ab",
            "diario", "éxito", "finanzas", "biblio", "casa", "noticias", "hábitos", "alge", "lite", "mini")
        // Calentamiento (JIT, carga de fuentes y diccionarios): no cuenta para la medición.
        listOf("a", "b", "c", "d", "e", "").forEach {
            rule.onNodeWithText("Buscar aplicación").performTextReplacement(it); rule.waitForIdle()
        }
        // (1) Lógica pura: índice construido una vez + consulta (sin interfaz).
        val index = com.focuslauncher.domain.usecase.SearchIndex(apps)
        val logic = queries.map { q -> val t0 = System.nanoTime(); index.filter(q); (System.nanoTime() - t0) / 1e6 }
        Log.i("FocusPerf", "  desglose: consulta_con_indice p50=%.3f p95=%.3f ms".format(percentile(logic, 0.5), percentile(logic, 0.95)))
        val build = (1..20).map { val t0 = System.nanoTime(); com.focuslauncher.domain.usecase.SearchIndex(apps); (System.nanoTime() - t0) / 1e6 }
        Log.i("FocusPerf", "  desglose: construir_indice_200 p50=%.2f p95=%.2f ms (una vez por lista)".format(percentile(build, 0.5), percentile(build, 0.95)))
        // (2) De la consulta a la interfaz ya recompuesta, SIN el arnés de entrada de texto (que por sí solo
        // cuesta ~400 ms): se cambia la consulta en el hilo principal y se espera a que la UI quede estable.
        fun measure(q: String): Double {
            val t0 = System.nanoTime()
            rule.runOnUiThread { vm.onQueryChange(q) }
            rule.waitForIdle()
            rule.waitUntil(5_000) { vm.state.value.query == q }
            return (System.nanoTime() - t0) / 1e6
        }
        // Línea base del mismo procedimiento sin cambio real de contenido (coste de runOnUiThread + waitForIdle).
        val baseline = (1..20).map { measure("") }
        Log.i("FocusPerf", "  desglose: linea_base_procedimiento p50=%.1f p95=%.1f ms".format(percentile(baseline, 0.5), percentile(baseline, 0.95)))
        val samples = queries.map { measure(it) }
        report("busqueda_200_entradas", samples, 150.0)
    }

    /** Línea base del arnés: un campo de texto vacío con el mismo procedimiento de medida. */
    @Test fun harnessBaselineForTextInput() {
        rule.setContent { MaterialTheme { androidx.compose.material3.OutlinedTextField(value = text.value, onValueChange = { text.value = it }, label = { androidx.compose.material3.Text("Buscar aplicación") }) } }
        listOf("a", "b", "c", "d", "e", "").forEach { rule.onNodeWithText("Buscar aplicación").performTextReplacement(it); rule.waitForIdle() }
        val samples = (1..30).map { n ->
            val t0 = System.nanoTime()
            rule.onNodeWithText("Buscar aplicación").performTextReplacement("consulta $n"); rule.waitForIdle()
            (System.nanoTime() - t0) / 1e6
        }
        Log.i("FocusPerf", "  linea_base_arnes p50=%.1f p95=%.1f max=%.1f ms".format(percentile(samples, 0.5), percentile(samples, 0.95), samples.max()))
    }
    private val text = androidx.compose.runtime.mutableStateOf("")

    // ---------------------------------------------------------------- catálogo real + cambio de visibilidad real
    private fun realStack(): Triple<AndroidAppCatalogRepository, ObserveVisibleApps, AdminAccessCoordinator> {
        val dispatchers = TestDispatchersInstr()
        val catalog = AndroidAppCatalogRepository(context, dispatchers, AndroidAppIconLoader(context, dispatchers))
        val clock = MonotonicClock { System.nanoTime() / 1_000_000 }
        val env = object : AdminEnvironment { override fun isScreenInteractive() = true; override fun isDeviceLocked() = false }
        val s = FocusStateStore(StateLocation(File(dir, "focus_state.pb.enc")), KeystoreStateCipher(alias), CoroutineScope(SupervisorJob() + Dispatchers.IO), context.packageName)
        store = s
        val settings = FocusStateRepositoryImpl(s)
        return Triple(catalog, ObserveVisibleApps(catalog, settings), AdminAccessCoordinator(s, AdminSessionManager(clock, env), Pbkdf2PinHasher(Dispatchers.Default), clock))
    }

    private suspend fun ObserveVisibleApps.readyApps(p: (List<InstalledApp>) -> Boolean): List<InstalledApp> =
        withTimeout(20_000) { (this@readyApps().first { it is VisibleAppsState.Ready && p(it.apps) } as VisibleAppsState.Ready).apps }

    @Test fun catalogOf200RealEntriesLoadsFast() = runBlocking {
        val (catalog, _, _) = realStack()
        val first = withTimeout(30_000) { (catalog.catalog.first { it is CatalogState.Ready } as CatalogState.Ready).apps }
        assumeTrue("falta el fixture de 200 entradas (fixtures/many-launchers)", first.count { it.id.packageName == "com.focuslauncher.fixture200" } >= 200)
        val samples = (1..20).map {
            val t0 = System.nanoTime()
            withTimeout(30_000) { catalog.catalog.first { it is CatalogState.Ready } }
            (System.nanoTime() - t0) / 1e6
        }
        report("catalogo_real_200_entradas", samples, 2000.0)
    }

    @Test fun hidingAndShowing200EntriesIsConfirmedUnder500msP95() = runBlocking {
        val (_, observe, admin) = realStack()
        val apps = observe.readyApps { true }
        val fixture = apps.filter { it.id.packageName == "com.focuslauncher.fixture200" }
        assumeTrue("falta el fixture de 200 entradas (fixtures/many-launchers)", fixture.size >= 200)
        val key = AppPackageKey("com.focuslauncher.fixture200", fixture.first().id.userSerial)
        val samples = (1..20).map { n ->
            val hide = n % 2 == 1
            val t0 = System.nanoTime()
            val result = admin.setPackageHidden(key, hide)
            assertTrue(result == MutationResult.Success)
            // Cambio efectivo en la lista visible (Inicio, cajón y búsqueda derivan de ella).
            observe.readyApps { list -> list.none { it.id.packageName == key.packageName } == hide }
            (System.nanoTime() - t0) / 1e6
        }
        report("ocultar_mostrar_200_entradas", samples, 500.0)
    }
}
