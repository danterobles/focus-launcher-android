package com.focuslauncher.presentation.settings

import app.cash.turbine.test
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.usecase.FakeAdmin
import com.focuslauncher.domain.usecase.FakeCatalog
import com.focuslauncher.domain.usecase.FakeSettings
import com.focuslauncher.domain.usecase.ObserveManageablePackages
import com.focuslauncher.domain.usecase.OwnPackage
import com.focuslauncher.domain.usecase.app
import com.focuslauncher.domain.usecase.key
import com.focuslauncher.domain.usecase.readySettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val catalog = FakeCatalog(CatalogState.Ready(listOf(app("a.app", label = "Alfa"), app("b.app", label = "Beta"))))
    private val settings = FakeSettings(readySettings())
    private val admin = FakeAdmin(settings)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SettingsViewModel(ObserveManageablePackages(catalog, settings, OwnPackage("com.focuslauncher")), admin)
    private fun SettingsUiState.ready() = content as EditorContent.Ready
    private fun SettingsUiState.hidden(pkg: String) = ready().packages.first { it.key.packageName == pkg }.hidden

    @Test fun savesEveryChangeImmediatelyThroughTheCoordinator() = runTest(dispatcher) {
        val vm = vm()
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            vm.onSetVisible(key("a.app"), visible = false)
            advanceUntilIdle()
            assertEquals(listOf(key("a.app") to true), admin.hideCalls)
            assertTrue(expectMostRecentItem().hidden("a.app"))
            vm.onSetVisible(key("a.app"), visible = true)
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().hidden("a.app"))
        }
    }

    @Test fun theDisplayedValueOnlyChangesAfterTheSaveIsConfirmed() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        admin.hideDelay = { gate.await() }
        val vm = vm()
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            vm.onSetVisible(key("a.app"), visible = false)
            advanceUntilIdle()
            val pending = expectMostRecentItem()
            assertFalse("no hay cambio optimista", pending.hidden("a.app"))
            assertTrue(key("a.app") in pending.ready().saving)
            assertEquals(SaveIndicator.Saving, pending.indicator)
            gate.complete(Unit); runCurrent() // sin adelantar el reloj: el «Guardado» aún es visible
            val done = expectMostRecentItem()
            assertTrue(done.hidden("a.app")); assertEquals(SaveIndicator.Saved, done.indicator)
            advanceTimeBy(3_000); advanceUntilIdle()
            assertEquals(SaveIndicator.Idle, expectMostRecentItem().indicator)
        }
    }

    @Test fun aFailedSaveNeverLooksLikeSuccessAndKeepsTheOldValue() = runTest(dispatcher) {
        admin.hideResult = MutationResult.StorageFailure
        val vm = vm()
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            vm.onSetVisible(key("a.app"), visible = false)
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertFalse(s.hidden("a.app"))
            assertEquals(SaveIndicator.Failed(SaveFailure.StorageFailure), s.indicator)
            assertEquals(SaveFailure.StorageFailure, s.ready().failed[key("a.app")])
        }
    }

    @Test fun expiredSessionIsReportedAndNothingChanges() = runTest(dispatcher) {
        admin.hideResult = MutationResult.AuthenticationRequired
        val vm = vm()
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            vm.onSetVisible(key("b.app"), visible = false); advanceUntilIdle()
            assertEquals(SaveIndicator.Failed(SaveFailure.SessionExpired), expectMostRecentItem().indicator)
        }
    }

    @Test fun aPackageBeingSavedIgnoresFurtherTaps() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>(); admin.hideDelay = { gate.await() }
        val vm = vm()
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            vm.onSetVisible(key("a.app"), false); vm.onSetVisible(key("a.app"), false); advanceUntilIdle()
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(1, admin.hideCalls.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun withAPinAndNoSessionTheEditorIsLockedAndHasNoLabels() = runTest(dispatcher) {
        settings.state.value = readySettings("b.app", pin = true)
        val vm = vm()
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            assertEquals(EditorContent.Locked, expectMostRecentItem().content)
            admin.isAuthorized.value = true; advanceUntilIdle()
            assertTrue(expectMostRecentItem().content is EditorContent.Ready)
            admin.lock(); advanceUntilIdle() // la sesión se revoca/caduca
            assertEquals(EditorContent.Locked, expectMostRecentItem().content)
        }
    }

    @Test fun withoutAPinTheEditorIsOpenDirectly() = runTest(dispatcher) {
        vm().state.test { skipItems(1); advanceUntilIdle(); assertTrue(expectMostRecentItem().content is EditorContent.Ready) }
    }

    @Test fun nothingIsRetainedWhenNobodyObservesAnymore() = runTest(dispatcher) {
        settings.state.value = readySettings("b.app", pin = true)
        admin.isAuthorized.value = true
        val vm = vm()
        vm.state.test { skipItems(1); advanceUntilIdle(); assertTrue(expectMostRecentItem().content is EditorContent.Ready); cancelAndIgnoreRemainingEvents() }
        advanceUntilIdle()
        assertEquals(EditorContent.Loading, vm.state.value.content) // el último valor (con etiquetas) se descartó
    }

    @Test fun clearForgetsTransientState() = runTest(dispatcher) {
        admin.hideResult = MutationResult.StorageFailure
        val vm = vm()
        vm.state.test {
            skipItems(1); advanceUntilIdle()
            vm.onSetVisible(key("a.app"), false); advanceUntilIdle()
            vm.clear(); advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(SaveIndicator.Idle, s.indicator); assertTrue(s.ready().failed.isEmpty())
        }
    }
}
