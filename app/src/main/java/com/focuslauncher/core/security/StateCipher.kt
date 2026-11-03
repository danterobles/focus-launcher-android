package com.focuslauncher.core.security

import com.focuslauncher.data.local.StateFailure
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class KeyState { Absent, Present, Invalid }

class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray)

/** Cifrado autenticado del estado. Nunca almacena ni deriva el PIN. */
interface StateCipher {
    /** Puede lanzar [StateFailure] (KeyUnavailable) si el almacén de claves no responde. */
    fun keyState(): KeyState

    /** Crea la clave AES-256. Solo para instalación limpia. */
    fun createKey()

    fun encrypt(plaintext: ByteArray, aad: ByteArray): EncryptedPayload

    fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
}

/** AES-256-GCM con IV generado por el propio Cipher (nunca derivado de datos propios). */
abstract class AesGcmStateCipher : StateCipher {
    protected abstract fun secretKey(): SecretKey

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): EncryptedPayload = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        EncryptedPayload(cipher.iv, ciphertext)
    } catch (e: StateFailure) {
        throw e
    } catch (e: InvalidKeyException) {
        throw StateFailure(StateFailure.Kind.KeyLost, e)
    } catch (e: ProviderException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    } catch (e: GeneralSecurityException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        cipher.doFinal(ciphertext)
    } catch (e: StateFailure) {
        throw e
    } catch (e: AEADBadTagException) {
        throw StateFailure(StateFailure.Kind.Corrupt, e)
    } catch (e: BadPaddingException) {
        throw StateFailure(StateFailure.Kind.Corrupt, e)
    } catch (e: IllegalBlockSizeException) {
        throw StateFailure(StateFailure.Kind.Corrupt, e)
    } catch (e: InvalidKeyException) {
        throw StateFailure(StateFailure.Kind.KeyLost, e)
    } catch (e: ProviderException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    } catch (e: GeneralSecurityException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
