package com.focuslauncher.docs

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.focuslauncher.core.ui.FocusTheme
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
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Genera las imágenes de OPERACION.md para las pantallas protegidas (Configuración, Seguridad y PIN), que
 * bloquean las capturas del sistema (FLAG_SECURE). Renderiza los MISMOS composables de la app con el tema
 * del dispositivo y datos de ejemplo. Solo se ejecuta con `-e docScreenshots true`; no forma parte de la suite.
 *
 * Salida: `files/docs/<nombre>.png` del paquete de la app (se extrae con `adb exec-out run-as ... cat`).
 */
class DocScreenshotsTest {
    @get:Rule val rule = createComposeRule()

    private val enabled get() = InstrumentationRegistry.getArguments().getString("docScreenshots") == "true"
    private val outDir by lazy { File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "docs").also { it.mkdirs() } }

    private val apps = listOf(
        "Calendar", "Camera", "Chrome", "Clock", "Contacts", "Drive", "Files", "Gmail", "Google",
        "Maps", "Messages", "Phone", "Photos", "Safety", "Settings", "YouTube", "YT Music",
    )

    /** Paquetes reales de las apps del emulador de referencia (así las capturas coinciden con lo que se ve en pantalla). */
    private val packages = mapOf(
        "Calendar" to "com.google.android.calendar", "Camera" to "com.android.camera2", "Chrome" to "com.android.chrome",
        "Clock" to "com.google.android.deskclock", "Contacts" to "com.google.android.contacts", "Drive" to "com.google.android.apps.docs",
        "Files" to "com.google.android.documentsui", "Gmail" to "com.google.android.gm", "Google" to "com.google.android.googlequicksearchbox",
        "Maps" to "com.google.android.apps.maps", "Messages" to "com.google.android.apps.messaging", "Phone" to "com.google.android.dialer",
        "Photos" to "com.google.android.apps.photos", "Safety" to "com.google.android.apps.safetyhub", "Settings" to "com.android.settings",
        "YouTube" to "com.google.android.youtube", "YT Music" to "com.google.android.apps.youtube.music",
    )

    private fun pkg(name: String, hidden: Boolean) = ManageablePackage(
        key = AppPackageKey(packages.getValue(name), UserSerial(0)),
        title = name,
        activityLabels = if (name == "Google") listOf("Google", "Voice Search") else listOf(name),
        hidden = hidden,
        installed = true,
    )

    private fun editor(hiddenExcept: Set<String>?, indicator: SaveIndicator) = SettingsUiState(
        EditorContent.Ready(
            apps.map { pkg(it, hidden = hiddenExcept != null && it !in hiddenExcept) },
            catalogError = null, saving = emptySet(), failed = emptyMap(),
        ),
        indicator,
    )

    private fun capture(name: String, content: @Composable () -> Unit, interact: () -> Unit = {}) {
        rule.setContent {
            FocusTheme(darkTheme = false, dynamicColor = true) {
                Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().padding(top = 40.dp)) { content() } }
            }
        }
        interact()
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun editorAllVisible() {
        assumeTrue(enabled)
        capture("10_editor_todo_visible", {
            SettingsScreen(editor(null, SaveIndicator.Idle), { _, _ -> }, {}, {}, {})
        })
    }

    @Test fun editorShortList() {
        assumeTrue(enabled)
        capture("11_editor_lista_corta", {
            SettingsScreen(editor(setOf("Camera", "Chrome", "Messages", "Phone"), SaveIndicator.Saved), { _, _ -> }, {}, {}, {})
        })
    }

    @Test fun securityCreate() {
        assumeTrue(enabled)
        capture("12_seguridad_crear", {
            SecurityScreen(SecurityUiState(pinEnabled = false), { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
        }, interact = {
            rule.onNodeWithText("PIN nuevo").performTextInput("482915")
            rule.onNodeWithText("Confirmar PIN").performTextInput("482915")
        })
    }

    @Test fun securityEnabled() {
        assumeTrue(enabled)
        capture("13_seguridad_activa", {
            SecurityScreen(SecurityUiState(pinEnabled = true), { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
        })
    }

    @Test fun pinGate() {
        assumeTrue(enabled)
        capture("14_puerta_pin", { PinGateScreen(AdminGate.NeedsPin(), {}, {}, {}) })
    }

    @Test fun pinWrong() {
        assumeTrue(enabled)
        capture("15_pin_incorrecto", {
            PinGateScreen(AdminGate.NeedsPin(feedback = PinFeedback(PinFeedbackKind.Incorrect, attemptsLeft = 3)), {}, {}, {})
        })
    }

    @Test fun pinCooldown() {
        assumeTrue(enabled)
        capture("16_pin_espera", {
            PinGateScreen(AdminGate.NeedsPin(feedback = PinFeedback(PinFeedbackKind.Cooldown), cooldownSeconds = 27), {}, {}, {})
        })
    }
}
