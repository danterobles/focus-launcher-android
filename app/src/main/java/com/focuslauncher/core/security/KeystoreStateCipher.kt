package com.focuslauncher.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.focuslauncher.data.local.StateFailure
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Clave AES-256 no exportable en Android Keystore. Sin autenticación de usuario por uso: Home debe
 * poder leer su política tras el desbloqueo normal. Android Keystore no guarda ni compara el PIN.
 */
class KeystoreStateCipher(private val alias: String) : AesGcmStateCipher() {

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun keyState(): KeyState = try {
        val store = keyStore()
        when {
            !store.containsAlias(alias) -> KeyState.Absent
            store.getKey(alias, null) is SecretKey -> KeyState.Present
            else -> KeyState.Invalid
        }
    } catch (_: UnrecoverableKeyException) {
        KeyState.Invalid
    } catch (e: GeneralSecurityException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    } catch (e: IOException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    } catch (e: ProviderException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    }

    override fun createKey() {
        try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            generator.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generator.generateKey()
        } catch (e: GeneralSecurityException) {
            throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
        } catch (e: ProviderException) {
            throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
        }
    }

    override fun secretKey(): SecretKey = try {
        keyStore().getKey(alias, null) as? SecretKey
            ?: throw StateFailure(StateFailure.Kind.KeyLost)
    } catch (e: StateFailure) {
        throw e
    } catch (e: UnrecoverableKeyException) {
        throw StateFailure(StateFailure.Kind.KeyLost, e)
    } catch (e: GeneralSecurityException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    } catch (e: IOException) {
        throw StateFailure(StateFailure.Kind.KeyUnavailable, e)
    }

    companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "focus_state_aes_v1"
    }
}
