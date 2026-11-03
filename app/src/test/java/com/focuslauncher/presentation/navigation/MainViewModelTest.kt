package com.focuslauncher.presentation.navigation

import android.content.Intent
import app.cash.turbine.test
import com.focuslauncher.TestDispatchers
import com.focuslauncher.core.home.HomeRoleController
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.LaunchResult
import com.focuslauncher.domain.repository.AdminRepository
import com.focuslauncher.domain.repository.AppCatalogRepository
import com.focuslauncher.domain.repository.OnboardingRepository
import com.focuslauncher.domain.usecase.FakeSettings
import com.focuslauncher.domain.model.LauncherSettings
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.model.VisibilityPolicy
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

    private class FakeAdmin : AdminRepository {
        var locks = 0
        override val isAuthorized = kotlinx.coroutines.flow.MutableStateFlow(false)
        override fun lock() { locks++ }
        override suspend fun verifyPin(pin: CharArray) = com.focuslauncher.domain.model.AuthenticationResult.NotRequired
        override suspend fun cooldownRemainingMs() = 0L
        override suspend fun setPackageHidden(key: com.focuslauncher.domain.model.AppPackageKey, hidden: Boolean) = com.focuslauncher.domain.model.MutationResult.Success
        override suspend fun enablePin(pin: CharArray, confirmation: CharArray) = com.focuslauncher.domain.model.MutationResult.Success
        override suspend fun changePin(pin: CharArray, confirmation: CharArray) = com.focuslauncher.domain.model.MutationResult.Success
        override suspend fun disablePin() = com.focuslauncher.domain.model.MutationResult.Success
        override suspend fun resetConfiguration() = com.focuslauncher.domain.model.MutationResult.Success
    }

    private class FakeOnboarding(var result: MutationResult = MutationResult.Success) : OnboardingRepository {
        var calls = 0
        override suspend fun acceptOnboarding(): MutationResult { calls++; return result }
    }

    private fun settings(accepted: Boolean) =
        SettingsReadState.Ready(LauncherSettings(accepted, VisibilityPolicy(emptySet()), pinEnabled = false))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(controller: FakeRoleController) =
        MainViewModel(controller, FakeCatalog(), FakeAdmin(), FakeSettings(settings(true)), FakeOnboarding(), TestDispatchers(dispatcher))

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
            assertEquals(MainEvent.OpenSystemSettings(SystemSettingsTarget.HomeSettings), awaitItem())
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

    @Test
    fun leavingAdministrativeAreaRevokesTheSession() = runTest(dispatcher) {
        val admin = FakeAdmin()
        val vm = MainViewModel(FakeRoleController(HomeRoleStatus.Held), FakeCatalog(), admin, FakeSettings(settings(true)), FakeOnboarding(), TestDispatchers(dispatcher))
        vm.navigateTo(Screen.Settings)
        vm.navigateTo(Screen.Security) // dentro de administración: no revoca
        assertEquals(0, admin.locks)
        vm.navigateTo(Screen.Home)
        assertEquals(1, admin.locks)
    }

    @Test
    fun homeIntentAndStopRevokeAndLeaveNoAdministrativeScreen() = runTest(dispatcher) {
        val admin = FakeAdmin()
        val vm = MainViewModel(FakeRoleController(HomeRoleStatus.Held), FakeCatalog(), admin, FakeSettings(settings(true)), FakeOnboarding(), TestDispatchers(dispatcher))
        vm.navigateTo(Screen.Settings)
        vm.onHomeIntent()
        assertEquals(Screen.Home, vm.state.value.screen)
        vm.navigateTo(Screen.Settings)
        vm.onActivityStopped()
        assertEquals(Screen.Home, vm.state.value.screen)
        assertEquals(true, admin.locks >= 2)
    }

    // ------------------------------------------------------------------ T08: aviso de alcance

    private fun vm(settings: FakeSettings, onboarding: FakeOnboarding = FakeOnboarding(), role: FakeRoleController = FakeRoleController(HomeRoleStatus.NotHeld, Intent("test.REQUEST_ROLE"))) =
        MainViewModel(role, FakeCatalog(), FakeAdmin(), settings, onboarding, TestDispatchers(dispatcher))

    @Test
    fun welcomeIsNeededOnlyWhenTheConfigurationIsReadableAndNotAccepted() = runTest(dispatcher) {
        val settings = FakeSettings(SettingsReadState.Loading)
        val vm = vm(settings)
        vm.needsWelcome.test {
            assertEquals(false, awaitItem()) // cargando: ni Bienvenida ni apps
            settings.state.value = settings(accepted = false); assertEquals(true, awaitItem())
            settings.state.value = settings(accepted = true); assertEquals(false, awaitItem())
            settings.state.value = settings(accepted = false); assertEquals(true, awaitItem()) // p. ej. tras restablecer
            settings.state.value = SettingsReadState.ReadError(com.focuslauncher.domain.model.ReadErrorKind.StorageUnavailable)
            assertEquals(false, awaitItem()) // error: no se inventa una Bienvenida
        }
    }

    @Test
    fun acceptingPersistsThenGoesHomeAndTheRoleRequestIsOptional() = runTest(dispatcher) {
        val onboarding = FakeOnboarding()
        val vm = vm(FakeSettings(settings(false)), onboarding)
        vm.navigateTo(Screen.Welcome)
        vm.events.test {
            vm.onAcceptWelcome(requestHome = false); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, onboarding.calls)
            assertEquals(Screen.Home, vm.state.value.screen)
            expectNoEvents() // «probar sin cambiar mi inicio» no pide el rol
        }
    }

    @Test
    fun acceptingWithHomeAsksForTheRoleOnlyAfterSaving() = runTest(dispatcher) {
        val intent = Intent("test.REQUEST_ROLE")
        val vm = vm(FakeSettings(settings(false)), role = FakeRoleController(HomeRoleStatus.NotHeld, intent))
        vm.events.test {
            vm.onAcceptWelcome(requestHome = true); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(MainEvent.LaunchRoleRequest(intent), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun aFailedSaveKeepsTheWelcomeShowsAnErrorAndNeverRequestsTheRole() = runTest(dispatcher) {
        val onboarding = FakeOnboarding(MutationResult.StorageFailure)
        val vm = vm(FakeSettings(settings(false)), onboarding)
        vm.navigateTo(Screen.Welcome)
        vm.events.test {
            vm.onAcceptWelcome(requestHome = true); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, vm.state.value.welcomeError)
            assertEquals(false, vm.state.value.welcomeSaving)
            assertEquals(Screen.Welcome, vm.state.value.screen)
            expectNoEvents()
        }
        onboarding.result = MutationResult.Success // reintento
        vm.onAcceptWelcome(requestHome = false); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, vm.state.value.welcomeError); assertEquals(Screen.Home, vm.state.value.screen)
    }

    @Test
    fun systemSettingsExitsAreEventsAndAnUnavailableOneLeavesManualInstructions() = runTest(dispatcher) {
        val vm = vm(FakeSettings(settings(true)))
        vm.events.test {
            SystemSettingsTarget.entries.forEach { target ->
                vm.onOpenSystemSettings(target); dispatcher.scheduler.advanceUntilIdle()
                assertEquals(MainEvent.OpenSystemSettings(target), awaitItem())
            }
        }
        vm.onSystemSettingsUnavailable(SystemSettingsTarget.AppDetails)
        assertEquals(SystemSettingsTarget.AppDetails, vm.state.value.settingsUnavailable)
        vm.onSystemSettingsOpened()
        assertEquals(null, vm.state.value.settingsUnavailable)
    }
}
