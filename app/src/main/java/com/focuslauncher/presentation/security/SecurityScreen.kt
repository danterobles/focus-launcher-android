package com.focuslauncher.presentation.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.core.security.PinPolicy
import com.focuslauncher.core.ui.PinField

private enum class Confirm { DisablePin, Reset }

@Composable
fun SecurityScreen(
    state: SecurityUiState,
    onEnablePin: (CharArray, CharArray) -> Unit,
    onChangePin: (CharArray, CharArray) -> Unit,
    onDisablePin: () -> Unit,
    onReset: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // PIN y confirmación: estado local no guardable (no sobreviven a rotación ni a morir el proceso).
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Confirm?>(null) }
    val enabled = state.pinEnabled == true
    val valid = pin.length in PinPolicy.MIN_LENGTH..PinPolicy.MAX_LENGTH && pin == confirmation
    val mismatch = confirmation.isNotEmpty() && pin != confirmation

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.screen_security), style = MaterialTheme.typography.headlineMedium)
        if (state.pinEnabled == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }

        Text(stringResource(if (enabled) R.string.security_pin_on else R.string.security_pin_off))
        Text(stringResource(R.string.security_pin_note), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(if (enabled) R.string.security_change_title else R.string.security_create_title), style = MaterialTheme.typography.titleMedium)
        PinField(pin, { pin = it; onDismissError() }, stringResource(R.string.pin_new_label), Modifier.fillMaxWidth(), enabled = !state.busy, imeAction = ImeAction.Next)
        PinField(confirmation, { confirmation = it; onDismissError() }, stringResource(R.string.pin_confirm_label), Modifier.fillMaxWidth(), enabled = !state.busy)
        Text(stringResource(R.string.pin_requirements), style = MaterialTheme.typography.bodySmall)
        if (mismatch) Text(stringResource(R.string.pin_mismatch), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        state.error?.let {
            Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (state.slow) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.pin_saving), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(
            onClick = {
                val p = pin.toCharArray(); val c = confirmation.toCharArray()
                pin = ""; confirmation = ""
                if (enabled) onChangePin(p, c) else onEnablePin(p, c)
            },
            enabled = valid && !state.busy,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(if (enabled) R.string.security_change_action else R.string.security_create_action)) }

        if (enabled) OutlinedButton(onClick = { pending = Confirm.DisablePin }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.security_disable_action))
        }
        Text(stringResource(R.string.security_reset_title), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { pending = Confirm.Reset }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.security_reset_action))
        }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back_settings)) }
    }

    pending?.let { which ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(if (which == Confirm.DisablePin) R.string.confirm_disable_title else R.string.confirm_reset_title)) },
            text = { Text(stringResource(if (which == Confirm.DisablePin) R.string.confirm_disable_body else R.string.confirm_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = { pending = null; if (which == Confirm.DisablePin) onDisablePin() else onReset() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(if (which == Confirm.DisablePin) R.string.confirm_disable_action else R.string.confirm_reset_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun SecurityError.messageRes(): Int = when (this) {
    SecurityError.InvalidPin -> R.string.pin_invalid
    SecurityError.SessionExpired -> R.string.save_failed_session
    SecurityError.Unavailable -> R.string.save_failed_unavailable
    SecurityError.StorageFailure -> R.string.save_failed_storage
    SecurityError.RecoveryRequired -> R.string.state_recovery_body
}
