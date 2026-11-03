package com.focuslauncher.presentation.home

import app.cash.turbine.test
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.usecase.FakeCatalog
import com.focuslauncher.domain.usecase.FakeSettings
import com.focuslauncher.domain.usecase.LaunchApp
import com.focuslauncher.domain.usecase.ObserveVisibleApps
import com.focuslauncher.domain.usecase.app
import com.focuslauncher.domain.usecase.readySettings
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.common.Notice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val twelve = (1..12).map { app("p.$it", label = "App %02d".format(it)) }
    private val catalog = FakeCatalog(CatalogState.Ready(twelve))
    private val settings = FakeSettings()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = HomeViewModel(ObserveVisibleApps(catalog, settings), LaunchApp(ObserveVisibleApps(catalog, settings), catalog), settings, catalog)

    @Test fun startsLoadingAndNeverShowsAppsBeforeThePolicyIsValid() = runTest {
        vm().state.test {
            assertEquals(AppsContent.Loading, awaitItem().content)
            expectNoEvents()
            settings.state.value = readySettings()
            assertTrue(awaitItem().content is AppsContent.Apps)
        }
    }

    @Test fun showsOnlyTheFirstEightVisibleEntries() = runTest {
        settings.state.value = readySettings("p.1", "p.2") // se ocultan dos: entran las siguientes
        vm().state.test {
            val content = expectMostRecentItem().content as AppsContent.Apps
            assertEquals((3..10).map { "p.$it" }, content.apps.map { it.id.packageName })
            assertEquals(10, content.total)
        }
    }

    @Test fun hidingTheAppAfterPaintingRejectsTheTapAndRefreshesTheList() = runTest {
        settings.state.value = readySettings()
        val vm = vm()
        vm.state.test {
            val first = (expectMostRecentItem().content as AppsContent.Apps).apps.first()
            settings.state.value = readySettings(first.id.packageName)
            vm.onAppClick(first.id)
            assertEquals(emptyList<Any>(), catalog.launched)
            assertTrue((expectMostRecentItem().content as AppsContent.Apps).apps.none { it == first })
        }
    }

    @Test fun launchingShowsNoNoticeOnSuccess() = runTest {
        settings.state.value = readySettings()
        val vm = vm()
        vm.state.test {
            expectMostRecentItem()
            vm.onAppClick(twelve[0].id)
            assertEquals(listOf(twelve[0].id), catalog.launched)
            assertEquals(null, vm.state.value.notice)
        }
    }

    @Test fun unavailableAppShowsANotice() = runTest {
        settings.state.value = readySettings()
        catalog.launchResult = com.focuslauncher.domain.model.LaunchResult.Unavailable
        val vm = vm()
        vm.state.test {
            expectMostRecentItem()
            vm.onAppClick(twelve[0].id)
            assertEquals(Notice.AppUnavailable, expectMostRecentItem().notice)
            vm.onNoticeShown()
            assertEquals(null, expectMostRecentItem().notice)
        }
    }

    @Test fun retryAsksForANewPolicyReadAndANewCatalogQuery() = runTest {
        val vm = vm()
        vm.onRetry()
                assertEquals(1, settings.retries)
        assertEquals(1, catalog.refreshes)
    }
}
