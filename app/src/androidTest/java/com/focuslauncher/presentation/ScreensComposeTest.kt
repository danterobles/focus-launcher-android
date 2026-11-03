package com.focuslauncher.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.drawer.DrawerScreen
import com.focuslauncher.presentation.drawer.DrawerUiState
import com.focuslauncher.presentation.home.HomeScreen
import com.focuslauncher.presentation.home.HomeUiState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreensComposeTest {
    @get:Rule val rule = createComposeRule()

    private fun app(n: Int, label: String = "App %02d".format(n)) =
        InstalledApp(AppId("p.$n", "p.$n.Main", UserSerial(0)), label, true)

    private val eight = (1..8).map { app(it) }

    @Composable
    private fun HomeUnderTest(
        content: AppsContent,
        role: HomeRoleStatus = HomeRoleStatus.Held,
        onApp: (AppId) -> Unit = {},
        onSettings: () -> Unit = {},
        onHelp: () -> Unit = {},
    ) = MaterialTheme {
        HomeScreen(
            state = HomeUiState(content), roleStatus = role, homeSettingsUnavailable = false,
            onAppClick = onApp, onOpenDrawer = {}, onOpenSettings = onSettings, onOpenHelp = onHelp,
            onRequestHomeRole = {}, onNoticeShown = {},
        )
    }

    @Test fun homeShowsTheEightEntriesAndTheyAreClickable() {
        var opened: AppId? = null
        rule.setContent { HomeUnderTest(AppsContent.Apps(eight, total = 20), onApp = { opened = it }) }
        eight.forEach { rule.onNodeWithContentDescription(it.label).assertIsDisplayed() }
        rule.onNodeWithContentDescription("App 03").performClick()
        assertEquals(eight[2].id, opened)
    }

    @Test fun loadingShowsNoApps() {
        rule.setContent { HomeUnderTest(AppsContent.Loading) }
        rule.onNodeWithContentDescription("App 01").assertDoesNotExist()
        rule.onNodeWithText("Configuración de Focus").assertIsDisplayed()
    }

    @Test fun settingsAndHelpStayReachableWhenThereAreNoVisibleApps() {
        var settings = 0; var help = 0
        rule.setContent { HomeUnderTest(AppsContent.Empty, onSettings = { settings++ }, onHelp = { help++ }) }
        rule.onNodeWithText("No hay aplicaciones visibles").assertIsDisplayed()
        rule.onNodeWithText("Configuración de Focus").performClick()
        rule.onNodeWithText("Ayuda").performClick()
        assertTrue(settings == 1 && help == 1)
    }

    @Test fun recoveryAndReadErrorShowNoAppsButKeepSettingsAndHelp() {
        rule.setContent { HomeUnderTest(AppsContent.Blocked(BlockedReason.RecoveryRequired(RecoveryReason.KeyLost))) }
        rule.onNodeWithText("La configuración necesita recuperación").assertIsDisplayed()
        rule.onNodeWithContentDescription("App 01").assertDoesNotExist()
        rule.onNodeWithText("Configuración de Focus").assertIsDisplayed()
        rule.onNodeWithText("Ayuda").assertIsDisplayed()
    }

    @Test fun touchTargetsAreAtLeast48dp() {
        rule.setContent { HomeUnderTest(AppsContent.Apps(eight, 8)) }
        rule.onNodeWithContentDescription("App 01").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Configuración de Focus").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Ayuda").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Todas las aplicaciones").assertHeightIsAtLeast(48.dp)
    }

    @Test fun settingsStaysOperableWithFontScale200() {
        var settings = 0
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                HomeUnderTest(AppsContent.Apps(eight, 8), onSettings = { settings++ })
            }
        }
        rule.onNodeWithText("Configuración de Focus").assertIsDisplayed().performClick()
        assertEquals(1, settings)
    }

    @Test fun roleBannerOffersAnExplicitRequestWhenFocusIsNotHome() {
        rule.setContent { HomeUnderTest(AppsContent.Empty, role = HomeRoleStatus.NotHeld) }
        rule.onNodeWithText("Usar Focus como pantalla de inicio").assertIsDisplayed()
    }

    @Test fun drawerShowsSearchFieldReportsTypingAndNoResults() {
        var typed = ""
        rule.setContent {
            MaterialTheme {
                DrawerScreen(
                    state = DrawerUiState(content = AppsContent.NoResults), onQueryChange = { typed = it },
                    onAppClick = {}, onBack = {}, onNoticeShown = {}, onLeave = {},
                )
            }
        }
        rule.onNodeWithText("Sin resultados").assertIsDisplayed()
        rule.onNodeWithText("Buscar aplicación").performTextInput("cam")
        assertEquals("cam", typed)
    }

    @Test fun drawerListsEntriesAndClearsQueryOnLeave() {
        var left = false
        var shown by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                if (shown) DrawerScreen(
                    state = DrawerUiState(content = AppsContent.Apps(eight, 8)), onQueryChange = {},
                    onAppClick = {}, onBack = {}, onNoticeShown = {}, onLeave = { left = true },
                )
            }
        }
        rule.onNodeWithContentDescription("App 08").assertIsDisplayed()
        shown = false
        rule.waitForIdle()
        assertTrue(left)
    }
}
