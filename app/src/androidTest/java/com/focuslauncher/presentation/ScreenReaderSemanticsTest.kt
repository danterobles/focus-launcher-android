package com.focuslauncher.presentation

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.material3.MaterialTheme
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.ManageablePackage
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.presentation.admin.AdminGate
import com.focuslauncher.presentation.admin.PinGateScreen
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.help.HelpScreen
import com.focuslauncher.presentation.help.HelpStatus
import com.focuslauncher.presentation.home.HomeScreen
import com.focuslauncher.presentation.home.HomeUiState
import com.focuslauncher.presentation.onboarding.WelcomeScreen
import com.focuslauncher.presentation.security.SecurityScreen
import com.focuslauncher.presentation.security.SecurityUiState
import com.focuslauncher.presentation.settings.EditorContent
import com.focuslauncher.presentation.settings.SettingsScreen
import com.focuslauncher.presentation.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Lo que un lector de pantalla recibe: el árbol de semántica FUSIONADO (lo que TalkBack recorre),
 * su orden, encabezados, roles y estados. Complementa a TalkBack real, que no está en el emulador.
 */
class ScreenReaderSemanticsTest {
    @get:Rule val rule = createComposeRule()

    private fun heading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    /** Anuncio aproximado de un nodo: texto/descripción + estado + rol. */
    private fun SemanticsNode.announcement(): List<String> = buildList {
        config.getOrNull(SemanticsProperties.ContentDescription)?.let { addAll(it) }
        config.getOrNull(SemanticsProperties.Text)?.let { addAll(it.map { t -> t.text }) }
        config.getOrNull(SemanticsProperties.StateDescription)?.let { add(it) }
        config.getOrNull(SemanticsProperties.Role)?.let { add("[${it}]") }
    }

    /** Nodos anunciables en orden de recorrido (los hijos fusionados no se repiten). */
    private fun traversal(): List<String> =
        rule.onAllNodes(hasText("", substring = true) or hasContentDescription("", substring = true)).fetchSemanticsNodes()
            .flatMap { it.announcement() }

    private fun app(n: Int) = InstalledApp(AppId("p.$n", "p.$n.Main", UserSerial(0)), "App %02d".format(n), true)

    private fun assertFirstHeading(title: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { MaterialTheme { content() } }
        val first = rule.onAllNodes(heading()).fetchSemanticsNodes().first()
        assertEquals(title, first.config.getOrNull(SemanticsProperties.Text)?.first()?.text)
    }

    @Test fun homeStartsWithItsTitleAsTheFirstHeading() =
        assertFirstHeading("Inicio") { HomeScreen(HomeUiState(AppsContent.Empty), HomeRoleStatus.Held, false, {}, {}, {}, {}, {}, {}) }

    @Test fun settingsStartsWithItsTitleAsTheFirstHeading() =
        assertFirstHeading("Configuración de Focus") { SettingsScreen(SettingsUiState(EditorContent.Locked), { _, _ -> }, {}, {}, {}) }

    @Test fun pinGateStartsWithItsTitleAsTheFirstHeading() =
        assertFirstHeading("Ingresa el PIN") { PinGateScreen(AdminGate.NeedsPin(), {}, {}, {}) }

    @Test fun securityStartsWithItsTitleAsTheFirstHeading() =
        assertFirstHeading("Seguridad") { SecurityScreen(SecurityUiState(pinEnabled = false), { _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }

    @Test fun helpStartsWithItsTitleAsTheFirstHeading() =
        assertFirstHeading("Ayuda") { HelpScreen(HelpStatus.Healthy, null, {}, {}, {}) }

    @Test fun welcomeStartsWithItsTitleAsTheFirstHeading() =
        assertFirstHeading("Bienvenido a Focus Launcher") { WelcomeScreen(false, false, {}, {}, {}) }

    @Test fun focusOrderFollowsTheVisualOrderOnThePinScreen() {
        rule.setContent { MaterialTheme { PinGateScreen(AdminGate.NeedsPin(), {}, {}, {}) } }
        val order = traversal()
        val idx = { s: String -> order.indexOfFirst { it == s } }
        val msg = "orden de recorrido: $order"
        assertTrue(msg, idx("Ingresa el PIN") in 0 until idx("PIN"))
        assertTrue(msg, idx("PIN") in 0 until idx("Desbloquear"))
        assertTrue(msg, idx("Desbloquear") in 0 until idx("Volver al inicio"))
    }

    @Test fun anAppTileIsAnnouncedAsAButtonWithItsLabelOnly() {
        rule.setContent {
            MaterialTheme { HomeScreen(HomeUiState(AppsContent.Apps(listOf(app(1)), 1)), HomeRoleStatus.Held, false, {}, {}, {}, {}, {}, {}) }
        }
        val node = rule.onNode(hasContentDescription("App 01")).fetchSemanticsNode()
        assertEquals(listOf("App 01"), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
    }

    @Test fun aSettingsRowIsASwitchThatAnnouncesItsStateExactlyOnce() {
        val key = AppPackageKey("cam", UserSerial(0))
        rule.setContent {
            MaterialTheme {
                SettingsScreen(
                    SettingsUiState(EditorContent.Ready(listOf(ManageablePackage(key, "Cámara", listOf("Cámara"), hidden = true, installed = true)), null, emptySet(), emptyMap())),
                    { _, _ -> }, {}, {}, {},
                )
            }
        }
        val row = rule.onNode(SemanticsMatcher("fila Cámara") { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }).fetchSemanticsNode()
        val spoken = row.announcement()
        assertEquals("el estado se anuncia una sola vez: $spoken", 1, spoken.count { it == "Oculta" })
        assertTrue(spoken.contains("Cámara"))
        assertEquals("[${Role.Switch}]", spoken.last())
    }

    @Test fun progressIndicatorsAreDescribedForScreenReaders() {
        rule.setContent { MaterialTheme { HomeScreen(HomeUiState(AppsContent.Loading), HomeRoleStatus.Held, false, {}, {}, {}, {}, {}, {}) } }
        rule.onNode(hasContentDescription("Cargando")).assertExists()
    }

    @Test fun errorsAndCountdownsAreLiveRegions() {
        rule.setContent {
            MaterialTheme {
                PinGateScreen(
                    AdminGate.NeedsPin(feedback = com.focuslauncher.presentation.admin.PinFeedback(com.focuslauncher.presentation.admin.PinFeedbackKind.Incorrect, 2)),
                    {}, {}, {},
                )
            }
        }
        val live = rule.onNodeWithText("PIN incorrecto. Quedan 2 intentos antes de una espera.").fetchSemanticsNode()
        assertTrue(live.config.getOrNull(SemanticsProperties.LiveRegion) != null)
    }
}
