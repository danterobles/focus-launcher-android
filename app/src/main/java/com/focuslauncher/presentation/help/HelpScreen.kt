package com.focuslauncher.presentation.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.domain.model.RecoveryReason
import com.focuslauncher.presentation.navigation.SystemSettingsTarget
import com.focuslauncher.presentation.onboarding.ScopeNotice

/**
 * Ayuda PÚBLICA: nunca pide PIN y la salida a Ajustes de Android siempre está disponible (con o sin
 * PIN, con la configuración sana o en recuperación). No ofrece ningún restablecimiento interno:
 * restablecer exige sesión vigente en Configuración → Seguridad.
 */
@Composable
fun HelpScreen(
    status: HelpStatus,
    unavailableTarget: SystemSettingsTarget?,
    onOpenSystemSettings: (SystemSettingsTarget) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.screen_help), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

        when (status) {
            is HelpStatus.RecoveryRequired -> {
                Text(stringResource(R.string.help_status_recovery_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(stringResource(recoveryReasonRes(status.reason)))
                Text(stringResource(R.string.help_status_recovery_steps))
            }
            is HelpStatus.ReadError -> {
                Text(stringResource(R.string.help_status_error_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.help_status_error_body))
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
            }
            HelpStatus.Healthy, HelpStatus.Unknown -> Unit
        }

        Section(R.string.help_scope_title) { ScopeNotice() }

        Section(R.string.help_forgot_title) {
            Text(stringResource(R.string.help_forgot_cannot_recover))
            Text(stringResource(R.string.help_forgot_no_master))
            Text(stringResource(R.string.help_forgot_steps_intro), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.help_forgot_step_1))
            Text(stringResource(R.string.help_forgot_step_2))
            Text(stringResource(R.string.help_forgot_step_3))
            Text(stringResource(R.string.help_forgot_loses))
            Text(stringResource(R.string.help_reset_inside))
        }

        Section(R.string.help_android_title) {
            Text(stringResource(R.string.help_android_body))
            SettingsButton(R.string.help_open_home_settings, SystemSettingsTarget.HomeSettings, onOpenSystemSettings)
            SettingsButton(R.string.help_open_app_details, SystemSettingsTarget.AppDetails, onOpenSystemSettings)
            SettingsButton(R.string.help_open_android_settings, SystemSettingsTarget.AndroidSettings, onOpenSystemSettings)
            if (unavailableTarget != null) {
                // Alternativa cuando el sistema no tiene una Activity para ese ajuste.
                Text(
                    stringResource(R.string.help_settings_unavailable) + " " + stringResource(manualPathRes(unavailableTarget)),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back_home)) }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun SettingsButton(label: Int, target: SystemSettingsTarget, onClick: (SystemSettingsTarget) -> Unit) {
    OutlinedButton(onClick = { onClick(target) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) }
}

private fun recoveryReasonRes(reason: RecoveryReason): Int = when (reason) {
    RecoveryReason.KeyLost -> R.string.help_recovery_key_lost
    RecoveryReason.Corrupt, RecoveryReason.InvalidState -> R.string.help_recovery_corrupt
    RecoveryReason.FutureVersion -> R.string.help_recovery_future
    RecoveryReason.InterruptedInit -> R.string.help_recovery_interrupted
}

private fun manualPathRes(target: SystemSettingsTarget): Int = when (target) {
    SystemSettingsTarget.HomeSettings -> R.string.help_manual_home
    SystemSettingsTarget.AppDetails -> R.string.help_manual_app_details
    SystemSettingsTarget.AndroidSettings -> R.string.help_manual_settings
}
