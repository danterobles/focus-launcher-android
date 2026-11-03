package com.focuslauncher.data.local

import com.focuslauncher.core.security.AesGcmStateCipher
import com.focuslauncher.core.security.KeyState
import com.focuslauncher.data.local.proto.FocusStateProto
import com.focuslauncher.data.local.proto.PackageKeyProto
import com.focuslauncher.data.local.proto.PinConfigProto
import com.focuslauncher.data.local.proto.PinVerifierProto
import com.focuslauncher.data.local.proto.AttemptStateProto
import com.google.protobuf.ByteString
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Cifrador de pruebas JVM con la misma lógica AES-GCM que producción y un almacén de claves controlable. */
class FakeKeyCipher(initial: KeyState = KeyState.Absent) : AesGcmStateCipher() {
    var state: KeyState = initial
    var unavailable = false
    var createCount = 0
    private var key: SecretKey? = if (initial == KeyState.Present) newKey() else null

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun keyState(): KeyState {
        if (unavailable) throw StateFailure(StateFailure.Kind.KeyUnavailable)
        return state
    }

    override fun createKey() {
        if (unavailable) throw StateFailure(StateFailure.Kind.KeyUnavailable)
        createCount++
        key = newKey()
        state = KeyState.Present
    }

    fun loseKey() { key = null; state = KeyState.Absent }

    override fun secretKey(): SecretKey = key ?: throw StateFailure(StateFailure.Kind.KeyLost)
}

object Fixtures {
    fun pinEnabled(): PinConfigProto = PinConfigProto.newBuilder().setEnabled(
        PinVerifierProto.newBuilder()
            .setKdf(StateValidator.KDF)
            .setIterations(StateValidator.KDF_ITERATIONS)
            .setSalt(ByteString.copyFrom(ByteArray(16) { 1 }))
            .setVerifier(ByteString.copyFrom(ByteArray(32) { 2 })),
    ).build()

    fun pkg(name: String, serial: Long = 0) =
        PackageKeyProto.newBuilder().setPackageName(name).setUserSerial(serial).build()

    fun attempts(reserved: Int = 0, level: Int = 0, cooldown: Boolean = false, durationMs: Long = 0) =
        AttemptStateProto.newBuilder().setReservedInWindow(reserved).setPenaltyLevel(level)
            .setCooldownActive(cooldown).setCooldownDurationMs(durationMs).build()

    /** Estado con PIN habilitado, reglas e intentos: permite comprobar que todo sobrevive al reinicio. */
    fun rich(): FocusStateProto = StateMapper.initialState().toBuilder()
        .setOnboardingAccepted(true)
        .addHiddenPackages(pkg("a.app")).addHiddenPackages(pkg("b.app", 0))
        .setPin(pinEnabled())
        .setAttempts(attempts(reserved = 3, level = 1))
        .build()
}
