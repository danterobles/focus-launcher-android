package com.focuslauncher.data.repository

import com.focuslauncher.data.local.StateValidator
import com.focuslauncher.data.local.proto.PinConfigProto
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.AuthenticationResult
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.model.UserSerial
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdminAccessCoordinatorTest {
    @get:Rule val tmp = TemporaryFolder()
    private var harness: AdminHarness? = null
    private val h: AdminHarness get() = harness ?: AdminHarness(tmp.newFolder()).also { harness = it }

    @After fun tearDown() { harness?.close() }

    private val pin = "123456"
    private val key = AppPackageKey("a.app", UserSerial(0))

    private fun AdminHarness.enabledAndUnlocked() {
        assertEquals(MutationResult.Success, enablePin(pin))
        assertEquals(AuthenticationResult.Success, verify(pin))
    }
    private fun AdminHarness.hide(k: AppPackageKey = key) = runBlocking { admin.setPackageHidden(k, true) }
    private fun AdminHarness.wrong(n: Int) = repeat(n) { verify("999999") }

    // ---------------------------------------------------------------- creación y política del PIN

    @Test fun pinLengthBoundaries() {
        listOf("12345", "1234567890123").forEach { bad ->
            AdminHarness(tmp.newFolder()).also { it.enablePin(bad).let { r -> assertTrue("$bad", r is MutationResult.InvalidInput) }; assertFalse(it.state().pin.hasEnabled()); it.close() }
        }
        listOf("123456", "123456789012").forEach { good ->
            AdminHarness(tmp.newFolder()).also { assertEquals(good, MutationResult.Success, it.enablePin(good)); it.close() }
        }
    }

    @Test fun leadingZerosAreKeptAndVerifiable() {
        h.enablePin("000123"); assertEquals(AuthenticationResult.Success, h.verify("000123"))
        assertEquals(AuthenticationResult.IncorrectPin(4), h.verify("123000"))
    }

    @Test fun nonAsciiDigitsAndOtherCharactersAreRejected() {
        listOf("٠١٢٣٤٥٦", "１２３４５６", "12345a", "123 456", "12-3456", "١٢٣٤٥٦").forEach {
            assertTrue(it, h.enablePin(it) is MutationResult.InvalidInput)
        }
        assertFalse(h.state().pin.hasEnabled())
    }

    @Test fun confirmationMustMatch() {
        val r = runBlocking { h.admin.enablePin("123456".toCharArray(), "123457".toCharArray()) }
        assertTrue(r is MutationResult.InvalidInput)
        assertFalse(h.state().pin.hasEnabled())
    }

    @Test fun enablingCreatesAVerifierWithTheMandatedParametersAndLocksAdministration() {
        assertEquals(MutationResult.Success, h.enablePin(pin))
        val v = h.state().pin.enabled
        assertEquals("PBKDF2WithHmacSHA256", v.kdf); assertEquals(600_000, v.iterations)
        assertEquals(16, v.salt.size()); assertEquals(32, v.verifier.size())
        assertFalse(h.admin.isAuthorized.value) // queda bloqueado: el siguiente ingreso pide el PIN
        assertEquals(MutationResult.AuthenticationRequired, h.hide())
    }

    @Test fun everyCredentialChangeUsesANewSalt() {
        h.enabledAndUnlocked()
        val salt1 = h.state().pin.enabled.salt.toByteArray()
        assertEquals(MutationResult.Success, runBlocking { h.admin.changePin(pin.toCharArray(), pin.toCharArray()) })
        val salt2 = h.state().pin.enabled.salt.toByteArray()
        assertFalse(salt1.contentEquals(salt2))
    }

    @Test fun enablingWhenAlreadyEnabledIsRejected() {
        h.enablePin(pin)
        assertTrue(h.enablePin("654321") is MutationResult.InvalidInput)
    }

    @Test fun thePinArrayIsWipedAfterUse() {
        h.enablePin(pin)
        val secret = "123456".toCharArray()
        runBlocking { h.admin.verifyPin(secret) }
        assertTrue(secret.all { it == '\u0000' })
    }

    // ---------------------------------------------------------------- verificación e intentos

    @Test fun correctPinOpensASessionAndWrongPinNeverDoes() {
        h.enablePin(pin)
        assertEquals(AuthenticationResult.IncorrectPin(4), h.verify("111111"))
        assertFalse(h.admin.isAuthorized.value)
        assertEquals(AuthenticationResult.Success, h.verify(pin))
        assertTrue(h.admin.isAuthorized.value)
    }

    @Test fun invalidFormatDoesNotConsumeAnAttemptNorRunTheKdf() {
        h.enablePin(pin); val before = h.hasher.calls.get()
        assertEquals(AuthenticationResult.InvalidInput, h.verify("1234"))
        assertEquals(before, h.hasher.calls.get())
        assertEquals(0, h.state().attempts.reservedInWindow)
    }

    @Test fun withoutAPinThereIsNothingToVerify() {
        assertEquals(AuthenticationResult.NotRequired, h.verify(pin))
    }

    @Test fun fourWrongThenCorrectResetsTheWindow() {
        h.enablePin(pin); h.wrong(4)
        assertEquals(4, h.state().attempts.reservedInWindow)
        assertEquals(AuthenticationResult.Success, h.verify(pin))
        assertEquals(0, h.state().attempts.reservedInWindow)
        h.admin.lock(); h.wrong(4)
        assertFalse(h.state().attempts.cooldownActive) // empezó una ventana nueva
    }

    @Test fun fifthReservationStartsA30sCooldownAndLaterAttemptsRunNoKdf() {
        h.enablePin(pin); h.wrong(4)
        assertEquals(AuthenticationResult.Cooldown(30_000), h.verify("999999"))
        val calls = h.hasher.calls.get()
        h.clock.now += 10_000
        assertEquals(AuthenticationResult.Cooldown(20_000), h.verify(pin)) // ni siquiera el PIN correcto
        assertEquals(calls, h.hasher.calls.get())
        assertEquals(20_000, runBlocking { h.admin.cooldownRemainingMs() })
        h.clock.now += 20_000
        assertEquals(AuthenticationResult.Success, h.verify(pin))
    }

    @Test fun cooldownEscalatesTo900sAndStaysThere() {
        h.enablePin(pin)
        val expected = listOf(30, 60, 120, 240, 480, 900, 900, 900)
        expected.forEach { secs ->
            h.wrong(4)
            assertEquals(AuthenticationResult.Cooldown(secs * 1_000L), h.verify("999999"))
            assertEquals(secs * 1_000L, h.state().attempts.cooldownDurationMs)
            h.clock.now += secs * 1_000L
        }
    }

    @Test fun successResetsTheLevel() {
        h.enablePin(pin)
        repeat(3) { h.wrong(4); h.verify("999999"); h.clock.now += 900_000 } // sube el nivel
        assertTrue(h.state().attempts.penaltyLevel >= 3)
        assertEquals(AuthenticationResult.Success, h.verify(pin))
        assertEquals(0, h.state().attempts.penaltyLevel)
        h.admin.lock(); h.wrong(4)
        assertEquals(AuthenticationResult.Cooldown(30_000), h.verify("999999"))
    }

    @Test fun correctPinOnTheFifthAttemptSucceedsAndResetsEverything() {
        h.enablePin(pin); h.wrong(4)
        assertEquals(AuthenticationResult.Success, h.verify(pin))
        val a = h.state().attempts
        assertEquals(0, a.reservedInWindow); assertEquals(0, a.penaltyLevel); assertFalse(a.cooldownActive)
        assertEquals(0L, a.cooldownDurationMs)
    }

    @Test fun cancellingAfterTheReservationDoesNotGiveTheAttemptBack() = runBlocking {
        h.enablePin(pin)
        h.hasher.gate = CompletableDeferred(); h.hasher.entered = CompletableDeferred()
        val job = async(Dispatchers.IO) { h.admin.verifyPin("999999".toCharArray()) }
        withTimeout(5_000) { h.hasher.entered!!.await() }
        job.cancelAndJoin()
        assertEquals(1, h.state().attempts.reservedInWindow)
        assertFalse(h.admin.isAuthorized.value)
    }

    @Test fun processDeathAfterAReservationKeepsItAndTheNextOneCanBeTheFifth() = runBlocking {
        h.enablePin(pin); h.wrong(3)
        h.hasher.gate = CompletableDeferred(); h.hasher.entered = CompletableDeferred()
        val job = async(Dispatchers.IO) { h.admin.verifyPin("999999".toCharArray()) }
        withTimeout(5_000) { h.hasher.entered!!.await() }
        job.cancelAndJoin() // el proceso muere con la 4.ª reserva ya guardada
        h.restart(); h.hasher.gate = null
        assertEquals(4, h.state().attempts.reservedInWindow)
        assertEquals(AuthenticationResult.Cooldown(30_000), h.verify("999999")) // la 5.ª
    }

    @Test fun failedReservationWriteBlocksTheAttemptWithoutRunningTheKdf() {
        h.enablePin(pin); val calls = h.hasher.calls.get()
        h.store.failWhen = { true }
        assertEquals(AuthenticationResult.StorageFailure, h.verify(pin))
        assertEquals(calls, h.hasher.calls.get())
        assertFalse(h.admin.isAuthorized.value)
    }

    @Test fun failedSuccessCommitNeverOpensTheSession() {
        h.enablePin(pin)
        val base = h.store.updates.get()
        h.store.failWhen = { it == base + 2 } // 1.ª = reserva (ok), 2.ª = commit de éxito (falla)
        assertEquals(AuthenticationResult.StorageFailure, h.verify(pin))
        assertFalse(h.admin.isAuthorized.value)
        assertEquals(1, h.state().attempts.reservedInWindow) // la reserva se conserva
    }

    @Test fun restartReappliesTheFullCooldownAndNeverShortensIt() {
        h.enablePin(pin); h.wrong(4); h.verify("999999") // espera de 30 s activa y persistida
        h.clock.now += 29_000 // casi vencida…
        h.restart(newClockStart = 777) // …pero el proceso muere
        assertEquals(30_000, runBlocking { h.admin.cooldownRemainingMs() }) // vuelve a empezar completa
        h.clock.now += 29_999
        assertEquals(AuthenticationResult.Cooldown(1), h.verify(pin))
        h.clock.now += 1
        assertEquals(AuthenticationResult.Success, h.verify(pin))
    }

    @Test fun restartingAgainDuringTheCooldownRestartsItAgain() {
        h.enablePin(pin); h.wrong(4); h.verify("999999")
        h.restart(); h.clock.now += 20_000
        h.restart(newClockStart = 42)
        assertEquals(30_000, runBlocking { h.admin.cooldownRemainingMs() })
    }

    @Test fun expiryPersistsResetKeepingTheLevelBeforeTheNextReservation() {
        h.enablePin(pin); h.wrong(4); h.verify("999999"); h.clock.now += 30_000
        h.hasher.gate = CompletableDeferred(); h.hasher.entered = CompletableDeferred()
        runBlocking {
            val job = async(Dispatchers.IO) { h.admin.verifyPin("999999".toCharArray()) }
            withTimeout(5_000) { h.hasher.entered!!.await() }
            val a = h.state().attempts
            assertFalse(a.cooldownActive); assertEquals(1, a.reservedInWindow); assertEquals(1, a.penaltyLevel)
            job.cancelAndJoin()
        }
    }

    @Test fun verificationsAreSerializedNeverTwoKdfsAtOnce() = runBlocking {
        h.enablePin(pin)
        h.hasher.gate = CompletableDeferred(); h.hasher.entered = CompletableDeferred()
        val a = async(Dispatchers.IO) { h.admin.verifyPin("111111".toCharArray()) }
        withTimeout(5_000) { h.hasher.entered!!.await() }
        val b = async(Dispatchers.IO) { h.admin.verifyPin("222222".toCharArray()) }
        Thread.sleep(150)
        h.hasher.gate!!.complete(Unit)
        a.await(); b.await()
        assertEquals(1, h.hasher.maxConcurrent)
    }

    @Test fun sourcesNeverUseWallClockTime() {
        val banned = listOf("currentTimeMillis", "Instant.now", "LocalDateTime", "System.nanoTime", "Date()", "Calendar.getInstance")
        val offenders = java.io.File("src/main/java").walkTopDown().filter { it.extension == "kt" }
            .filter { f -> banned.any { f.readText().contains(it) } }.map { it.name }.toList()
        assertTrue("usan hora de pared: $offenders", offenders.isEmpty())
    }

    // ---------------------------------------------------------------- sesión

    @Test fun sessionLasts120sOnTheMonotonicClockAndRotationCannotRenewIt() {
        h.enabledAndUnlocked()
        h.clock.now += 119_999
        assertTrue(h.sessions.isActive())
        assertEquals(MutationResult.Success, h.hide())
        h.clock.now += 1 // 120 s exactos desde el desbloqueo; ninguna operación lo renovó
        assertFalse(h.sessions.isActive())
        assertEquals(MutationResult.AuthenticationRequired, h.hide(AppPackageKey("b.app", UserSerial(0))))
    }

    @Test fun lockRevokesImmediately() {
        h.enabledAndUnlocked(); h.admin.lock()
        assertFalse(h.admin.isAuthorized.value)
        assertEquals(MutationResult.AuthenticationRequired, h.hide())
    }

    @Test fun aNewProcessNeverHasASessionEvenWithAPinEnabled() {
        h.enabledAndUnlocked(); h.restart()
        assertFalse(h.admin.isAuthorized.value)
        assertEquals(MutationResult.AuthenticationRequired, h.hide())
        assertTrue(h.state().hiddenPackagesList.isEmpty())
    }

    @Test fun screenOffOrLockedDeviceBlocksNewAndExistingSessions() {
        h.enablePin(pin)
        h.env.interactive = false
        assertEquals(AuthenticationResult.Unavailable, h.verify(pin))
        h.env.interactive = true; h.env.locked = true
        assertEquals(AuthenticationResult.Unavailable, h.verify(pin))
        h.env.locked = false
        assertEquals(AuthenticationResult.Success, h.verify(pin))
        h.env.interactive = false
        assertFalse(h.sessions.isActive())
        assertEquals(MutationResult.AuthenticationRequired, h.hide())
    }

    @Test fun aLateKdfAfterLeavingTheScreenNeverEmitsASession() = runBlocking {
        h.enablePin(pin)
        h.hasher.gate = CompletableDeferred(); h.hasher.entered = CompletableDeferred()
        val job = async(Dispatchers.IO) { h.admin.verifyPin(pin.toCharArray()) } // PIN correcto
        withTimeout(5_000) { h.hasher.entered!!.await() }
        h.admin.lock() // salir de la pantalla / recibir Home / bloquear: NO espera al Mutex de la KDF
        h.hasher.gate!!.complete(Unit)
        assertEquals(AuthenticationResult.Unavailable, job.await())
        assertFalse(h.admin.isAuthorized.value)
        assertEquals(MutationResult.AuthenticationRequired, h.hide())
        assertEquals(1, h.state().attempts.reservedInWindow) // el intento sí quedó contabilizado
    }

    @Test fun revocationDoesNotWaitForTheMutexHeldByAKdf() = runBlocking {
        h.enabledAndUnlocked()
        h.hasher.gate = CompletableDeferred(); h.hasher.entered = CompletableDeferred()
        val change = async(Dispatchers.IO) { h.admin.changePin("654321".toCharArray(), "654321".toCharArray()) }
        withTimeout(5_000) { h.hasher.entered!!.await() }
        val t0 = System.nanoTime(); h.admin.lock(); val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("lock tardó $ms ms", ms < 500)
        h.hasher.gate!!.complete(Unit)
        assertEquals(MutationResult.AuthenticationRequired, change.await()) // revalidada antes de confirmar
        assertEquals(AuthenticationResult.Success, h.verify(pin)) // el PIN anterior sigue vigente
    }

    // ---------------------------------------------------------------- mutaciones

    @Test fun withoutAPinChangesAreAllowedDirectly() {
        assertEquals(MutationResult.Success, h.hide())
        assertEquals(1, h.state().hiddenPackagesCount)
        assertEquals(MutationResult.Success, runBlocking { h.admin.setPackageHidden(key, false) })
        assertEquals(0, h.state().hiddenPackagesCount)
    }

    @Test fun withAPinEveryAdministrativeMutationNeedsAFreshSession() {
        h.enabledAndUnlocked(); h.admin.lock()
        assertEquals(MutationResult.AuthenticationRequired, h.hide())
        assertEquals(MutationResult.AuthenticationRequired, runBlocking { h.admin.changePin("654321".toCharArray(), "654321".toCharArray()) })
        assertEquals(MutationResult.AuthenticationRequired, runBlocking { h.admin.disablePin() })
        assertEquals(MutationResult.AuthenticationRequired, runBlocking { h.admin.resetConfiguration() })
        assertTrue(h.state().pin.hasEnabled())
        assertEquals(0, h.state().hiddenPackagesCount)
    }

    @Test fun theOwnPackageCannotBeHidden() {
        val r = runBlocking { h.admin.setPackageHidden(AppPackageKey(h.own, UserSerial(0)), true) }
        assertTrue(r is MutationResult.InvalidInput)
    }

    @Test fun hidingIsPerPackageAndSerialAndIdempotent() {
        h.hide(); h.hide(); h.hide(AppPackageKey("a.app", UserSerial(3)))
        assertEquals(2, h.state().hiddenPackagesCount)
    }

    @Test fun changingThePinRequiresSessionRevokesItAndReplacesTheOldPin() {
        h.enabledAndUnlocked()
        assertEquals(MutationResult.Success, runBlocking { h.admin.changePin("654321".toCharArray(), "654321".toCharArray()) })
        assertFalse(h.admin.isAuthorized.value)
        assertTrue(h.verify(pin) is AuthenticationResult.IncorrectPin)
        assertEquals(AuthenticationResult.Success, h.verify("654321"))
    }

    @Test fun credentialChangeThatOutlivesTheSessionIsNotCommitted() = runBlocking {
        h.enabledAndUnlocked()
        val verifierBefore = h.state().pin.enabled.verifier.toByteArray()
        h.hasher.gate = CompletableDeferred(); h.hasher.entered = CompletableDeferred()
        val change = async(Dispatchers.IO) { h.admin.changePin("654321".toCharArray(), "654321".toCharArray()) }
        withTimeout(5_000) { h.hasher.entered!!.await() }
        h.clock.now += 121_000 // la sesión caduca durante la KDF
        h.hasher.gate!!.complete(Unit)
        assertEquals(MutationResult.AuthenticationRequired, change.await())
        assertArrayEquals(verifierBefore, h.state().pin.enabled.verifier.toByteArray())
    }

    @Test fun credentialChangeAndAConcurrentMutationAreSerializedAndTheLoserIsDenied() = runBlocking {
        h.enabledAndUnlocked()
        h.hasher.gate = CompletableDeferred(); h.hasher.entered = CompletableDeferred()
        val change = async(Dispatchers.IO) { h.admin.changePin("654321".toCharArray(), "654321".toCharArray()) }
        withTimeout(5_000) { h.hasher.entered!!.await() }
        val hide = async(Dispatchers.IO) { h.hide() } // espera al Mutex
        Thread.sleep(150)
        h.hasher.gate!!.complete(Unit)
        assertEquals(MutationResult.Success, change.await())
        assertEquals(MutationResult.AuthenticationRequired, hide.await()) // el cambio revocó la sesión
        assertEquals(0, h.state().hiddenPackagesCount)
    }

    @Test fun disablingThePinClearsAttemptsRevokesAndReopensSettings() {
        h.enabledAndUnlocked()
        assertEquals(MutationResult.Success, runBlocking { h.admin.disablePin() })
        val s = h.state()
        assertEquals(PinConfigProto.ModeCase.DISABLED, s.pin.modeCase)
        assertEquals(0, s.attempts.reservedInWindow)
        assertFalse(h.admin.isAuthorized.value)
        assertEquals(MutationResult.Success, h.hide()) // sin PIN, ajustes abiertos
    }

    @Test fun resetReturnsToTheInitialStateAndNeedsASessionWhenThereIsAPin() {
        h.hide(); h.enabledAndUnlocked(); h.hide(AppPackageKey("z.app", UserSerial(0)))
        h.admin.lock()
        assertEquals(MutationResult.AuthenticationRequired, runBlocking { h.admin.resetConfiguration() })
        assertEquals(AuthenticationResult.Success, h.verify(pin))
        assertEquals(MutationResult.Success, runBlocking { h.admin.resetConfiguration() })
        val s = h.state()
        assertEquals(com.focuslauncher.data.local.StateMapper.initialState(), s)
        assertFalse(h.admin.isAuthorized.value)
    }

    // ---------------------------------------------------------------- errores de almacenamiento

    @Test fun corruptStateNeverBecomesPinDisabledNorAllowsAnything() {
        h.enablePin(pin); h.close()
        h.file.writeBytes(h.file.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })
        h.restart()
        assertEquals(AuthenticationResult.RecoveryRequired, h.verify(pin))
        assertEquals(MutationResult.RecoveryRequired, h.hide())
        assertEquals(MutationResult.RecoveryRequired, runBlocking { h.admin.disablePin() })
        assertEquals(MutationResult.RecoveryRequired, runBlocking { h.admin.resetConfiguration() })
        assertFalse(h.admin.isAuthorized.value)
    }

    @Test fun storageFailureOnAMutationIsNeverReportedAsSuccess() {
        h.store.failWhen = { true }
        assertEquals(MutationResult.StorageFailure, h.hide())
    }

    @Test fun failedCredentialWriteKeepsTheOldPinWorking() {
        h.enabledAndUnlocked()
        h.store.failWhen = { true }
        assertEquals(MutationResult.StorageFailure, runBlocking { h.admin.changePin("654321".toCharArray(), "654321".toCharArray()) })
        h.store.failWhen = { false }
        assertEquals(AuthenticationResult.Success, h.verify(pin))
    }

    @Test fun differentSaltsGiveDifferentVerifiersForTheSamePin() {
        h.enabledAndUnlocked()
        val v1 = h.state().pin.enabled.verifier.toByteArray()
        runBlocking { h.admin.disablePin() }
        h.enablePin(pin)
        assertNotEquals(v1.toList(), h.state().pin.enabled.verifier.toByteArray().toList())
    }

    // ---------------------------------------------------------------- aviso de alcance (T08)

    @Test fun acceptingTheNoticePersistsAcrossARestartAndIsIdempotent() {
        assertFalse(h.state().onboardingAccepted)
        assertEquals(MutationResult.Success, runBlocking { h.admin.acceptOnboarding() })
        assertEquals(MutationResult.Success, runBlocking { h.admin.acceptOnboarding() })
        h.restart()
        assertTrue(h.state().onboardingAccepted)
    }

    @Test fun acceptingTheNoticeNeverNeedsASessionAndChangesNothingElse() {
        h.enablePin(pin); h.hide(); // sin sesión: hide queda denegado
        val before = h.state()
        assertEquals(MutationResult.Success, runBlocking { h.admin.acceptOnboarding() })
        assertEquals(before.toBuilder().setOnboardingAccepted(true).build(), h.state())
    }

    @Test fun aFailedWriteIsNeverReportedAsAccepted() {
        h.store.failWhen = { true }
        assertEquals(MutationResult.StorageFailure, runBlocking { h.admin.acceptOnboarding() })
        h.store.failWhen = { false }
        assertFalse(h.state().onboardingAccepted)
    }

    @Test fun anUnreadableStateRequiresRecoveryInsteadOfAccepting() {
        h.enablePin(pin); h.close()
        h.file.writeBytes(h.file.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })
        h.restart()
        assertEquals(MutationResult.RecoveryRequired, runBlocking { h.admin.acceptOnboarding() })
    }

    @Test fun resettingFromInsideReturnsToTheNoticeWithEmptyRulesAndNoPin() {
        runBlocking { h.admin.acceptOnboarding() }
        h.hide(); h.enabledAndUnlocked()
        assertEquals(MutationResult.Success, runBlocking { h.admin.resetConfiguration() })
        val s = h.state()
        assertFalse(s.onboardingAccepted) // vuelve la Bienvenida
        assertEquals(0, s.hiddenPackagesCount)
        assertEquals(PinConfigProto.ModeCase.DISABLED, s.pin.modeCase)
    }
}
