package com.focuslauncher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprobaciones contra el sistema real (sin mocks de RoleManager). */
class HomeEntryPointTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pm = context.packageManager

    @Test
    fun focusIsRegisteredAsHomeCandidate() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val candidates = pm.queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(0))
        assertTrue(candidates.any { it.activityInfo.packageName == "com.focuslauncher" })
    }

    @Test
    fun focusAlsoHasSeparateLauncherEntry() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.focuslauncher")
        val entries = pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        assertEquals(1, entries.size)
    }

    @Test
    fun homeRoleIsAvailableAndRequestIntentIsCreatable() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        assertTrue(roleManager.isRoleAvailable(RoleManager.ROLE_HOME))
        if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            assertNotNull(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }
}
