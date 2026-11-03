package com.focuslauncher.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class StateEnvelopeTest {
    private val cipher = FakeKeyCipher().also { it.createKey() }
    private val plain = "estado de prueba".toByteArray()

    private fun kindOf(block: () -> Unit): StateFailure.Kind? =
        try { block(); null } catch (e: StateFailure) { e.kind }

    @Test
    fun roundTrip() {
        val sealed = StateEnvelope.seal(plain, cipher)
        assertArrayEquals(plain, StateEnvelope.open(sealed, cipher))
        assertEquals("FCLS", String(sealed.copyOfRange(0, 4)))
        assertEquals(1, sealed[4].toInt())
        assertEquals(12, sealed[5].toInt())
    }

    @Test
    fun everySealUsesADifferentIv() {
        val ivs = (1..50).map { StateEnvelope.seal(plain, cipher).copyOfRange(6, 18).toList() }.toSet()
        assertEquals(50, ivs.size)
    }

    @Test
    fun flippingAnyByteIsRejected() {
        val sealed = StateEnvelope.seal(plain, cipher)
        for (i in sealed.indices) {
            val tampered = sealed.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            val kind = kindOf { StateEnvelope.open(tampered, cipher) }
            assertFalse("el byte $i alterado fue aceptado", kind == null)
        }
    }

    @Test
    fun truncatedFilesAreCorruptNeverDefaults() {
        val sealed = StateEnvelope.seal(plain, cipher)
        for (len in listOf(0, 3, 5, 6, 17, 18, sealed.size - 1)) {
            assertEquals("len=$len", StateFailure.Kind.Corrupt, kindOf { StateEnvelope.open(sealed.copyOf(len), cipher) })
        }
    }

    @Test
    fun badMagicAndUnexpectedIvLengthAreCorrupt() {
        val sealed = StateEnvelope.seal(plain, cipher)
        assertEquals(StateFailure.Kind.Corrupt, kindOf { StateEnvelope.open(sealed.copyOf().also { it[0] = 'X'.code.toByte() }, cipher) })
        assertEquals(StateFailure.Kind.Corrupt, kindOf { StateEnvelope.open(sealed.copyOf().also { it[5] = 16 }, cipher) })
        assertEquals(StateFailure.Kind.Corrupt, kindOf { StateEnvelope.open(sealed.copyOf().also { it[4] = 0 }, cipher) })
    }

    @Test
    fun futureEnvelopeVersionIsReportedAsFutureVersion() {
        val sealed = StateEnvelope.seal(plain, cipher)
        assertEquals(StateFailure.Kind.FutureVersion, kindOf { StateEnvelope.open(sealed.copyOf().also { it[4] = 2 }, cipher) })
    }

    @Test
    fun oversizedFilesAreRejectedWithoutDecrypting() {
        val big = ByteArray(StateEnvelope.MAX_FILE_BYTES + 1)
        assertEquals(StateFailure.Kind.TooLarge, kindOf { StateEnvelope.open(big, cipher) })
    }

    @Test
    fun differentKeyCannotOpenTheEnvelope() {
        val sealed = StateEnvelope.seal(plain, cipher)
        val other = FakeKeyCipher().also { it.createKey() }
        assertEquals(StateFailure.Kind.Corrupt, kindOf { StateEnvelope.open(sealed, other) })
    }

    @Test
    fun lostKeyIsReportedAsKeyLost() {
        val sealed = StateEnvelope.seal(plain, cipher)
        cipher.loseKey()
        try { StateEnvelope.open(sealed, cipher); fail() } catch (e: StateFailure) {
            assertEquals(StateFailure.Kind.KeyLost, e.kind)
        }
    }
}

class BoundedReadTest {
    @Test
    fun readsEverythingBelowTheLimitAndStopsAtTheLimit() {
        val data = ByteArray(20_000) { it.toByte() }
        assertArrayEquals(data, FocusStateSerializer.readBounded(data.inputStream(), 30_000))
        assertEquals(10_000, FocusStateSerializer.readBounded(data.inputStream(), 10_000).size)
        assertEquals(0, FocusStateSerializer.readBounded(ByteArray(0).inputStream(), 10).size)
    }
}
