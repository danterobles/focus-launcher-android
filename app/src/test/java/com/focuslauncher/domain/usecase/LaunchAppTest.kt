package com.focuslauncher.domain.usecase

import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.LaunchResult
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.SettingsReadState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchAppTest {
    private val a = app("p.a")
    private val b = app("p.b")
    private val catalog = FakeCatalog(CatalogState.Ready(listOf(a, b)))
    private val settings = FakeSettings(readySettings())
    private val launch = LaunchApp(ObserveVisibleApps(catalog, settings), catalog)

    @Test fun visibleAppIsLaunched() = runTest {
        assertEquals(LaunchOutcome.Launched, launch(a.id))
        assertEquals(listOf(a.id), catalog.launched)
    }

    @Test fun staleTapOnAnAppHiddenInTheMeantimeIsRejected() = runTest {
        settings.state.value = readySettings("p.a") // se oculta tras pintar la pantalla
        assertEquals(LaunchOutcome.NotVisible, launch(a.id))
        assertEquals(emptyList<Any>(), catalog.launched)
    }

    @Test fun everythingHiddenRejectsAndLaunchesNothing() = runTest {
        settings.state.value = readySettings("p.a", "p.b")
        assertEquals(LaunchOutcome.NotVisible, launch(a.id))
        assertEquals(emptyList<Any>(), catalog.launched)
    }

    @Test fun appThatDisappearedFromTheCatalogIsRejectedAndRefreshes() = runTest {
        catalog.state.value = CatalogState.Ready(listOf(b))
        assertEquals(LaunchOutcome.NotVisible, launch(a.id))
        assertEquals(1, catalog.refreshes)
    }

    @Test fun unreadablePolicyOpensNothing() = runTest {
        settings.state.value = SettingsReadState.ReadError(ReadErrorKind.StorageUnavailable)
        assertEquals(LaunchOutcome.SettingsUnavailable, launch(a.id))
        assertEquals(emptyList<Any>(), catalog.launched)
    }

    @Test fun systemRejectionsAreMappedAndUnavailableTriggersRefresh() = runTest {
        catalog.launchResult = LaunchResult.Unavailable
        assertEquals(LaunchOutcome.Unavailable, launch(a.id))
        assertEquals(1, catalog.refreshes)
        catalog.launchResult = LaunchResult.Failure
        assertEquals(LaunchOutcome.Failed, launch(a.id))
    }
}
