package com.focuslauncher.core.security

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.focuslauncher.core.di.MonotonicClock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado del dispositivo relevante para emitir/mantener una sesión. */
interface AdminEnvironment {
    fun isScreenInteractive(): Boolean
    fun isDeviceLocked(): Boolean
}

class AndroidAdminEnvironment(private val context: Context) : AdminEnvironment {
    override fun isScreenInteractive(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: false

    override fun isDeviceLocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked ?: true
}

/**
 * Sesión administrativa única, solo en memoria y con reloj monotónico. Se revoca al salir de
 * administración, recibir Home, pasar a segundo plano, apagar/bloquear la pantalla o expirar (120 s).
 * Rotar la pantalla no la renueva; morir el proceso la destruye. Nunca se serializa.
 *
 * [revoke] no toma ningún lock: es inmediata aunque una KDF esté en curso. La "generación" permite
 * que una verificación que termina tarde compruebe que sigue vigente antes de emitir sesión.
 */
class AdminSessionManager(
    private val clock: MonotonicClock,
    private val environment: AdminEnvironment,
    private val scope: CoroutineScope? = null,
) {
    private class Active(val issuedAt: Long)

    private val generation = AtomicInteger(0)
    private val lock = Any()
    @Volatile private var active: Active? = null
    private val _authorized = MutableStateFlow(false)

    val authorized: StateFlow<Boolean> = _authorized.asStateFlow()

    /** Generación actual: se captura al iniciar una verificación. */
    fun generation(): Int = generation.get()

    /**
     * Emite la sesión solo si la generación no cambió y el entorno sigue válido (pantalla activa y
     * dispositivo desbloqueado). Devuelve `false` sin efectos en caso contrario.
     */
    fun issue(expectedGeneration: Int): Boolean {
        val session: Active
        synchronized(lock) {
            if (generation.get() != expectedGeneration) return false
            if (!environment.isScreenInteractive() || environment.isDeviceLocked()) return false
            session = Active(clock.elapsedRealtimeMillis())
            active = session
            _authorized.value = true
        }
        scope?.launch {
            delay(TTL_MS)
            expire(session)
        }
        return true
    }

    /** `true` si hay sesión vigente: no revocada, dentro del plazo y con el entorno válido. */
    fun isActive(): Boolean {
        val session = active ?: return false
        if (clock.elapsedRealtimeMillis() - session.issuedAt >= TTL_MS) {
            expire(session)
            return false
        }
        if (!environment.isScreenInteractive() || environment.isDeviceLocked()) {
            revoke()
            return false
        }
        return true
    }

    /** Revocación inmediata (salida de administración, Home, segundo plano, pantalla apagada…). */
    fun revoke() {
        synchronized(lock) {
            generation.incrementAndGet()
            active = null
            _authorized.value = false
        }
    }

    private fun expire(session: Active) {
        synchronized(lock) {
            if (active === session) {
                generation.incrementAndGet()
                active = null
                _authorized.value = false
            }
        }
    }

    companion object {
        const val TTL_MS = 120_000L
    }
}
