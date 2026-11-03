package com.focuslauncher.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.core.security.PinPolicy
import com.focuslauncher.core.ui.PinField

@Composable
fun PinGateScreen(
    gate: AdminGate.NeedsPin,
    onSubmit: (CharArray) -> Unit,
    onBack: () -> Unit,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Estado local no guardable: rotar o volver desde Recientes nunca restaura el PIN escrito.
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { onShown() }
    // Tras un intento fallido el campo se vacía.
    LaunchedEffect(gate.feedback) { if (gate.feedback != null) pin = "" }
    val waiting = gate.cooldownSeconds > 0
    val canSubmit = !gate.busy && !waiting && pin.length in PinPolicy.MIN_LENGTH..PinPolicy.MAX_LENGTH

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.pin_gate_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.pin_gate_body))
        PinField(
            value = pin,
            onValueChange = { pin = it },
            label = stringResource(R.string.pin_label),
            modifier = Modifier.fillMaxWidth(),
            enabled = !gate.busy && !waiting,
        )
        gate.feedback?.let { feedback ->
            Text(
                text = when (feedback.kind) {
                    PinFeedbackKind.Incorrect ->
                        if (feedback.attemptsLeft > 0)
                            pluralStringResource(R.plurals.pin_incorrect_with_attempts, feedback.attemptsLeft, feedback.attemptsLeft)
                        else stringResource(R.string.pin_incorrect)
                    PinFeedbackKind.Cooldown -> stringResource(R.string.pin_cooldown, gate.cooldownSeconds)
                    PinFeedbackKind.CouldNotVerify -> stringResource(R.string.pin_could_not_verify)
                    PinFeedbackKind.StorageFailure -> stringResource(R.string.pin_storage_failure)
                    PinFeedbackKind.RecoveryRequired -> stringResource(R.string.pin_recovery_required)
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (gate.slow) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.pin_verifying),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            onClick = { onSubmit(pin.toCharArray()); pin = "" },
            enabled = canSubmit,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.pin_submit)) }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_back_home))
        }
    }
}

/** La configuración no se puede leer: no se muestra nada protegido, solo cómo continuar. */
@Composable
fun AdminBlockedScreen(
    blocked: AdminGate.Blocked,
    onRetry: () -> Unit,
    onOpenHelp: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.screen_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        val recovery = blocked.recovery != null
        Text(stringResource(if (recovery) R.string.state_recovery_title else R.string.state_read_error_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (recovery) R.string.state_recovery_body else R.string.state_read_error_body))
        if (!recovery) Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
        Button(onClick = onOpenHelp, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_help)) }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back_home)) }
    }
}

