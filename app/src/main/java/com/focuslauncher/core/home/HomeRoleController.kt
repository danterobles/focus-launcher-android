package com.focuslauncher.core.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Estado del rol Home para este launcher. */
enum class HomeRoleStatus {
    /** Focus es el launcher Home actual. */
    Held,

    /** El rol existe y Focus no lo tiene; se puede solicitar. */
    NotHeld,

    /** El sistema no ofrece el rol (o no pudo consultarse). */
    Unavailable,
}

interface HomeRoleController {
    /** Consulta bloqueante (Binder): llamar fuera del hilo principal. */
    fun status(): HomeRoleStatus

    /** Intent oficial de solicitud, o null si el rol no puede solicitarse. */
    fun createRequestIntent(): Intent?
}

@Singleton
class RoleManagerHomeRoleController @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomeRoleController {

    private val roleManager: RoleManager? get() = context.getSystemService(RoleManager::class.java)

    override fun status(): HomeRoleStatus {
        val manager = roleManager ?: return HomeRoleStatus.Unavailable
        return when {
            !manager.isRoleAvailable(RoleManager.ROLE_HOME) -> HomeRoleStatus.Unavailable
            manager.isRoleHeld(RoleManager.ROLE_HOME) -> HomeRoleStatus.Held
            else -> HomeRoleStatus.NotHeld
        }
    }

    override fun createRequestIntent(): Intent? {
        val manager = roleManager ?: return null
        if (!manager.isRoleAvailable(RoleManager.ROLE_HOME) || manager.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }
}
