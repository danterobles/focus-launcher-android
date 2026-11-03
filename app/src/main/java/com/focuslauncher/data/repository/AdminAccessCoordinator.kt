package com.focuslauncher.data.repository

import com.focuslauncher.core.di.MonotonicClock
import com.focuslauncher.core.security.AdminSessionManager
import com.focuslauncher.core.security.PinHasher
import com.focuslauncher.core.security.PinPolicy
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateFailure
import com.focuslauncher.data.local.StateMapper
import com.focuslauncher.data.local.StateResult
import com.focuslauncher.data.local.StateValidator
import com.focuslauncher.data.local.StateWriteResult
import com.focuslauncher.data.local.proto.AttemptStateProto
import com.focuslauncher.data.local.proto.FocusStateProto
import com.focuslauncher.data.local.proto.PackageKeyProto
import com.focuslauncher.data.local.proto.PinConfigProto
import com.focuslauncher.data.local.proto.PinVerifierProto
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.AuthenticationResult
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.repository.AdminRepository
import com.focuslauncher.domain.repository.OnboardingRepository
import com.google.protobuf.ByteString
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Único camino de escritura administrativa (DATA.md §5–§7, ARCHITECTURE.md §8).
 *
 * - Un [Mutex] de alcance aplicación serializa verificaciones, mutaciones y cambios de credencial
 *   (nunca dos KDF en paralelo). La revocación de sesión NO lo toma.
 * - La autorización se comprueba contra la sesión interna en el propio commit (dentro de
 *   `updateData`), no solo antes de empezar.
 * - Reloj monotónico exclusivamente: jamás hora de pared.
 */
