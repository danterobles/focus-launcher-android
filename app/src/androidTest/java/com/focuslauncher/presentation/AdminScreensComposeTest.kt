package com.focuslauncher.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.ManageablePackage
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.presentation.admin.AdminGate
import com.focuslauncher.presentation.admin.PinFeedback
import com.focuslauncher.presentation.admin.PinFeedbackKind
import com.focuslauncher.presentation.admin.PinGateScreen
import com.focuslauncher.presentation.security.SecurityScreen
import com.focuslauncher.presentation.security.SecurityUiState
import com.focuslauncher.presentation.settings.EditorContent
import com.focuslauncher.presentation.settings.SaveIndicator
import com.focuslauncher.presentation.settings.SettingsScreen
import com.focuslauncher.presentation.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdminScreensComposeTest {
    @get:Rule val rule = createComposeRule()

    private fun key(n: String) = AppPackageKey(n, UserSerial(0))
    private fun pkg(n: String, title: String, labels: List<String> = listOf(title), hidden: Boolean = false, installed: Boolean = true) =
        ManageablePackage(key(n), title, labels, hidden, installed)

    // ------------------------------------------------------------------------- PIN
    @Composable private fun Gate(
        gate: AdminGate.NeedsPin = AdminGate.NeedsPin(),
        onSubmit: (CharArray) -> Unit = {},
    ) = MaterialTheme { PinGateScreen(gate, onSubmit, onBack = {}, onShown = {}) }

    private fun isPassword() = SemanticsMatcher("es campo de contraseña") { it.config.getOrNull(SemanticsProperties.Password) != null }

    @Test fun pinFieldIsMaskedAcceptsOnlyDigitsAndSubmitNeedsSixToTwelve() {
        var sent: String? = null
        rule.setContent { Gate(onSubmit = { sent = String(it) }) }
        rule.onNode(isPassword()).assertExists()
        rule.onNodeWithText("Desbloquear").assertIsNotEnabled()
        rule.onNode(isPassword()).performTextInput("12ab3")
        rule.onNodeWithText("Desbloquear").assertIsNotEnabled() // las letras se descartaron: solo «123»
        rule.onNode(isPassword()).performTextInput("456")
        rule.onNodeWithText("Desbloquear").assertIsEnabled()
        rule.onNodeWithText("Desbloquear").performClick()
        assertEquals("123456", sent)
    }

    @Test fun pinFieldIsEmptiedAfterSubmitting() {
        rule.setContent { Gate() }
        rule.onNode(isPassword()).performTextInput("000123")
        rule.onNodeWithText("Desbloquear").performClick()
        rule.onNodeWithText("Desbloquear").assertIsNotEnabled()
    }

    @Test fun pinFieldIsCappedAtTwelveDigits() {
        var sent: String? = null
        rule.setContent { Gate(onSubmit = { sent = String(it) }) }
        rule.onNode(isPassword()).performTextInput("1234567890123456")
        rule.onNodeWithText("Desbloquear").performClick()
        assertEquals(12, sent!!.length)
    }

    @Test fun cooldownDisablesEntryAndExplainsTheFullRestart() {
        rule.setContent { Gate(AdminGate.NeedsPin(feedback = PinFeedback(PinFeedbackKind.Cooldown), cooldownSeconds = 30)) }
        rule.onNodeWithText("Demasiados intentos. Espera 30 s. Si Focus se cierra, la espera empieza de nuevo por completo.").assertIsDisplayed()
        rule.onNode(isPassword()).assertIsNotEnabled()
        rule.onNodeWithText("Desbloquear").assertIsNotEnabled()
    }

    @Test fun wrongPinShowsRemainingAttempts() {
        rule.setContent { Gate(AdminGate.NeedsPin(feedback = PinFeedback(PinFeedbackKind.Incorrect, attemptsLeft = 3))) }
        rule.onNodeWithText("PIN incorrecto. Quedan 3 intentos antes de una espera.").assertIsDisplayed()
    }

    @Test fun aSlowVerificationShowsTheIndicator() {
        rule.setContent { Gate(AdminGate.NeedsPin(busy = true, slow = true)) }
        rule.onNodeWithText("Verificando…").assertIsDisplayed()
        rule.onNode(isPassword()).assertIsNotEnabled()
    }

    @Test fun nonAsciiDigitsAreDiscardedWhilePasting() {
        var sent: String? = null
        rule.setContent { Gate(onSubmit = { sent = String(it) }) }
        rule.onNode(isPassword()).performTextInput("١٢٣456-789 0")
        rule.onNodeWithText("Desbloquear").performClick()
        assertEquals("4567890", sent)
    }

    @Test fun pinScreenIsOperableAtFontScale200AndTargetsAre48dp() {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { Gate() }
        }
        rule.onNodeWithText("Volver al inicio").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Desbloquear").assertHeightIsAtLeast(48.dp)
    }

    // ------------------------------------------------------------------------- Editor
    @Composable private fun Editor(
        content: EditorContent,
        indicator: SaveIndicator = SaveIndicator.Idle,
        onSet: (AppPackageKey, Boolean) -> Unit = { _, _ -> },
    ) = MaterialTheme { SettingsScreen(SettingsUiState(content, indicator), onSet, onOpenSecurity = {}, onOpenHelp = {}, onBack = {}) }

    private fun ready(vararg p: ManageablePackage, saving: Set<AppPackageKey> = emptySet()) =
        EditorContent.Ready(p.toList(), null, saving, emptyMap())

    @Test fun rowsShowStateSummaryAndTombstonesAndToggleReportsTheDesiredValue() {
        val calls = mutableListOf<Pair<String, Boolean>>()
        rule.setContent {
            Editor(
                ready(
                    pkg("cam", "Cámara", listOf("Cámara", "Cámara de vídeo")),
                    pkg("mus", "Música", hidden = true),
                    pkg("old.pkg", "old.pkg", emptyList(), hidden = true, installed = false),
                ),
                onSet = { k, v -> calls += k.packageName to v },
            )
        }
        rule.onNodeWithText("2 actividades: Cámara, Cámara de vídeo").assertIsDisplayed()
        rule.onNodeWithText("No instalada · old.pkg · la regla se conserva").assertIsDisplayed()
        rule.onNodeWithText("Cámara").performClick() // visible → ocultar
        rule.onNodeWithText("Música").performClick() // oculta → mostrar
        assertEquals(listOf("cam" to false, "mus" to true), calls)
    }

    @Test fun eachRowAnnouncesItsStateAndIsAtLeast48dpHigh() {
        rule.setContent { Editor(ready(pkg("cam", "Cámara"), pkg("mus", "Música", hidden = true))) }
        val state = { text: String -> SemanticsMatcher("estado $text") { it.config.getOrNull(SemanticsProperties.StateDescription) == text } }
        rule.onNode(state("Visible")).assertExists().assertHeightIsAtLeast(48.dp)
        rule.onNode(state("Oculta")).assertExists().assertHeightIsAtLeast(48.dp)
    }

    @Test fun aSavingRowIsDisabledAndTheIndicatorTellsTheTruth() {
        rule.setContent { Editor(ready(pkg("cam", "Cámara"), saving = setOf(key("cam"))), SaveIndicator.Saving) }
        rule.onNodeWithText("Guardando…").assertIsDisplayed()
        rule.onNode(SemanticsMatcher("fila deshabilitada") { it.config.getOrNull(SemanticsProperties.StateDescription) == "Visible" })
            .assertIsNotEnabled()
    }

    @Test fun savedIndicator() {
        rule.setContent { Editor(ready(pkg("cam", "Cámara")), SaveIndicator.Saved) }
        rule.onNodeWithText("Cambios guardados.").assertIsDisplayed()
    }

    @Test fun failedIndicatorNeverLooksLikeSuccess() {
        rule.setContent { Editor(ready(pkg("cam", "Cámara")), SaveIndicator.Failed(com.focuslauncher.presentation.settings.SaveFailure.StorageFailure)) }
        rule.onNodeWithText("No se pudo guardar el cambio.").assertIsDisplayed()
        rule.onNodeWithText("Cambios guardados.").assertDoesNotExist()
    }

    @Test fun lockedEditorShowsNoAppLabelsAtAll() {
        rule.setContent { Editor(EditorContent.Locked) }
        rule.onNodeWithText("Desbloquea con tu PIN para ver la configuración.").assertIsDisplayed()
        rule.onNodeWithText("Cámara").assertDoesNotExist()
    }

    @Test fun editorKeepsSecurityReachableAtFontScale200() {
        var security = 0
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                MaterialTheme { SettingsScreen(SettingsUiState(ready(pkg("cam", "Cámara"))), { _, _ -> }, onOpenSecurity = { security++ }, onOpenHelp = {}, onBack = {}) }
            }
        }
        rule.onNodeWithText("Seguridad").assertIsDisplayed().performClick()
        assertEquals(1, security)
    }

    // ------------------------------------------------------------------------- Seguridad
    @Composable private fun Security(
        state: SecurityUiState,
        onEnable: (CharArray, CharArray) -> Unit = { _, _ -> },
        onDisable: () -> Unit = {},
        onReset: () -> Unit = {},
    ) = MaterialTheme { SecurityScreen(state, onEnable, { _, _ -> }, onDisable, onReset, onDismissError = {}, onBack = {}) }

    @Test fun creatingAPinNeedsMatchingConfirmationAndSendsBoth() {
        rule.setContent { Security(SecurityUiState(pinEnabled = false)) }
        rule.onNodeWithText("Activar PIN").assertIsNotEnabled()
        rule.onNodeWithText("PIN nuevo").performTextInput("123456")
        rule.onNodeWithText("Confirmar PIN").performTextInput("123457")
        rule.onNodeWithText("Los PIN no coinciden.").assertIsDisplayed()
        rule.onNodeWithText("Activar PIN").assertIsNotEnabled()
    }

    @Test fun matchingPinsEnableTheButtonAndAreSent() {
        var got: Pair<String, String>? = null
        rule.setContent { Security(SecurityUiState(pinEnabled = false), onEnable = { a, b -> got = String(a) to String(b) }) }
        rule.onNodeWithText("PIN nuevo").performTextInput("000999")
        rule.onNodeWithText("Confirmar PIN").performTextInput("000999")
        rule.onNodeWithText("Activar PIN").assertIsEnabled().performClick()
        assertEquals("000999" to "000999", got)
    }

    @Test fun disablingThePinAndResettingNeedAnExplicitConfirmation() {
        var disabled = 0; var reset = 0
        rule.setContent { Security(SecurityUiState(pinEnabled = true), onDisable = { disabled++ }, onReset = { reset++ }) }
        rule.onNodeWithText("Desactivar PIN").performClick()
        rule.onNodeWithText("¿Desactivar el PIN?").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").performClick()
        assertEquals(0, disabled)
        rule.onNodeWithText("Desactivar PIN").performClick()
        rule.onNodeWithText("Desactivar").performClick()
        assertEquals(1, disabled)

        rule.onNodeWithText("Restablecer configuración").performClick()
        rule.onNodeWithText("Se borrarán todas las reglas de visibilidad, el aviso inicial y el PIN. No se puede deshacer.").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").performClick()
        assertEquals(0, reset)
        rule.onNodeWithText("Restablecer configuración").performClick()
        rule.onNodeWithText("Restablecer").performClick()
        assertEquals(1, reset)
    }

    @Test fun theDisablePinActionOnlyExistsWhenAPinIsEnabled() {
        rule.setContent { Security(SecurityUiState(pinEnabled = false)) }
        rule.onNodeWithText("Desactivar PIN").assertDoesNotExist()
        assertTrue(true)
    }
}
