package com.focuslauncher.data.local

/**
 * Fallo clasificado al leer/escribir el estado. Solo transporta la clase del fallo: nunca bytes,
 * claves, verificadores ni contenido descifrado.
 */
class StateFailure(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {

    enum class Kind {
        /** Sobre truncado/alterado, etiqueta GCM inválida o protobuf ilegible. */
        Corrupt,
        FutureVersion,
        TooLarge,
        InvalidState,
        KeyLost,
        InterruptedInit,

        /** Almacén de claves no disponible ahora mismo: reintentable. */
        KeyUnavailable,

        /** Error de E/S: reintentable. */
        Io,
    }

    /** Reintentar no lo arregla; exige recuperación externa. */
    val requiresRecovery: Boolean
        get() = kind != Kind.KeyUnavailable && kind != Kind.Io
}

/** Escritura rechazada por invariantes (p. ej. regla sobre la propia app). */
class InvalidWriteException(message: String) : Exception(message)