@Singleton
class AdminAccessCoordinator @Inject constructor(
    private val store: FocusStateStore,
    private val sessions: AdminSessionManager,
    private val hasher: PinHasher,
    private val clock: MonotonicClock,
) : AdminRepository, OnboardingRepository {

    private val mutex = Mutex()
    private val timerLock = Any()

    /** Instante monotónico en que termina la espera activa de ESTE proceso; null si no la hemos visto. */
    @Volatile private var cooldownEndsAt: Long? = null

    override val isAuthorized: StateFlow<Boolean> get() = sessions.authorized

    override fun lock() = sessions.revoke()

    // ------------------------------------------------------------------ verificación del PIN

    override suspend fun verifyPin(pin: CharArray): AuthenticationResult {
        try {
            if (!PinPolicy.isValid(pin)) return AuthenticationResult.InvalidInput // no consume intento
            val generation = sessions.generation()
            return mutex.withLock { verifyLocked(pin, generation) }
        } finally {
            PinPolicy.wipe(pin)
        }
    }

    private suspend fun verifyLocked(pin: CharArray, generation: Int): AuthenticationResult {
        val state = when (val r = store.readCurrent()) {
            is StateResult.Ready -> r.state
            is StateResult.Failed -> return r.failure.toAuthFailure()
        }
        if (state.pin.modeCase != PinConfigProto.ModeCase.ENABLED) return AuthenticationResult.NotRequired

        // 1) Espera activa: denegar antes de ejecutar ninguna KDF.
        if (state.attempts.cooldownActive) {
            val end = cooldownEnd(state.attempts.cooldownDurationMs)
            val remaining = end - clock.elapsedRealtimeMillis()
            if (remaining > 0) return AuthenticationResult.Cooldown(remaining)
            // 5) Vencida y sin verificación en curso: reservas 0, sin espera, se mantiene el nivel.
            val expired = store.update { cur ->
                if (!cur.attempts.cooldownActive) cur
                else cur.toBuilder().setAttempts(
                    cur.attempts.toBuilder().setReservedInWindow(0).setCooldownActive(false).setCooldownDurationMs(0),
                ).build()
            }
            if (expired != StateWriteResult.Success) return expired.toAuthFailure()
        }
        cooldownEndsAt = null

        // 2) Reserva atómica y duradera ANTES de la KDF. Si no se guarda, no hay intento.
        var verifier: PinVerifierProto? = null
        var reservedNow = 0
        var cooldownStartedMs = 0L
        val reserved = store.update { cur ->
            if (cur.pin.modeCase != PinConfigProto.ModeCase.ENABLED) return@update cur
            val a = cur.attempts
            val reserves = a.reservedInWindow + 1
            val b = a.toBuilder().setReservedInWindow(reserves)
            if (reserves >= StateValidator.MAX_RESERVED) {
                val duration = StateValidator.COOLDOWN_MS.sorted()[a.penaltyLevel.coerceIn(0, StateValidator.MAX_LEVEL)]
                b.setCooldownActive(true).setCooldownDurationMs(duration)
                    .setPenaltyLevel((a.penaltyLevel + 1).coerceAtMost(StateValidator.MAX_LEVEL))
                cooldownStartedMs = duration
            }
            verifier = cur.pin.enabled
            reservedNow = reserves
            cur.toBuilder().setAttempts(b).build()
        }
        if (reserved != StateWriteResult.Success) return reserved.toAuthFailure()
        val v = verifier ?: return AuthenticationResult.Unavailable
        if (cooldownStartedMs > 0) synchronized(timerLock) { cooldownEndsAt = clock.elapsedRealtimeMillis() + cooldownStartedMs }

        // 3) KDF. Una cancelación aquí NO devuelve la reserva (ya está persistida).
        val derived = hasher.derive(pin, v.salt.toByteArray(), v.iterations)
        val match = MessageDigest.isEqual(derived, v.verifier.toByteArray())
        derived.fill(0)

        // Si se abandonó la pantalla / se bloqueó el dispositivo durante la KDF, el resultado
        // tardío no emite sesión (el intento queda contabilizado).
        if (sessions.generation() != generation) return AuthenticationResult.Unavailable

        if (!match) {
            return if (cooldownStartedMs > 0) AuthenticationResult.Cooldown(cooldownStartedMs)
            else AuthenticationResult.IncorrectPin(StateValidator.MAX_RESERVED - reservedNow)
        }

        // 4) Acierto (también en el 5.º intento): reinicia reservas, nivel y espera; solo tras
        // commit duradero se emite la sesión.
        val success = store.update { cur ->
            cur.toBuilder().setAttempts(AttemptStateProto.getDefaultInstance()).build()
        }
        if (success != StateWriteResult.Success) return success.toAuthFailure()
        synchronized(timerLock) { cooldownEndsAt = null }
        return if (sessions.issue(generation)) AuthenticationResult.Success else AuthenticationResult.Unavailable
    }

    override suspend fun cooldownRemainingMs(): Long {
        val state = (store.readCurrent() as? StateResult.Ready)?.state ?: return 0
        if (state.pin.modeCase != PinConfigProto.ModeCase.ENABLED || !state.attempts.cooldownActive) return 0
        return (cooldownEnd(state.attempts.cooldownDurationMs) - clock.elapsedRealtimeMillis()).coerceAtLeast(0)
    }

    /**
     * Si el archivo dice «espera activa» y este proceso aún no la vio, se reaplica su duración
     * COMPLETA desde ahora: reiniciar el proceso o el teléfono nunca la acorta.
     */
    private fun cooldownEnd(durationMs: Long): Long = synchronized(timerLock) {
        cooldownEndsAt ?: (clock.elapsedRealtimeMillis() + durationMs).also { cooldownEndsAt = it }
    }

    // ------------------------------------------------------------------ mutaciones

    /**
     * Mutación protegida. La autorización se evalúa contra el estado actual DENTRO del commit:
     * con PIN habilitado exige sesión vigente justo antes de escribir.
     */
    private suspend fun mutate(transform: (FocusStateProto) -> FocusStateProto): MutationResult =
        mutex.withLock {
            var denied = false
            val result = store.update { cur ->
                if (cur.pin.modeCase == PinConfigProto.ModeCase.ENABLED && !sessions.isActive()) {
                    denied = true
                    cur
                } else transform(cur)
            }
            if (denied) MutationResult.AuthenticationRequired else result.toMutation()
        }

    override suspend fun setPackageHidden(key: AppPackageKey, hidden: Boolean): MutationResult = mutate { cur ->
        val keep = cur.hiddenPackagesList.filterNot { it.packageName == key.packageName && it.userSerial == key.userSerial.value }
        val list = if (hidden) keep + PackageKeyProto.newBuilder().setPackageName(key.packageName).setUserSerial(key.userSerial.value).build() else keep
        cur.toBuilder().clearHiddenPackages().addAllHiddenPackages(list).build()
    }

    override suspend fun enablePin(pin: CharArray, confirmation: CharArray): MutationResult =
        changeCredential(pin, confirmation, mustAlreadyBeEnabled = false)

    override suspend fun changePin(pin: CharArray, confirmation: CharArray): MutationResult =
        changeCredential(pin, confirmation, mustAlreadyBeEnabled = true)

    private suspend fun changeCredential(pin: CharArray, confirmation: CharArray, mustAlreadyBeEnabled: Boolean): MutationResult {
        try {
            if (!PinPolicy.isValid(pin) || !PinPolicy.matches(pin, confirmation)) return MutationResult.InvalidInput("PIN")
            return mutex.withLock {
                val before = when (val r = store.readCurrent()) {
                    is StateResult.Ready -> r.state
                    is StateResult.Failed -> return@withLock r.failure.toMutationFailure()
                }
                val enabled = before.pin.modeCase == PinConfigProto.ModeCase.ENABLED
                if (enabled != mustAlreadyBeEnabled) return@withLock MutationResult.InvalidInput("estado del PIN")
                // Cambiar exige sesión vigente ANTES de gastar una KDF…
                if (enabled && !sessions.isActive()) return@withLock MutationResult.AuthenticationRequired

                val salt = hasher.newSalt()
                val derived = hasher.derive(pin, salt, StateValidator.KDF_ITERATIONS)

                // …y de nuevo justo antes de confirmar (la KDF pudo tardar y la sesión caducar/revocarse).
                var denied = false
                var changedMeanwhile = false
                val result = store.update { cur ->
                    when {
                        cur.pin != before.pin -> { changedMeanwhile = true; cur }
                        enabled && !sessions.isActive() -> { denied = true; cur }
                        else -> cur.toBuilder()
                            .setPin(
                                PinConfigProto.newBuilder().setEnabled(
                                    PinVerifierProto.newBuilder()
                                        .setKdf(StateValidator.KDF)
                                        .setIterations(StateValidator.KDF_ITERATIONS)
                                        .setSalt(ByteString.copyFrom(salt))
                                        .setVerifier(ByteString.copyFrom(derived)),
                                ),
                            )
                            .setAttempts(AttemptStateProto.getDefaultInstance())
                            .build()
                    }
                }
                derived.fill(0)
                when {
                    denied -> MutationResult.AuthenticationRequired
                    changedMeanwhile -> MutationResult.Unavailable
                    result == StateWriteResult.Success -> {
                        // Tras guardar se revoca: el siguiente ingreso exige el PIN nuevo.
                        synchronized(timerLock) { cooldownEndsAt = null }
                        sessions.revoke()
                        MutationResult.Success
                    }
                    else -> result.toMutation()
                }
            }
        } finally {
            PinPolicy.wipe(pin, confirmation)
        }
    }

    override suspend fun disablePin(): MutationResult {
        val result = mutate { cur ->
            cur.toBuilder().setPin(PinConfigProto.newBuilder().setDisabled(true))
                .setAttempts(AttemptStateProto.getDefaultInstance()).build()
        }
        if (result == MutationResult.Success) {
            synchronized(timerLock) { cooldownEndsAt = null }
            sessions.revoke()
        }
        return result
    }

    override suspend fun resetConfiguration(): MutationResult {
        val result = mutate { StateMapper.initialState() }
        if (result == MutationResult.Success) {
            synchronized(timerLock) { cooldownEndsAt = null }
            sessions.revoke()
        }
        return result
    }

    // ------------------------------------------------------------------ aviso de alcance

    /** Serializado con el resto de escrituras por el mismo Mutex; confirma solo tras persistir. */
    override suspend fun acceptOnboarding(): MutationResult = mutex.withLock {
        store.update { cur -> if (cur.onboardingAccepted) cur else cur.toBuilder().setOnboardingAccepted(true).build() }.toMutation()
    }

    // ------------------------------------------------------------------ mapeo de errores

    private fun StateWriteResult.toMutation(): MutationResult = when (this) {
        StateWriteResult.Success -> MutationResult.Success
        is StateWriteResult.InvalidInput -> MutationResult.InvalidInput(reason)
        StateWriteResult.StorageFailure -> MutationResult.StorageFailure
        StateWriteResult.Unavailable -> MutationResult.Unavailable
        StateWriteResult.RecoveryRequired -> MutationResult.RecoveryRequired
    }

    private fun StateWriteResult.toAuthFailure(): AuthenticationResult = when (this) {
        StateWriteResult.Success -> AuthenticationResult.Unavailable
        is StateWriteResult.InvalidInput -> AuthenticationResult.StorageFailure
        StateWriteResult.StorageFailure -> AuthenticationResult.StorageFailure
        StateWriteResult.Unavailable -> AuthenticationResult.Unavailable
        StateWriteResult.RecoveryRequired -> AuthenticationResult.RecoveryRequired
    }

    private fun StateFailure.toAuthFailure(): AuthenticationResult = when {
        requiresRecovery -> AuthenticationResult.RecoveryRequired
        kind == StateFailure.Kind.KeyUnavailable -> AuthenticationResult.Unavailable
        else -> AuthenticationResult.StorageFailure
    }

    private fun StateFailure.toMutationFailure(): MutationResult = when {
        requiresRecovery -> MutationResult.RecoveryRequired
        kind == StateFailure.Kind.KeyUnavailable -> MutationResult.Unavailable
        else -> MutationResult.StorageFailure
    }
}
