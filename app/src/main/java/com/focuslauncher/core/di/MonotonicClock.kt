package com.focuslauncher.core.di

import android.os.SystemClock

/** Reloj monotónico inyectable (esperas y sesión administrativa; nunca hora de pared). */
fun interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

class SystemMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
