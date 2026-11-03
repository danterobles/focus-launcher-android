package com.focuslauncher.domain.model

sealed interface AuthenticationResult {
    /** Verificación correcta y sesión administrativa emitida. */
    data object Success : AuthenticationResult

    /** No hay PIN habilitado: no hace falta autenticarse. */
    data object NotRequired : AuthenticationResult

    /** Formato fuera de política; no consume ningún intento. */
    data object InvalidInput : AuthenticationResult
    data class IncorrectPin(val attemptsLeftInWindow: Int) : AuthenticationResult

    /** Espera activa: no se ejecuta ninguna KDF. */
    data class Cooldown(val remainingMs: Long) : AuthenticationResult

    /**
     * Resultado sin sesión: la pantalla se abandonó, el dispositivo se bloqueó o el entorno ya no es
     * válido mientras corría la verificación. El intento puede quedar contabilizado.
     */
    data object Unavailable : AuthenticationResult
    data object StorageFailure : AuthenticationResult
    data object RecoveryRequired : AuthenticationResult
}

sealed interface MutationResult {
    data object Success : MutationResult
    data class InvalidInput(val reason: String = "") : MutationResult

    /** Falta una sesión vigente (PIN habilitado): nada se escribió. */
    data object AuthenticationRequired : MutationResult
    data object Unavailable : MutationResult
    data object StorageFailure : MutationResult
    data object RecoveryRequired : MutationResult
}
