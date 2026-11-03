package com.focuslauncher.domain.usecase

import app.cash.turbine.test
import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.CatalogError
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.model.VisibleAppsState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveVisibleAppsTest {
    private val two = listOf(app("p.one", "p.one.A", "Uno A"), app("p.one", "p.one.B", "Uno B"), app("p.two"))
    private val catalog = FakeCatalog(CatalogState.Ready(two))
    private val settings = FakeSettings()
    private val observe = ObserveVisibleApps(catalog, settings)

    @Test fun neverEmitsAppsWhilePolicyIsLoading() = runTest {
        observe().test {
            assertEquals(VisibleAppsState.Loading, awaitItem())
            expectNoEvents() // la política pendiente NO equivale a «ningún oculto»
            settings.state.value = readySettings()
            assertEquals(3, (awaitItem() as VisibleAppsState.Ready).apps.size)
        }
    }

    @Test fun hidingAPackageRemovesAllItsActivities() = runTest {
        settings.state.value = readySettings("p.one")
        observe().test {
            val ready = awaitItem() as VisibleAppsState.Ready
            assertEquals(listOf("p.two"), ready.apps.map { it.id.packageName })
        }
    }

    @Test fun newAppsAreVisibleByDefaultAndShowingRestoresThem() = runTest {
        settings.state.value = readySettings("p.one")
        observe().test {
            awaitItem()
            catalog.state.value = CatalogState.Ready(two + app("p.new"))
            assertEquals(listOf("p.two", "p.new"), (awaitItem() as VisibleAppsState.Ready).apps.map { it.id.packageName })
            settings.state.value = readySettings()
            assertEquals(4, (awaitItem() as VisibleAppsState.Ready).apps.size)
        }
    }

    @Test fun ruleOfAnotherUserSerialDoesNotHide() = runTest {
        val other = com.focuslauncher.domain.model.AppPackageKey("p.two", com.focuslauncher.domain.model.UserSerial(9))
        settings.state.value = SettingsReadState.Ready(
            com.focuslauncher.domain.model.LauncherSettings(true, com.focuslauncher.domain.model.VisibilityPolicy(setOf(other)), false),
        )
        observe().test { assertEquals(3, (awaitItem() as VisibleAppsState.Ready).apps.size) }
    }

    @Test fun everythingHiddenIsEmptyNotAnError() = runTest {
        settings.state.value = readySettings("p.one", "p.two")
        observe().test { assertEquals(VisibleAppsState.Empty, awaitItem()) }
    }

    @Test fun emptyCatalogIsEmpty() = runTest {
        settings.state.value = readySettings()
        catalog.state.value = CatalogState.Ready(emptyList())
        observe().test { assertEquals(VisibleAppsState.Empty, awaitItem()) }
    }

    @Test fun readErrorAndRecoveryNeverExposeTheCatalog() = runTest {
        settings.state.value = SettingsReadState.ReadError(ReadErrorKind.StorageUnavailable)
        observe().test {
            assertEquals(VisibleAppsState.Blocked(BlockedReason.SettingsReadError(ReadErrorKind.StorageUnavailable)), awaitItem())
            settings.state.value = SettingsReadState.RecoveryRequired(RecoveryReason.KeyLost)
            assertEquals(VisibleAppsState.Blocked(BlockedReason.RecoveryRequired(RecoveryReason.KeyLost)), awaitItem())
        }
    }

    @Test fun policyFailingAfterBeingReadyBlocksInsteadOfShowingEverything() = runTest {
        settings.state.value = readySettings("p.one")
        observe().test {
            assertTrue(awaitItem() is VisibleAppsState.Ready)
            settings.state.value = SettingsReadState.ReadError(ReadErrorKind.KeystoreUnavailable)
            assertTrue(awaitItem() is VisibleAppsState.Blocked)
        }
    }

    @Test fun catalogErrorIsBlockedAndLoadingCatalogIsLoading() = runTest {
        settings.state.value = readySettings()
        catalog.state.value = CatalogState.Loading
        observe().test {
            assertEquals(VisibleAppsState.Loading, awaitItem())
            catalog.state.value = CatalogState.Error(CatalogError.QueryFailed)
            assertEquals(VisibleAppsState.Blocked(BlockedReason.CatalogFailure(CatalogError.QueryFailed)), awaitItem())
        }
    }
}
