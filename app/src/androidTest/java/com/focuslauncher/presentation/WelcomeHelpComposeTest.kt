package com.focuslauncher.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.help.HelpScreen
import com.focuslauncher.presentation.help.HelpStatus
import com.focuslauncher.presentation.home.HomeScreen
import com.focuslauncher.presentation.home.HomeUiState
import com.focuslauncher.presentation.navigation.SystemSettingsTarget
import com.focuslauncher.presentation.onboarding.WelcomeScreen
import com.focuslauncher.core.home.HomeRoleStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeHelpComposeTest {
    @get:Rule val rule = createComposeRule()

    private fun welcome(saving: Boolean = false, error: Boolean = false, home: () -> Unit = {}, tryIt: () -> Unit = {}, help: () -> Unit = {}) {
        rule.setContent { MaterialTheme { WelcomeScreen(saving, error, home, tryIt, help) } }
    }

    private fun helpScreen(
        status: HelpStatus = HelpStatus.Healthy,
        unavailable: SystemSettingsTarget? = null,
        onOpen: (SystemSettingsTarget) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent { MaterialTheme { HelpScreen(status, unavailable, onOpen, onRetry, onBack = {}) } }
    }

    // ------------------------------------------------------------------------- Bienvenida
    @Test fun welcomeExplainsScopeNewAppsOptionalPinAndLocalProfileBeforeAnythingElse() {
        welcome()
        listOf(
            "Bienvenido a Focus Launcher",
            "Ocultar una aplicación solo cambia lo que se muestra en Focus.",
            "Las aplicaciones nuevas aparecen visibles por defecto",
            "El PIN es opcional",
            "Solo se gestionan las aplicaciones de este perfil personal.",
            "Focus no es un bloqueo parental",
        ).forEach { rule.onNode(hasText(it, substring = true)).assertExists() }
    }

    @Test fun bothAcceptActionsAreSeparateAndTheHomeRequestIsOptional() {
        var home = 0; var tryIt = 0
        welcome(home = { home++ }, tryIt = { tryIt++ })
        rule.onNodeWithText("Entendido: probar Focus sin cambiar mi inicio").performScrollTo().performClick()
        assertEquals(0 to 1, home to tryIt)
        rule.onNodeWithText("Entendido: usar Focus como pantalla de inicio").performScrollTo().performClick()
        assertEquals(1 to 1, home to tryIt)
    }

    @Test fun whileSavingTheActionsAreDisabledAndAnErrorIsShownWithoutAcceptingAnything() {
        welcome(saving = true, error = true)
        rule.onNodeWithText("No se pudo guardar. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithText("Entendido: probar Focus sin cambiar mi inicio").assertIsNotEnabled()
    }

    @Test fun welcomeIsOperableAtFontScale200WithBigTouchTargets() {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { MaterialTheme { WelcomeScreen(false, false, {}, {}, {}) } }
        }
        rule.onNodeWithText("Entendido: probar Focus sin cambiar mi inicio").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Ayuda").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    // ------------------------------------------------------------------------- Ayuda
    @Test fun helpKeepsTheScopeNoticeAvailable() {
        helpScreen()
        rule.onNode(hasText("Ocultar una aplicación solo cambia lo que se muestra en Focus.", substring = true)).assertExists()
        rule.onNode(hasText("Espacio privado", substring = true)).assertExists()
    }

    @Test fun helpStatesThatAForgottenPinCannotBeRecoveredAndGivesUsableSteps() {
        helpScreen()
        rule.onNodeWithText("El PIN olvidado no se puede recuperar.").assertExists()
        rule.onNodeWithText("No existe un PIN maestro ni una pregunta secreta.").assertExists()
        rule.onNode(hasText("Cambia el launcher", substring = true)).assertExists()
        rule.onNode(hasText("Almacenamiento → Borrar datos", substring = true)).assertExists()
        rule.onNode(hasText("se pierden las reglas de visibilidad", substring = true)).assertExists()
    }

    @Test fun androidSettingsExitsAreAlwaysThereAndReportTheirTarget() {
        val opened = mutableListOf<SystemSettingsTarget>()
        helpScreen(onOpen = { opened += it })
        rule.onNodeWithText("Abrir ajustes de pantalla de inicio").performScrollTo().performClick()
        rule.onNodeWithText("Abrir información de la app de Focus").performScrollTo().performClick()
        rule.onNodeWithText("Abrir Ajustes de Android").performScrollTo().performClick()
        assertEquals(SystemSettingsTarget.entries.toList(), opened)
    }

    @Test fun whenTheSystemHasNoActivityManualInstructionsAreShown() {
        helpScreen(unavailable = SystemSettingsTarget.AppDetails)
        rule.onNode(hasText("Este dispositivo no pudo abrir esa pantalla.", substring = true)).assertExists()
        rule.onNode(hasText("Almacenamiento → Borrar datos", substring = true) and hasText("Ábrela a mano", substring = true)).assertExists()
    }

    @Test fun recoveryRequiredExplainsTheCauseAndOffersNoInternalReset() {
        helpScreen(status = HelpStatus.RecoveryRequired(RecoveryReason.KeyLost))
        rule.onNodeWithText("La configuración de Focus necesita recuperación").assertExists()
        rule.onNodeWithText("La clave que protege la configuración ya no está disponible.").assertExists()
        // Ningún control de la pantalla restablece nada: restablecer exige sesión en Configuración → Seguridad.
        rule.onAllNodes(hasClickAction() and hasText("Restablecer", substring = true)).assertCountEquals(0)
        rule.onAllNodes(hasClickAction() and hasText("Reintentar")).assertCountEquals(0) // recuperar no se arregla reintentando
    }

    @Test fun aTemporaryReadErrorCanBeRetried() {
        var retries = 0
        helpScreen(status = HelpStatus.ReadError(ReadErrorKind.StorageUnavailable), onRetry = { retries++ })
        rule.onNodeWithText("Reintentar").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun helpIsOperableAtFontScale200() {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                MaterialTheme { HelpScreen(HelpStatus.Healthy, null, {}, {}, {}) }
            }
        }
        rule.onNodeWithText("Abrir Ajustes de Android").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Volver al inicio").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    // ------------------------------------------------------------------------- Inicio con error
    private fun home(content: AppsContent, onRetry: () -> Unit = {}) {
        rule.setContent {
            MaterialTheme {
                HomeScreen(
                    state = HomeUiState(content), roleStatus = HomeRoleStatus.Held, homeSettingsUnavailable = false,
                    onAppClick = {}, onOpenDrawer = {}, onOpenSettings = {}, onOpenHelp = {}, onRequestHomeRole = {},
                    onNoticeShown = {}, onRetry = onRetry,
                )
            }
        }
    }

    @Test fun homeOffersRetryOnTemporaryErrorsButNotOnRecovery() {
        var retries = 0
        home(AppsContent.Blocked(BlockedReason.SettingsReadError(ReadErrorKind.KeystoreUnavailable)), onRetry = { retries++ })
        rule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test fun homeNeverOffersRetryNorResetWhenRecoveryIsRequired() {
        home(AppsContent.Blocked(BlockedReason.RecoveryRequired(RecoveryReason.Corrupt)))
        rule.onAllNodes(hasText("Reintentar")).assertCountEquals(0)
        rule.onAllNodes(hasClickAction() and hasText("Restablecer", substring = true)).assertCountEquals(0)
        rule.onNodeWithText("Ayuda").assertIsDisplayed()
    }
}
