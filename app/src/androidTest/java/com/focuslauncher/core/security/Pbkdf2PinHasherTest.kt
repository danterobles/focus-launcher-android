package com.focuslauncher.core.security

import android.util.Log
import com.focuslauncher.data.local.StateValidator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** KDF real de la plataforma (no un fake). */
class Pbkdf2PinHasherTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun matchesKnownPbkdf2HmacSha256Vectors() {
        // RFC 7914 / vectores públicos: P="password", S="salt", dkLen=32.
        assertArrayEquals(
            hex("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"),
            Pbkdf2PinHasher.pbkdf2("password".toCharArray(), "salt".toByteArray(), 1),
        )
        assertArrayEquals(
            hex("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43"),
            Pbkdf2PinHasher.pbkdf2("password".toCharArray(), "salt".toByteArray(), 2),
        )
    }

    @Test fun usesMandatedParametersAndFreshSalts() = runBlocking {
        val hasher = Pbkdf2PinHasher(Dispatchers.Default)
        val s1 = hasher.newSalt(); val s2 = hasher.newSalt()
        assertEquals(16, s1.size)
        assertFalse(s1.contentEquals(s2))
        val d1 = hasher.derive("123456".toCharArray(), s1, StateValidator.KDF_ITERATIONS)
        val d2 = hasher.derive("123456".toCharArray(), s1, StateValidator.KDF_ITERATIONS)
        assertEquals(32, d1.size)
        assertArrayEquals(d1, d2) // determinista con el mismo salt
        assertFalse(d1.contentEquals(hasher.derive("123456".toCharArray(), s2, StateValidator.KDF_ITERATIONS)))
    }

    @Test fun measuresTheRealCostOf600000Iterations() {
        val salt = ByteArray(16) { it.toByte() }
        Pbkdf2PinHasher.pbkdf2("123456".toCharArray(), salt, 600_000) // calentamiento
        val ms = (1..7).map {
            val t0 = System.nanoTime()
            Pbkdf2PinHasher.pbkdf2("123456".toCharArray(), salt, StateValidator.KDF_ITERATIONS)
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
        }.sorted()
        Log.i("FocusKdfCost", "PBKDF2-HMAC-SHA256 600000 it: min=${ms.first()} mediana=${ms[ms.size / 2]} max=${ms.last()} ms (n=${ms.size})")
    }
}
