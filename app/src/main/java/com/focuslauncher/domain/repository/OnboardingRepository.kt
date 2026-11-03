package com.focuslauncher.domain.repository

import com.focuslauncher.domain.model.MutationResult

/**
 * Aceptación del aviso de alcance (RF-01). No es una operación protegida: solo marca el aviso como
 * leído, no cambia ninguna regla ni credencial, y es idempotente. No se confirma sin persistir.
 */
interface OnboardingRepository {
    suspend fun acceptOnboarding(): MutationResult
}
