package com.focuslauncher.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.core.ui.FocusTheme
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.ManageablePackage
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.drawer.DrawerScreen
import com.focuslauncher.presentation.drawer.DrawerUiState
import com.focuslauncher.presentation.home.HomeScreen
import com.focuslauncher.presentation.home.HomeUiState
import com.focuslauncher.presentation.settings.EditorContent
import com.focuslauncher.presentation.settings.SettingsScreen
import com.focuslauncher.presentation.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Poco alto útil (pantalla horizontal, fuente al 200 %, teclado abierto): nada queda recortado ni
 * inalcanzable; todo se puede desplazar hasta y se puede operar. Se fuerza con un contenedor de 300 dp.
 */
class SmallHeightLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun app(n: Int) = InstalledApp(AppId("p.$n", "p.$n.Main", UserSerial(0)), "App %02d".format(n), true)
    private val eight = AppsContent.Apps((1..8).map(::app), 8)

    /** Desplaza el contenedor (lista perezosa) hasta que el nodo exista y quede a la vista. */
    private fun scrollTo(matcher: SemanticsMatcher) = rule.onNode(hasScrollAction()).performScrollToNode(matcher)
    private fun text(t: String) = hasText(t)
    private fun desc(t: String) = hasContentDescription(t)

    private fun small(fontScale: Float = 2f, content: @Composable () -> Unit) = rule.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = fontScale)) {
            FocusTheme(dynamicColor = false) { Box(Modifier.fillMaxWidth().height(300.dp)) { content() } }
        }
    }

    @Test fun homeEveryAppAndEveryActionIsReachableWithLittleHeight() {
        var settings = 0; var opened: AppId? = null
        small {
            HomeScreen(
                HomeUiState(eight), HomeRoleStatus.NotHeld, false, onAppClick = { opened = it }, onOpenDrawer = {},
                onOpenSettings = { settings++ }, onOpenHelp = {}, onRequestHomeRole = {}, onNoticeShown = {},
            )
        }
        scrollTo(desc("App 08")); rule.onNodeWithContentDescription("App 08").assertIsDisplayed().performClick()
        assertEquals(app(8).id, opened)
        scrollTo(text("Configuración de Focus")); rule.onNodeWithText("Configuración de Focus").assertIsDisplayed().performClick()
        assertEquals(1, settings)
        scrollTo(text("Ayuda")); rule.onNodeWithText("Ayuda").assertIsDisplayed()
        scrollTo(text("Usar Focus como pantalla de inicio")); rule.onNodeWithText("Usar Focus como pantalla de inicio").assertIsDisplayed()
    }

    @Test fun homeWithNoAppsStillShowsSettingsAndHelpWithLittleHeight() {
        small {
            HomeScreen(HomeUiState(AppsContent.Empty), HomeRoleStatus.Held, false, {}, {}, {}, {}, {}, {})
        }
        scrollTo(text("No hay aplicaciones visibles")); rule.onNodeWithText("No hay aplicaciones visibles").assertIsDisplayed()
        scrollTo(text("Configuración de Focus")); rule.onNodeWithText("Configuración de Focus").assertIsDisplayed()
        scrollTo(text("Ayuda")); rule.onNodeWithText("Ayuda").assertIsDisplayed()
    }

    @Test fun drawerKeepsTheSearchFieldAndEveryAppReachableWithLittleHeight() {
        var typed = ""
        small { DrawerScreen(DrawerUiState("", eight), onQueryChange = { typed = it }, onAppClick = {}, onBack = {}, onNoticeShown = {}, onLeave = {}) }
        scrollTo(text("Buscar aplicación")); rule.onNodeWithText("Buscar aplicación").assertIsDisplayed()
        scrollTo(desc("App 08")); rule.onNodeWithContentDescription("App 08").assertIsDisplayed()
        scrollTo(text("Volver al inicio")); rule.onNodeWithText("Volver al inicio").assertIsDisplayed().performClick()
        assertEquals("", typed)
    }

    @Test fun settingsKeepsTheEditorAndItsActionsReachableWithLittleHeight() {
        val rows = (1..12).map { ManageablePackage(AppPackageKey("p$it", UserSerial(0)), "Paquete $it", listOf("Paquete $it"), it % 2 == 0, true) }
        var security = 0
        small {
            SettingsScreen(
                SettingsUiState(EditorContent.Ready(rows, null, emptySet(), emptyMap())), { _, _ -> },
                onOpenSecurity = { security++ }, onOpenHelp = {}, onBack = {},
            )
        }
        scrollTo(text("Seguridad")); rule.onNodeWithText("Seguridad").assertIsDisplayed().performClick()
        assertEquals(1, security)
        scrollTo(text("Paquete 12")); rule.onNodeWithText("Paquete 12").assertIsDisplayed()
    }
}
