package com.focuslauncher

import android.view.WindowManager
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Ciclo de vida real de la Activity: FLAG_SECURE y expulsión de administración al pasar a segundo plano. */
@HiltAndroidTest
class MainActivityAdminLifecycleTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() = hiltRule.inject()

    private fun secure(): Boolean {
        var flag = false
        rule.activityRule.scenario.onActivity { flag = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
        return flag
    }

    @Test fun administrativeScreensAreSecureAndLeavingThemClearsIt() {
        assertFalse(secure())
        rule.onNodeWithText("Configuración de Focus").performClick()
        rule.waitForIdle()
        assertTrue(secure())
        rule.activityRule.scenario.onActivity { activity ->
            // Volver desde administración quita la protección cuando el contenido ya se reemplazó.
            activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        assertFalse(secure())
    }

    @Test fun goingToBackgroundWhileAdministeringReturnsToHomeAndKeepsTheFlagUntilVisibleAgain() {
        rule.onNodeWithText("Configuración de Focus").performClick()
        rule.waitForIdle()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // onStop: segundo plano
        // Mientras no es visible, la protección se conserva (la miniatura de Recientes sale protegida).
        assertTrue(secure())
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        rule.onNodeWithText("Inicio").assertExists() // volvió a Inicio, no a Configuración
        assertFalse(secure())
    }

    @Test fun rotationDoesNotKickYouOutOfAdministration() {
        rule.onNodeWithText("Configuración de Focus").performClick()
        rule.waitForIdle()
        rule.activityRule.scenario.recreate() // cambio de configuración: onStop con isChangingConfigurations
        rule.waitForIdle()
        assertEquals(true, secure())
    }
}
