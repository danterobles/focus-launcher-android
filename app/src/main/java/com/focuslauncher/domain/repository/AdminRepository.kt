package com.focuslauncher.domain.repository

import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.AuthenticationResult
import com.focuslauncher.domain.model.MutationResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Toda operación administrativa. La autorización la aplica internamente el coordinador (sesión
 * opaca en memoria): no existe ningún parámetro tipo `isAdmin` ni token que un llamador pueda forjar.
 */
interface AdminRepository {
    /** `true` mientras haya una sesión administrativa vigente. Nunca se persiste. */
    val isAuthorized: StateFlow<Boolean>

    /** El llamador cede la propiedad de [pin]: se borra al terminar. */
    suspend fun verifyPin(pin: CharArray): AuthenticationResult

    /** Revoca la sesión de inmediato, sin esperar a ninguna verificación en curso. */
    fun lock()

    /** Espera restante (0 si no hay). Tras un proceso nuevo la espera activa se reaplica completa. */
    suspend fun cooldownRemainingMs(): Long

    suspend fun setPackageHidden(key: AppPackageKey, hidden: Boolean): MutationResult
    suspend fun enablePin(pin: CharArray, confirmation: CharArray): MutationResult
    suspend fun changePin(pin: CharArray, confirmation: CharArray): MutationResult
    suspend fun disablePin(): MutationResult
    suspend fun resetConfiguration(): MutationResult
}
