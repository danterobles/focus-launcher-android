package com.focuslauncher.data.local

import com.focuslauncher.core.security.StateCipher

/**
 * Sobre binario v1: `"FCLS" | version(1) | ivLength(1) | iv(12) | AES-GCM(protobuf)+tag(128 bits)`.
 * La cabecera fija y la cadena de contexto se autentican como AAD.
 */
object StateEnvelope {
    const val MAX_FILE_BYTES = 2 * 1024 * 1024
    const val VERSION: Int = 1
    const val IV_LENGTH = 12
    private const val TAG_BYTES = 16
    private const val CONTEXT = "com.focuslauncher|focus-state|v1"
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'C'.code.toByte(), 'L'.code.toByte(), 'S'.code.toByte())
    private const val HEADER_SIZE = 6

    private val header = MAGIC + byteArrayOf(VERSION.toByte(), IV_LENGTH.toByte())
    private val aad = header + CONTEXT.toByteArray(Charsets.US_ASCII)

    fun seal(plaintext: ByteArray, cipher: StateCipher): ByteArray {
        val payload = cipher.encrypt(plaintext, aad)
        check(payload.iv.size == IV_LENGTH) { "IV inesperado" }
        val sealed = header + payload.iv + payload.ciphertext
        if (sealed.size > MAX_FILE_BYTES) throw StateFailure(StateFailure.Kind.TooLarge)
        return sealed
    }

    fun open(bytes: ByteArray, cipher: StateCipher): ByteArray {
        if (bytes.size > MAX_FILE_BYTES) throw StateFailure(StateFailure.Kind.TooLarge)
        if (bytes.size < HEADER_SIZE + IV_LENGTH + TAG_BYTES) throw StateFailure(StateFailure.Kind.Corrupt)
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC)) throw StateFailure(StateFailure.Kind.Corrupt)
        val version = bytes[4].toInt() and 0xFF
        if (version > VERSION) throw StateFailure(StateFailure.Kind.FutureVersion)
        if (version != VERSION) throw StateFailure(StateFailure.Kind.Corrupt)
        if ((bytes[5].toInt() and 0xFF) != IV_LENGTH) throw StateFailure(StateFailure.Kind.Corrupt)
        val iv = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + IV_LENGTH)
        val ciphertext = bytes.copyOfRange(HEADER_SIZE + IV_LENGTH, bytes.size)
        return cipher.decrypt(iv, ciphertext, aad)
    }
}
