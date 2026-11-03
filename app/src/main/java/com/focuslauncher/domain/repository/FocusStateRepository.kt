package com.focuslauncher.domain.repository

import com.focuslauncher.domain.model.SettingsReadState
import kotlinx.coroutines.flow.Flow

interface FocusStateRepository {
    /** Proyección sin verificador. Emite `Loading` antes de cualquier valor y nunca defaults ante errores. */
    val settings: Flow<SettingsReadState>

    /** Vuelve a intentar tras un `ReadError`. */
    fun retry()
}
