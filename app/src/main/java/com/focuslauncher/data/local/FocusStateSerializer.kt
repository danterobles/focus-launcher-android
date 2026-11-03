package com.focuslauncher.data.local

import androidx.datastore.core.Serializer
import com.focuslauncher.core.security.StateCipher
import com.focuslauncher.data.local.proto.FocusStateProto
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer cifrado. `defaultValue` es el protobuf vacío, que NO es una configuración válida
 * (schema 0): la instalación limpia se crea explícitamente en el bootstrap, nunca desde aquí.
 * Un archivo ilegible lanza [StateFailure]; no existe ReplaceFileCorruptionHandler.
 */
class FocusStateSerializer(private val cipher: StateCipher) : Serializer<FocusStateProto> {

    override val defaultValue: FocusStateProto = FocusStateProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): FocusStateProto {
        // Se lee un byte más que el límite para detectar archivos demasiado grandes sin cargarlos enteros.
        val bytes = readBounded(input, StateEnvelope.MAX_FILE_BYTES + 1)
        val plain = StateEnvelope.open(bytes, cipher)
        val state = try {
            FocusStateProto.parseFrom(plain)
        } catch (e: InvalidProtocolBufferException) {
            throw StateFailure(StateFailure.Kind.Corrupt, e)
        }
        StateValidator.validate(state)
        return state
    }

    override suspend fun writeTo(t: FocusStateProto, output: OutputStream) {
        StateValidator.validate(t)
        output.write(StateEnvelope.seal(t.toByteArray(), cipher))
    }

    companion object {
        /** Lee como máximo [limit] bytes (`readNBytes` es API 33; el mínimo es 31). */
        internal fun readBounded(input: InputStream, limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var remaining = limit
            while (remaining > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (n < 0) break
                out.write(buffer, 0, n)
                remaining -= n
            }
            return out.toByteArray()
        }
    }
}
