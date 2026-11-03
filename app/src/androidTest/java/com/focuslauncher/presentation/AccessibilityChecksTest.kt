package com.focuslauncher.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.core.ui.FocusTheme
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.InstalledApp
import com.focuslauncher.domain.model.ManageablePackage
import com.focuslauncher.domain.model.ReadErrorKind
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.presentation.admin.AdminBlockedScreen
import com.focuslauncher.presentation.admin.AdminGate
import com.focuslauncher.presentation.admin.PinFeedback
import com.focuslauncher.presentation.admin.PinFeedbackKind
import com.focuslauncher.presentation.admin.PinGateScreen
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.drawer.DrawerScreen
import com.focuslauncher.presentation.drawer.DrawerUiState
import com.focuslauncher.presentation.help.HelpScreen
import com.focuslauncher.presentation.help.HelpStatus
import com.focuslauncher.presentation.home.HomeScreen
import com.focuslauncher.presentation.home.HomeUiState
import com.focuslauncher.presentation.navigation.SystemSettingsTarget
import com.focuslauncher.presentation.navigation.UiMessage
import com.focuslauncher.presentation.onboarding.WelcomeScreen
import com.focuslauncher.presentation.security.SecurityScreen
import com.focuslauncher.presentation.security.SecurityUiState
import com.focuslauncher.presentation.settings.EditorContent
import com.focuslauncher.presentation.settings.SaveFailure
import com.focuslauncher.presentation.settings.SaveIndicator
import com.focuslauncher.presentation.settings.SettingsScreen
import com.focuslauncher.presentation.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Accessibility Test Framework (el mismo motor de las comprobaciones de Accessibility Scanner) sobre
 * CADA pantalla, en tema claro, oscuro y dinámico del dispositivo: tamaño de objetivos táctiles,
 * contraste de texto e imágenes, etiquetas, elementos duplicados, etc. Un error de nivel ERROR falla
 * la prueba. No sustituye una sesión real con TalkBack.
 */
@RunWith(Parameterized::class)
class AccessibilityChecksTest(private val dark: Boolean, private val dynamic: Boolean) {

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "dark={0} dinámico={1}")
        fun themes() = listOf(arrayOf<Any>(false, false), arrayOf<Any>(true, false), arrayOf<Any>(false, true), arrayOf<Any>(true, true))
    }

    @get:Rule val rule = createComposeRule()

    private fun app(n: Int) = InstalledApp(AppId("p.$n", "p.$n.Main", UserSerial(0)), "App %02d".format(n), true)
    private fun pkg(n: String, title: String, labels: List<String> = listOf(title), hidden: Boolean = false, installed: Boolean = true) =
        ManageablePackage(AppPackageKey(n, UserSerial(0)), title, labels, hidden, installed)

    private fun check(content: @Composable () -> Unit) {
        rule.enableAccessibilityChecks()
        rule.setContent { FocusTheme(darkTheme = dark, dynamicColor = dynamic) { content() } }
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    private val eight = AppsContent.Apps((1..8).map(::app), 8)

    @Composable private fun Home(c: AppsContent, role: HomeRoleStatus = HomeRoleStatus.NotHeld, msg: UiMessage? = null) = HomeScreen(
        HomeUiState(c), role, homeSettingsUnavailable = true, onAppClick = {}, onOpenDrawer = {}, onOpenSettings = {},
        onOpenHelp = {}, onRequestHomeRole = {}, onNoticeShown = {}, message = msg,
    )

    @Test fun homeWithApps() = check { Home(eight, msg = UiMessage.PinEnabled) }
    @Test fun homeLoading() = check { Home(AppsContent.Loading, HomeRoleStatus.Held) }
    @Test fun homeEmpty() = check { Home(AppsContent.Empty, HomeRoleStatus.Held) }
    @Test fun homeRecovery() = check { Home(AppsContent.Blocked(BlockedReason.RecoveryRequired(RecoveryReason.KeyLost)), HomeRoleStatus.Held) }
    @Test fun homeReadError() = check { Home(AppsContent.Blocked(BlockedReason.SettingsReadError(ReadErrorKind.StorageUnavailable)), HomeRoleStatus.Held) }

    @Test fun drawer() = check { DrawerScreen(DrawerUiState("ap", eight), {}, {}, {}, {}, {}) }
    @Test fun drawerNoResults() = check { DrawerScreen(DrawerUiState("zzz", AppsContent.NoResults), {}, {}, {}, {}, {}) }

    @Test fun settingsReady() = check {
        SettingsScreen(
            SettingsUiState(
                EditorContent.Ready(
                    listOf(pkg("cam", "Cámara", listOf("Cámara", "Cámara de vídeo")), pkg("mus", "Música", hidden = true), pkg("old", "old", emptyList(), true, false)),
                    null, saving = setOf(AppPackageKey("mus", UserSerial(0))), failed = emptyMap(),
                ),
                SaveIndicator.Failed(SaveFailure.StorageFailure),
            ),
            { _, _ -> }, {}, {}, {},
        )
    }
    @Test fun settingsLocked() = check { SettingsScreen(SettingsUiState(EditorContent.Locked), { _, _ -> }, {}, {}, {}) }

    @Test fun pinGate() = check { PinGateScreen(AdminGate.NeedsPin(feedback = PinFeedback(PinFeedbackKind.Incorrect, 3)), {}, {}, {}) }
    @Test fun pinGateCooldown() = check {
        PinGateScreen(AdminGate.NeedsPin(feedback = PinFeedback(PinFeedbackKind.Cooldown), cooldownSeconds = 20), {}, {}, {})
    }
    @Test fun pinGateBusy() = check { PinGateScreen(AdminGate.NeedsPin(busy = true, slow = true), {}, {}, {}) }
    @Test fun adminBlocked() = check { AdminBlockedScreen(AdminGate.Blocked(null, RecoveryReason.Corrupt), {}, {}, {}) }

    @Test fun securityCreate() = check { SecurityScreen(SecurityUiState(pinEnabled = false), { _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
    @Test fun securityEnabled() = check { SecurityScreen(SecurityUiState(pinEnabled = true), { _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }

    @Test fun welcome() = check { WelcomeScreen(saving = false, error = true, {}, {}, {}) }
    @Test fun help() = check { HelpScreen(HelpStatus.Healthy, SystemSettingsTarget.AppDetails, {}, {}, {}) }
    @Test fun helpRecovery() = check { HelpScreen(HelpStatus.RecoveryRequired(RecoveryReason.KeyLost), null, {}, {}, {}) }
}
