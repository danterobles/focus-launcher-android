package com.focuslauncher.presentation.admin

import app.cash.turbine.test
import com.focuslauncher.core.di.MonotonicClock
import com.focuslauncher.domain.model.AuthenticationResult
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.domain.model.SettingsReadState
import com.focuslauncher.domain.usecase.FakeAdmin
import com.focuslauncher.domain.usecase.FakeSettings
import com.focuslauncher.domain.usecase.readySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettings(readySettings(pin = true))
    private val admin = FakeAdmin(settings)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm() = AdminGateViewModel(settings, admin, MonotonicClock { testScheduler.currentTime + 10_000 })
    private fun pin() = "123456".toCharArray()
    private fun AdminGate.needs() = this as AdminGate.NeedsPin

    @Test fun withoutAPinTheGateIsOpen() = runTest(dispatcher) {
        settings.state.value = readySettings(pin = false)
        vm().gate.test { skipItems(1); advanceUntilIdle(); assertEquals(AdminGate.Open, expectMostRecentItem()) }
    }

    @Test fun withAPinAndNoSessionItAsksForIt() = runTest(dispatcher) {
        vm().gate.test { skipItems(1); advanceUntilIdle(); assertTrue(expectMostRecentItem() is AdminGate.NeedsPin) }
    }

    @Test fun aCorrectPinOpensTheGateAndLockingClosesItAgain() = runTest(dispatcher) {
        admin.verifyResults += AuthenticationResult.Success
        val vm = vm()
        vm.gate.test {
            skipItems(1); advanceUntilIdle()
            vm.submit(pin()); advanceUntilIdle()
            assertEquals(AdminGate.Open, expectMostRecentItem())
            admin.lock(); advanceUntilIdle()
            assertTrue(expectMostRecentItem() is AdminGate.NeedsPin)
        }
    }

    @Test fun aWrongPinShowsRemainingAttemptsAndNeverOpens() = runTest(dispatcher) {
        admin.verifyResults += AuthenticationResult.IncorrectPin(3)
        val vm = vm()
        vm.gate.test {
            skipItems(1); advanceUntilIdle()
            vm.submit(pin()); advanceUntilIdle()
            val g = expectMostRecentItem().needs()
            assertEquals(PinFeedback(PinFeedbackKind.Incorrect, 3), g.feedback)
            assertTrue(!g.busy)
        }
    }

    @Test fun cooldownCountsDownEverySecondAndThenAllowsAnotherTry() = runTest(dispatcher) {
        admin.verifyResults += AuthenticationResult.Cooldown(3_000)
        val vm = vm()
        vm.gate.test {
            skipItems(1); advanceUntilIdle()
            vm.submit(pin()); runCurrent()
            assertEquals(3L, expectMostRecentItem().needs().cooldownSeconds)
            advanceTimeBy(1_001); runCurrent()
            assertEquals(2L, expectMostRecentItem().needs().cooldownSeconds)
            advanceTimeBy(2_100); runCurrent()
            val done = expectMostRecentItem().needs()
            assertEquals(0L, done.cooldownSeconds); assertNull(done.feedback) // el aviso de espera desaparece
        }
    }

    @Test fun anActiveCooldownIsRecoveredWhenTheGateIsShown() = runTest(dispatcher) {
        admin.cooldownMs = 30_000 // p. ej. tras reiniciar el proceso: la espera se reaplica completa
        val vm = vm()
        vm.gate.test {
            skipItems(1); advanceUntilIdle()
            vm.onGateShown(); runCurrent()
            val g = expectMostRecentItem().needs()
            assertEquals(30L, g.cooldownSeconds); assertEquals(PinFeedbackKind.Cooldown, g.feedback?.kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun theSlowIndicatorAppearsOnlyAfter300ms() = runTest(dispatcher) {
        admin.verifyDelay = { delay(1_000) }
        admin.verifyResults += AuthenticationResult.IncorrectPin(4)
        val vm = vm()
        vm.gate.test {
            skipItems(1); advanceUntilIdle()
            vm.submit(pin()); advanceTimeBy(100); runCurrent()
            val early = expectMostRecentItem().needs(); assertTrue(early.busy); assertTrue(!early.slow)
            advanceTimeBy(250); runCurrent()
            assertTrue(expectMostRecentItem().needs().slow)
            advanceUntilIdle()
            val end = expectMostRecentItem().needs(); assertTrue(!end.busy && !end.slow)
        }
    }

    @Test fun aSecondSubmitWhileBusyIsIgnored() = runTest(dispatcher) {
        admin.verifyDelay = { delay(1_000) }
        val vm = vm()
        vm.gate.test {
            skipItems(1); advanceUntilIdle()
            vm.submit(pin()); runCurrent(); vm.submit(pin()); advanceUntilIdle()
            assertEquals(1, admin.verifyCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun invalidFormatIsNeverSentToTheCoordinatorAndIsWiped() = runTest(dispatcher) {
        val vm = vm(); val short = "123".toCharArray()
        vm.gate.test { skipItems(1); advanceUntilIdle(); vm.submit(short); advanceUntilIdle(); assertEquals(0, admin.verifyCalls); assertTrue(short.all { it == '\u0000' }); cancelAndIgnoreRemainingEvents() }
    }

    @Test fun lateOrEnvironmentFailuresShowAMessageWithoutOpening() = runTest(dispatcher) {
        admin.verifyResults += AuthenticationResult.Unavailable
        val vm = vm()
        vm.gate.test {
            skipItems(1); advanceUntilIdle(); vm.submit(pin()); advanceUntilIdle()
            val g = expectMostRecentItem().needs()
            assertEquals(PinFeedbackKind.CouldNotVerify, g.feedback?.kind)
        }
    }

    @Test fun unreadableConfigurationBlocksInsteadOfOpening() = runTest(dispatcher) {
        settings.state.value = SettingsReadState.ReadError(ReadErrorKind.KeystoreUnavailable)
        vm().gate.test { skipItems(1); advanceUntilIdle(); assertTrue(expectMostRecentItem() is AdminGate.Blocked) }
        settings.state.value = SettingsReadState.RecoveryRequired(RecoveryReason.KeyLost)
        vm().gate.test { skipItems(1); advanceUntilIdle(); assertTrue((expectMostRecentItem() as AdminGate.Blocked).recovery == RecoveryReason.KeyLost) }
    }
}
