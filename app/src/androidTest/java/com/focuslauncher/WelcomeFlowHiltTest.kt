package com.focuslauncher

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import android.content.Context
import com.focuslauncher.core.di.StorageModule
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.core.security.StateCipher
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.di.TestStores
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.focuslauncher.data.local.StateResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Instalación nueva (aviso sin aceptar) con la Activity real y un almacén aislado. */
@HiltAndroidTest
@UninstallModules(StorageModule::class)
class WelcomeFlowHiltTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FreshInstallStorage {
        @Provides @Singleton fun cipher(): StateCipher = TestStores.cipher()
        @Provides @Singleton fun location(@ApplicationContext context: Context): StateLocation = TestStores.location(context)
        @Provides @Singleton fun store(@ApplicationContext context: Context, location: StateLocation, cipher: StateCipher): FocusStateStore =
            TestStores.store(context, location, cipher as KeystoreStateCipher, accepted = false)
    }

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var store: FocusStateStore

    @Before fun setUp() = hiltRule.inject()

    private fun waitFor(text: String) = rule.waitUntil(15_000) {
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun aFreshInstallShowsTheNoticeBeforeAnyAppsOrConfiguration() {
        waitFor("Bienvenido a Focus Launcher")
        rule.onNode(hasText("Configuración de Focus")).assertDoesNotExist()
        rule.onNode(hasText("Todas las aplicaciones")).assertDoesNotExist()
        rule.onNode(hasText("Ocultar una aplicación solo cambia lo que se muestra en Focus.", substring = true)).assertExists()
    }

    @Test fun helpIsReachableFromTheNoticeAndBackReturnsToIt() {
        waitFor("Bienvenido a Focus Launcher")
        rule.onNodeWithText("Ayuda").performScrollTo().performClick()
        waitFor("El PIN olvidado no se puede recuperar.")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("Bienvenido a Focus Launcher")
    }

    @Test fun acceptingPersistsLeadsToHomeAndSurvivesRecreation() {
        waitFor("Bienvenido a Focus Launcher")
        rule.onNodeWithText("Entendido: probar Focus sin cambiar mi inicio").performScrollTo().performClick()
        waitFor("Configuración de Focus")
        val saved = (runBlocking { store.readCurrent() } as StateResult.Ready).state
        assertTrue("la aceptación debe estar persistida", saved.onboardingAccepted)

        rule.activityRule.scenario.recreate()
        waitFor("Configuración de Focus")
        rule.onNode(hasText("Bienvenido a Focus Launcher")).assertDoesNotExist()
        assertEquals(true, (runBlocking { store.readCurrent() } as StateResult.Ready).state.onboardingAccepted)
    }
}
