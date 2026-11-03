package com.focuslauncher.data.local

import com.focuslauncher.data.local.proto.FocusStateProto
import com.focuslauncher.data.local.proto.PinConfigProto
import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateValidatorTest {
    private val initial = StateMapper.initialState()
    private val own = "com.focuslauncher"

    private fun kind(state: FocusStateProto): StateFailure.Kind? =
        try { StateValidator.validate(state); null } catch (e: StateFailure) { e.kind }

    private fun withPin(f: (com.focuslauncher.data.local.proto.PinVerifierProto.Builder) -> Unit): FocusStateProto {
        val v = Fixtures.pinEnabled().enabled.toBuilder().also(f)
        return initial.toBuilder().setPin(PinConfigProto.newBuilder().setEnabled(v)).build()
    }

    @Test fun initialAndRichStatesAreValid() {
        assertEquals(null, kind(initial))
        assertEquals(null, kind(Fixtures.rich()))
    }

    @Test fun defaultProtobufIsNotAnOperationalConfiguration() {
        assertEquals(StateFailure.Kind.InvalidState, kind(FocusStateProto.getDefaultInstance()))
    }

    @Test fun schemaVersions() {
        assertEquals(StateFailure.Kind.FutureVersion, kind(initial.toBuilder().setSchemaVersion(2).build()))
        assertEquals(StateFailure.Kind.InvalidState, kind(initial.toBuilder().setSchemaVersion(0).build()))
    }

    @Test fun pinModeMustBeExplicitAndTrue() {
        assertEquals(StateFailure.Kind.InvalidState, kind(initial.toBuilder().clearPin().build()))
        assertEquals(StateFailure.Kind.InvalidState,
            kind(initial.toBuilder().setPin(PinConfigProto.newBuilder().setDisabled(false)).build()))
    }

    @Test fun enabledPinParametersAreStrict() {
        assertEquals(null, kind(withPin { }))
        assertEquals(StateFailure.Kind.InvalidState, kind(withPin { it.setKdf("PBKDF2WithHmacSHA1") }))
        assertEquals(StateFailure.Kind.InvalidState, kind(withPin { it.setIterations(1_000_000_000) }))
        assertEquals(StateFailure.Kind.InvalidState, kind(withPin { it.setIterations(1) }))
        assertEquals(StateFailure.Kind.InvalidState, kind(withPin { it.setSalt(ByteString.copyFrom(ByteArray(15))) }))
        assertEquals(StateFailure.Kind.InvalidState, kind(withPin { it.setVerifier(ByteString.copyFrom(ByteArray(33))) }))
    }

    @Test fun rulesNeedPackageAndNonNegativeSerial() {
        assertEquals(StateFailure.Kind.InvalidState, kind(initial.toBuilder().addHiddenPackages(Fixtures.pkg("")).build()))
        assertEquals(StateFailure.Kind.InvalidState, kind(initial.toBuilder().addHiddenPackages(Fixtures.pkg("a.app", -1)).build()))
    }

    @Test fun ruleCountIsBounded() {
        val ok = initial.toBuilder().apply { repeat(StateValidator.MAX_RULES) { addHiddenPackages(Fixtures.pkg("p$it")) } }.build()
        assertEquals(null, kind(ok))
        assertEquals(StateFailure.Kind.TooLarge, kind(ok.toBuilder().addHiddenPackages(Fixtures.pkg("extra")).build()))
    }

    @Test fun duplicateRulesAreNormalizedAsASet() {
        val dup = initial.toBuilder().addHiddenPackages(Fixtures.pkg("a.app")).addHiddenPackages(Fixtures.pkg("a.app")).build()
        assertEquals(null, kind(dup))
        assertEquals(1, StateMapper.toSettings(dup).visibility.hiddenPackages.size)
    }

    @Test fun attemptsRanges() {
        fun st(a: com.focuslauncher.data.local.proto.AttemptStateProto) =
            Fixtures.rich().toBuilder().setAttempts(a).build()
        assertEquals(null, kind(st(Fixtures.attempts(reserved = 4, level = 5))))
        assertEquals(StateFailure.Kind.InvalidState, kind(st(Fixtures.attempts(reserved = 6))))
        assertEquals(StateFailure.Kind.InvalidState, kind(st(Fixtures.attempts(level = 6))))
        // sin espera: como máximo 4 reservas y duración 0
        assertEquals(StateFailure.Kind.InvalidState, kind(st(Fixtures.attempts(reserved = 5))))
        assertEquals(StateFailure.Kind.InvalidState, kind(st(Fixtures.attempts(reserved = 1, durationMs = 30_000))))
    }

    @Test fun activeCooldownRequiresFiveReservesAndAKnownDuration() {
        fun st(a: com.focuslauncher.data.local.proto.AttemptStateProto) =
            Fixtures.rich().toBuilder().setAttempts(a).build()
        StateValidator.COOLDOWN_MS.forEach {
            assertEquals(null, kind(st(Fixtures.attempts(reserved = 5, level = 2, cooldown = true, durationMs = it))))
        }
        assertEquals(StateFailure.Kind.InvalidState, kind(st(Fixtures.attempts(reserved = 5, cooldown = true, durationMs = 45_000))))
        assertEquals(StateFailure.Kind.InvalidState, kind(st(Fixtures.attempts(reserved = 4, cooldown = true, durationMs = 30_000))))
        assertEquals(StateFailure.Kind.InvalidState, kind(st(Fixtures.attempts(reserved = 5, cooldown = true, durationMs = 0))))
    }

    @Test fun disabledPinImpliesResetAttempts() {
        assertEquals(StateFailure.Kind.InvalidState,
            kind(initial.toBuilder().setAttempts(Fixtures.attempts(reserved = 1)).build()))
        assertEquals(StateFailure.Kind.InvalidState,
            kind(initial.toBuilder().setAttempts(Fixtures.attempts(level = 1)).build()))
    }

    @Test fun ownPackageCannotGetANewRuleButLegacyRulesSurvive() {
        val withOwn = initial.toBuilder().addHiddenPackages(Fixtures.pkg(own)).build()
        try { StateValidator.validateWrite(initial, withOwn, own); throw AssertionError("debía rechazarse") }
        catch (_: InvalidWriteException) { }
        // Regla heredada: se conserva y otras escrituras siguen permitidas.
        StateValidator.validateWrite(withOwn, withOwn.toBuilder().setOnboardingAccepted(true).build(), own)
    }

    @Test fun invalidResultIsAnInvalidWriteNotAStorageProblem() {
        try {
            StateValidator.validateWrite(initial, initial.toBuilder().setSchemaVersion(7).build(), own)
            throw AssertionError("debía rechazarse")
        } catch (e: InvalidWriteException) { assertTrue(e.message!!.isNotEmpty()) }
    }

    @Test fun settingsProjectionExposesNoVerifier() {
        val s = StateMapper.toSettings(Fixtures.rich())
        assertTrue(s.pinEnabled)
        assertTrue(s.onboardingAccepted)
        assertEquals(setOf("a.app", "b.app"), s.visibility.hiddenPackages.map { it.packageName }.toSet())
        assertEquals(false, StateMapper.toSettings(initial).pinEnabled)
    }
}
