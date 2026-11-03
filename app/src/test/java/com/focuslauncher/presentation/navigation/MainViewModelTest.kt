package com.focuslauncher.presentation.navigation

import android.content.Intent
import app.cash.turbine.test
import com.focuslauncher.TestDispatchers
import com.focuslauncher.core.home.HomeRoleController
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.LaunchResult
import com.focuslauncher.domain.repository.AppCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRoleController(var status: HomeRoleStatus, val intent: Intent? = null) : HomeRoleController {
        var statusCalls = 0
        var requestIntentCalls = 0
        override fun status(): HomeRoleStatus { statusCalls++; return status }
        override fun createRequestIntent(): Intent? { requestIntentCalls++; return intent }
    }

    private class FakeCatalog : AppCatalogRepository {
        override val catalog: Flow<CatalogState> = flowOf(CatalogState.Ready(emptyList()))
        override suspend fun refresh() = Unit
        override suspend fun launch(id: AppId) = LaunchResult.Success
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(controller: FakeRoleController) =
        MainViewModel(controller, FakeCatalog(), TestDispatchers(dispatcher))

    @Test
    fun homeIntentReturnsToHomeFromAnyScreen() = runTest(dispatcher) {
        val vm = viewModel(FakeRoleController(HomeRoleStatus.Held))
        Screen.entries.forEach { screen ->
            vm.navigateTo(screen)
            vm.onHomeIntent()
            assertEquals(Screen.Home, vm.state.value.screen)
        }
    }

    @Test
    fun backFromSecondaryScreenGoesHome() = runTest(dispatcher) {
        val vm = viewModel(FakeRoleController(HomeRoleStatus.Held))
        vm.navigateTo(Screen.Settings)
        vm.navigateBack()
        assertEquals(Screen.Home, vm.state.value.screen)
    }

    @Test
    fun refreshingRoleNeverRequestsIt() = runTest(dispatcher) {
        val controller = FakeRoleController(HomeRoleStatus.NotHeld)
        val vm = viewModel(controller)
        vm.events.test {
            vm.refreshRoleStatus()
            vm.refreshRoleStatus()
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(HomeRoleStatus.NotHeld, vm.state.value.roleStatus)
        assertEquals(0, controller.requestIntentCalls)
    }

    @Test
    fun explicitRequestEmitsOfficialIntentOnce() = runTest(dispatcher) {
        val intent = Intent("test.REQUEST_ROLE")
        val vm = viewModel(FakeRoleController(HomeRoleStatus.NotHeld, intent))
        vm.events.test {
            vm.onRequestHomeRole()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(MainEvent.LaunchRoleRequest(intent), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun requestWhenAlreadyHeldDoesNothing() = runTest(dispatcher) {
        val controller = FakeRoleController(HomeRoleStatus.Held)
        val vm = viewModel(controller)
        vm.events.test {
            vm.onRequestHomeRole()
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(0, controller.requestIntentCalls)
    }

    @Test
    fun unavailableRoleFallsBackToHomeSettings() = runTest(dispatcher) {
        val vm = viewModel(FakeRoleController(HomeRoleStatus.Unavailable))
        vm.events.test {
            vm.onRequestHomeRole()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(MainEvent.OpenHomeSettings, awaitItem())
        }
    }

    @Test
    fun roleLossIsReflectedOnNextRefresh() = runTest(dispatcher) {
        val controller = FakeRoleController(HomeRoleStatus.Held)
        val vm = viewModel(controller)
        vm.refreshRoleStatus(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeRoleStatus.Held, vm.state.value.roleStatus)
        controller.status = HomeRoleStatus.NotHeld
        vm.refreshRoleStatus(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeRoleStatus.NotHeld, vm.state.value.roleStatus)
    }
}
