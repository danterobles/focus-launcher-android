package com.focuslauncher.core.ui

import android.graphics.Bitmap
import com.focuslauncher.domain.model.AppId

/** Carga de iconos fuera del hilo principal. Siempre devuelve un bitmap (icono de reserva si falla). */
interface AppIconLoader {
    suspend fun load(id: AppId, sizePx: Int): Bitmap
}
