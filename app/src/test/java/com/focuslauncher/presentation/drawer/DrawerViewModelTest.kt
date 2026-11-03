package com.focuslauncher.presentation.drawer

import app.cash.turbine.test
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.usecase.FakeCatalog
import com.focuslauncher.domain.usecase.FakeSettings
import com.focuslauncher.domain.usecase.LaunchApp
import com.focuslauncher.domain.usecase.ObserveVisibleApps
import com.focuslauncher.domain.usecase.app
import com.focuslauncher.domain.usecase.readySettings
import com.focuslauncher.presentation.common.AppsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerViewModelTest {
    // Un paquete con DOS actividades y nombres con acentos.
    private val camaraA = app("cam.pkg", "cam.pkg.Main", "Cámara")
    private val camaraB = app("cam.pkg", "cam.pkg.Video", "Cámara de vídeo")
    private val musica = app("mus.pkg", label = "Música")
    private val catalog = FakeCatalog(CatalogState.Ready(listOf(camaraA, camaraB, musica)))
    private val settings = FakeSettings(readySettings())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DrawerViewModel(ObserveVisibleApps(catalog, settings), LaunchApp(ObserveVisibleApps(catalog, settings), catalog))
    private fun DrawerUiState.packages() = (content as AppsContent.Apps).apps.map { it.id.activityName }

    @Test fun showsEveryVisibleEntryAndSearchesWithoutAccents() = runTest {
        val vm = vm()
        vm.state.test {
            assertEquals(listOf("cam.pkg.Main", "cam.pkg.Video", "mus.pkg.Main"), expectMostRecentItem().packages())
            vm.onQueryChange("  CAMARA ")
            assertEquals(listOf("cam.pkg.Main", "cam.pkg.Video"), expectMostRecentItem().packages())
            vm.onQueryChange("musica")
            assertEquals(listOf("mus.pkg.Main"), expectMostRecentItem().packages())
        }
    }

    @Test fun hidingThePackageWithAnActiveQueryRemovesBothActivitiesFromResults() = runTest {
        val vm = vm()
        vm.state.test {
            vm.onQueryChange("camara")
            assertEquals(2, expectMostRecentItem().packages().size)
            settings.state.value = readySettings("cam.pkg")
            assertEquals(AppsContent.NoResults, expectMostRecentItem().content)
            vm.onQueryChange("")
            assertEquals(listOf("mus.pkg.Main"), expectMostRecentItem().packages())
        }
    }

    @Test fun searchNeverReachesHiddenAppsEvenWithExactNames() = runTest {
        settings.state.value = readySettings("mus.pkg")
        val vm = vm()
        vm.state.test {
            vm.onQueryChange("Música")
            assertEquals(AppsContent.NoResults, expectMostRecentItem().content)
        }
    }

    @Test fun allHiddenIsEmptyAndClearingTheQueryResets() = runTest {
        settings.state.value = readySettings("cam.pkg", "mus.pkg")
        val vm = vm()
        vm.state.test {
            vm.onQueryChange("x")
            assertEquals(AppsContent.Empty, expectMostRecentItem().content)
            vm.clearQuery()
            assertEquals("", expectMostRecentItem().query)
        }
    }

    @Test fun policyNotReadyShowsNoAppsAtAll() = runTest {
        settings.state.value = com.focuslauncher.domain.model.SettingsReadState.Loading
        val vm = vm()
        vm.state.test {
            vm.onQueryChange("camara")
            assertEquals(AppsContent.Loading, expectMostRecentItem().content)
        }
    }
}
