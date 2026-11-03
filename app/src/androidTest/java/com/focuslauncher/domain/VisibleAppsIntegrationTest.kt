package com.focuslauncher.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focuslauncher.TestDispatchersInstr
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.data.android.AndroidAppCatalogRepository
import com.focuslauncher.data.android.AndroidAppIconLoader
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.data.local.StateWriteResult
import com.focuslauncher.data.local.proto.PackageKeyProto
import com.focuslauncher.data.repository.FocusStateRepositoryImpl
import com.focuslauncher.domain.model.VisibleAppsState
import com.focuslauncher.domain.usecase.LaunchApp
import com.focuslauncher.domain.usecase.LaunchOutcome
import com.focuslauncher.domain.usecase.ObserveVisibleApps
import com.focuslauncher.domain.usecase.SearchApps
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Catálogo real + fixtures reales (`com.focuslauncher.test`: 2 actividades LAUNCHER) + estado real
 * en Keystore (alias y archivo propios de la prueba).
 */
class VisibleAppsIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "focus_state_test_" + UUID.randomUUID().toString().replace("-", "")
    private val dir = File(context.cacheDir, "vis-" + UUID.randomUUID())
    private val fixturePackage = "com.focuslauncher.test"
    private lateinit var store: FocusStateStore
    private lateinit var observe: ObserveVisibleApps
    private lateinit var catalog: AndroidAppCatalogRepository
    private lateinit var launch: LaunchApp

    @Before fun setUp() {
        val dispatchers = TestDispatchersInstr()
        catalog = AndroidAppCatalogRepository(context, dispatchers, AndroidAppIconLoader(context, dispatchers))
        store = FocusStateStore(
            StateLocation(File(dir, "focus_state.pb.enc")), KeystoreStateCipher(alias),
            CoroutineScope(SupervisorJob() + Dispatchers.IO), context.packageName,
        )
        observe = ObserveVisibleApps(catalog, FocusStateRepositoryImpl(store))
        launch = LaunchApp(observe, catalog)
    }

    @After fun tearDown() = runBlocking {
        store.close(); dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { if (it.containsAlias(alias)) it.deleteEntry(alias) }
    }

    private suspend fun ready(predicate: (List<com.focuslauncher.domain.model.InstalledApp>) -> Boolean = { true }) =
        withTimeout(15_000) {
            (observe().first { it is VisibleAppsState.Ready && predicate(it.apps) } as VisibleAppsState.Ready).apps
        }

    private fun fixtures(apps: List<com.focuslauncher.domain.model.InstalledApp>) = apps.filter { it.id.packageName == fixturePackage }

    @Test fun hidingTheFixturePackageRemovesBothActivitiesAndShowingRestoresThem() = runBlocking {
        val before = ready()
        assertEquals(2, fixtures(before).size)
        val serial = before.first().id.userSerial.value

        assertEquals(StateWriteResult.Success, store.update {
            it.toBuilder().addHiddenPackages(PackageKeyProto.newBuilder().setPackageName(fixturePackage).setUserSerial(serial)).build()
        })
        val hidden = ready { fixtures(it).isEmpty() }
        assertEquals(0, fixtures(hidden).size)
        assertEquals(before.size - 2, hidden.size) // el resto del catálogo sigue visible

        // búsqueda con consulta activa: «fixture» ya no encuentra nada
        assertTrue(SearchApps.filter(hidden, "fixture").isEmpty())

        store.update { it.toBuilder().clearHiddenPackages().build() }
        assertEquals(2, fixtures(ready { fixtures(it).size == 2 }).size)
    }

    @Test fun staleLaunchOfAnAppHiddenInTheMeantimeIsRejectedWithoutOpeningIt() = runBlocking {
        val app = fixtures(ready()).first()
        store.update {
            it.toBuilder().addHiddenPackages(PackageKeyProto.newBuilder().setPackageName(fixturePackage).setUserSerial(app.id.userSerial.value)).build()
        }
        assertEquals(LaunchOutcome.NotVisible, launch(app.id))
    }

    @Test fun firstEmissionIsAlwaysLoadingNeverAnAppList() = runBlocking {
        // Estado inicial aún sin leer: la primera emisión jamás contiene apps.
        val first = withTimeout(15_000) { observe().first() }
        assertEquals(VisibleAppsState.Loading, first)
    }
}
