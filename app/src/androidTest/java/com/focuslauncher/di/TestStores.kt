package com.focuslauncher.di

import android.content.Context
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Piezas de un almacén aislado para pruebas con Hilt. Cada componente de prueba crea las suyas
 * (alias y archivo únicos): nunca se toca el estado real de la app ni dos DataStores comparten archivo.
 */
object TestStores {
    fun cipher() = KeystoreStateCipher("focus_state_test_" + UUID.randomUUID().toString().replace("-", ""))

    fun location(context: Context) =
        StateLocation(File(File(context.cacheDir, "hilt-" + UUID.randomUUID()), "focus_state.pb.enc"))

    fun store(context: Context, location: StateLocation, cipher: KeystoreStateCipher, accepted: Boolean): FocusStateStore =
        FocusStateStore(location, cipher, CoroutineScope(SupervisorJob() + Dispatchers.IO), context.packageName).also { store ->
            if (accepted) runBlocking { store.update { it.toBuilder().setOnboardingAccepted(true).build() } }
        }
}
