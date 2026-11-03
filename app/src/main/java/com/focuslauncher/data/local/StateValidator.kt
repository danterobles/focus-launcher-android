package com.focuslauncher.data.local

import com.focuslauncher.data.local.proto.FocusStateProto
import com.focuslauncher.data.local.proto.PinConfigProto

/** Invariantes de DATA.md §3. Ningún estado inválido se interpreta ni se corrige con defaults. */
object StateValidator {
    const val SCHEMA_VERSION = 1
    const val MAX_RULES = 10_000
    const val KDF = "PBKDF2WithHmacSHA256"
    const val KDF_ITERATIONS = 600_000
    const val SALT_BYTES = 16
    const val VERIFIER_BYTES = 32
    const val MAX_RESERVED = 5
    const val MAX_LEVEL = 5
    val COOLDOWN_MS: Set<Long> = setOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L)

    /** Lanza [StateFailure] si [state] no es una configuración operativa válida. */
    fun validate(state: FocusStateProto) {
        when {
            state.schemaVersion > SCHEMA_VERSION -> throw StateFailure(StateFailure.Kind.FutureVersion)
            state.schemaVersion != SCHEMA_VERSION -> invalid() // incluye el protobuf por defecto (todo ceros)
        }
        if (state.hiddenPackagesCount > MAX_RULES) throw StateFailure(StateFailure.Kind.TooLarge)
        state.hiddenPackagesList.forEach { if (it.packageName.isEmpty() || it.userSerial < 0) invalid() }
        validatePin(state.pin)
        validateAttempts(state)
    }

    private fun validatePin(pin: PinConfigProto) {
        when (pin.modeCase) {
            PinConfigProto.ModeCase.DISABLED -> if (!pin.disabled) invalid()
            PinConfigProto.ModeCase.ENABLED -> {
                val v = pin.enabled
                if (v.kdf != KDF || v.iterations != KDF_ITERATIONS ||
                    v.salt.size() != SALT_BYTES || v.verifier.size() != VERIFIER_BYTES
                ) invalid()
            }
            PinConfigProto.ModeCase.MODE_NOT_SET, null -> invalid()
        }
    }

    private fun validateAttempts(state: FocusStateProto) {
        val a = state.attempts
        if (a.reservedInWindow > MAX_RESERVED || a.penaltyLevel > MAX_LEVEL) invalid()
        if (a.cooldownActive) {
            if (a.reservedInWindow != MAX_RESERVED || a.cooldownDurationMs !in COOLDOWN_MS) invalid()
        } else {
            if (a.cooldownDurationMs != 0L || a.reservedInWindow > MAX_RESERVED - 1) invalid()
        }
        if (state.pin.modeCase == PinConfigProto.ModeCase.DISABLED &&
            (a.reservedInWindow != 0 || a.penaltyLevel != 0 || a.cooldownActive || a.cooldownDurationMs != 0L)
        ) invalid()
    }

    /**
     * Reglas de escritura: la propia app no puede ser objeto de una regla nueva. Las reglas
     * heredadas que la mencionen se conservan y no afectan el acceso a ajustes.
     */
    fun validateWrite(current: FocusStateProto, next: FocusStateProto, ownPackage: String) {
        try {
            validate(next)
        } catch (e: StateFailure) {
            // El resultado propuesto es inválido: es un error de quien escribe, no del almacenamiento.
            throw InvalidWriteException("Estado resultante inválido (${e.kind})")
        }
        val before = current.hiddenPackagesList.filter { it.packageName == ownPackage }.toSet()
        val added = next.hiddenPackagesList.filter { it.packageName == ownPackage }.toSet() - before
        if (added.isNotEmpty()) throw InvalidWriteException("La propia app no puede ocultarse")
    }

    private fun invalid(): Nothing = throw StateFailure(StateFailure.Kind.InvalidState)
}
