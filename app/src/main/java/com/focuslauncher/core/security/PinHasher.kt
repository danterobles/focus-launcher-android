package com.focuslauncher.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Derivación del verificador del PIN. Nunca se guarda el PIN ni un hash rápido. */
interface PinHasher {
    /** Salt aleatorio de 16 bytes. */
    fun newSalt(): ByteArray

    /** Derivación costosa: fuera del hilo principal y con concurrencia 1. */
    suspend fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray
}

class Pbkdf2PinHasher(private val kdfDispatcher: CoroutineDispatcher) : PinHasher {
    private val random = SecureRandom()

    override fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    override suspend fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray =
        withContext(kdfDispatcher) { pbkdf2(pin, salt, iterations) }

    companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val SALT_BYTES = 16
        const val OUTPUT_BITS = 256

        /** Síncrona y sin estado: usada por [derive] y por las pruebas con vectores conocidos. */
        fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(secret, salt, iterations, OUTPUT_BITS)
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }
    }
}
