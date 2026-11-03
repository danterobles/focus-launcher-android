package com.focuslauncher.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Garantiza que `AccessibilityChecksTest` no da falsa seguridad: el motor SÍ falla ante una infracción real. */
class AccessibilityChecksGuardTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aTinyUnlabelledClickableIsRejected() {
        rule.enableAccessibilityChecks()
        rule.setContent { Box(Modifier.size(20.dp).background(Color.LightGray).clickable(onClick = {})) }
        val failed = runCatching { rule.onRoot().tryPerformAccessibilityChecks() }.exceptionOrNull()
        assertTrue("las comprobaciones deberían haber fallado (objetivo de 20 dp sin etiqueta)", failed != null)
    }
}
