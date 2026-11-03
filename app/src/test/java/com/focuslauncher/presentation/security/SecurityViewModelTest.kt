package com.focuslauncher.presentation.security

import app.cash.turbine.test
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.usecase.FakeAdmin
import com.focuslauncher.domain.usecase.FakeSettings
import com.focuslauncher.domain.usecase.readySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettings(readySettings(pin = false))
    private val admin = FakeAdmin(settings)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SecurityViewModel(settings, admin)
    private fun pin() = "123456".toCharArray()

    @Test fun reflectsThePersistedPinState() = runTest(dispatcher) {
        vm().state.test {
            skipItems(1); advanceUntilIdle()
            assertEquals(false, expectMostRecentItem().pinEnabled)
            settings.state.value = readySettings(pin = true); advanceUntilIdle()
            assertEquals(true, expectMostRecentItem().pinEnabled)
        }
    }

    @Test fun successfulOperationsEmitTheirOutcomeOnce() = runTest(dispatcher) {
        val vm = vm()
        vm.outcomes.test {
            vm.enablePin(pin(), pin()); advanceUntilIdle(); assertEquals(SecurityOutcome.PinEnabled, awaitItem())
            vm.changePin(pin(), pin()); advanceUntilIdle(); assertEquals(SecurityOutcome.PinChanged, awaitItem())
            vm.disablePin(); advanceUntilIdle(); assertEquals(SecurityOutcome.PinDisabled, awaitItem())
            vm.resetConfiguration(); advanceUntilIdle(); assertEquals(SecurityOutcome.ConfigurationReset, awaitItem())
            expectNoEvents()
        }
        assertEquals(listOf("enable", "change", "disable", "reset"), admin.pinCalls)
    }

    @Test fun failuresAreShownAndNeverAnnouncedAsSuccess() = runTest(dispatcher) {
        val vm = vm()
        val expected = mapOf(
            MutationResult.InvalidInput("x") to SecurityError.InvalidPin,
            MutationResult.AuthenticationRequired to SecurityError.SessionExpired,
            MutationResult.Unavailable to SecurityError.Unavailable,
            MutationResult.StorageFailure to SecurityError.StorageFailure,
            MutationResult.RecoveryRequired to SecurityError.RecoveryRequired,
        )
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            for ((result, error) in expected) {
                admin.pinResult = result
                vm.enablePin(pin(), pin()); advanceUntilIdle()
                assertEquals(error, expectMostRecentItem().error)
            }
            cancelAndIgnoreRemainingEvents()
        }
        vm.outcomes.test { expectNoEvents() }
    }

    @Test fun theBusyIndicatorAppearsAfter300msAndDoubleSubmitIsIgnored() = runTest(dispatcher) {
        admin.hideDelay = {}
        val slow = object : com.focuslauncher.domain.repository.AdminRepository by admin {
            override suspend fun enablePin(pin: CharArray, confirmation: CharArray): MutationResult { delay(1_000); return MutationResult.Success }
        }
        val vm = SecurityViewModel(settings, slow)
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            vm.enablePin(pin(), pin()); advanceTimeBy(100); runCurrent()
            assertTrue(expectMostRecentItem().busy.also { }); 
            advanceTimeBy(300); runCurrent()
            val s = expectMostRecentItem(); assertTrue(s.busy && s.slow)
            vm.enablePin(pin(), pin()) // ignorado
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().busy)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun dismissingAndClearingRemoveErrors() = runTest(dispatcher) {
        admin.pinResult = MutationResult.StorageFailure
        val vm = vm()
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            vm.enablePin(pin(), pin()); advanceUntilIdle(); assertEquals(SecurityError.StorageFailure, expectMostRecentItem().error)
            vm.dismissError(); advanceUntilIdle(); assertEquals(null, expectMostRecentItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
