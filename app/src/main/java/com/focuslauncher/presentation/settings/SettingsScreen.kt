package com.focuslauncher.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.domain.model.ManageablePackage
import com.focuslauncher.presentation.common.LoadingIndicator

/**
 * Editor de visibilidad. Cada cambio se guarda al instante; el interruptor refleja el valor
 * CONFIRMADO, nunca uno optimista, y se muestra el estado de guardado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSetVisible: (AppPackageKey, Boolean) -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenHelp: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Una sola lista desplazable: título, indicador y accesos van dentro, de modo que con poco alto
    // (horizontal, fuente grande) nada se comprime ni queda recortado.
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.screen_settings),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            )
        }
        item { SaveIndicatorText(state.indicator) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenSecurity, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_security)) }
                TextButton(onClick = onOpenHelp, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_help)) }
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back_home)) }
            }
        }
        when (val content = state.content) {
            EditorContent.Loading -> item { LoadingIndicator(Modifier.fillMaxWidth().padding(24.dp)) }
            EditorContent.Locked -> item { Text(stringResource(R.string.settings_locked)) }
            is EditorContent.Blocked -> item {
                Text(
                    stringResource(
                        if (content.reason is BlockedReason.RecoveryRequired) R.string.state_recovery_body else R.string.state_read_error_body,
                    ),
                )
            }
            is EditorContent.Ready -> {
                item { Text(stringResource(R.string.settings_scope_note), style = MaterialTheme.typography.bodySmall) }
                if (content.catalogError != null) item { Text(stringResource(R.string.state_catalog_error_body), color = MaterialTheme.colorScheme.error) }
                if (content.packages.isEmpty() && content.catalogError == null) item { Text(stringResource(R.string.settings_no_apps)) }
                items(content.packages, key = { "${it.key.userSerial.value}/${it.key.packageName}" }) { pkg ->
                    PackageRow(pkg, saving = pkg.key in content.saving, failed = content.failed[pkg.key], onSetVisible)
                }
            }
        }
    }
}

@Composable
private fun PackageRow(
    pkg: ManageablePackage,
    saving: Boolean,
    failed: SaveFailure?,
    onSetVisible: (AppPackageKey, Boolean) -> Unit,
) {
    val visible = !pkg.hidden
    val stateText = stringResource(if (visible) R.string.state_visible else R.string.state_hidden)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = visible, enabled = !saving, role = Role.Switch) { onSetVisible(pkg.key, it) }
                .semantics { stateDescription = stateText },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(pkg.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        !pkg.installed -> stringResource(R.string.row_not_installed, pkg.key.packageName)
                        pkg.activityLabels.size > 1 -> pluralStringResource(R.plurals.row_multiple_activities, pkg.activityLabels.size, pkg.activityLabels.size, pkg.activityLabels.joinToString(", "))
                        else -> pkg.key.packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (saving) {
                val savingDescription = stringResource(R.string.save_saving)
                androidx.compose.material3.CircularProgressIndicator(Modifier.padding(end = 8.dp).semantics { contentDescription = savingDescription })
            } else {
                // Solo visual: el estado ya se anuncia con stateDescription; así no se lee dos veces.
                Text(stateText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clearAndSetSemantics { })
            }
            Switch(checked = visible, onCheckedChange = null, enabled = !saving)
        }
        if (failed != null) Text(stringResource(failed.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SaveIndicatorText(indicator: SaveIndicator) {
    val text = when (indicator) {
        SaveIndicator.Idle -> return
        SaveIndicator.Saving -> stringResource(R.string.save_saving)
        SaveIndicator.Saved -> stringResource(R.string.save_saved)
        is SaveIndicator.Failed -> stringResource(indicator.reason.messageRes())
    }
    Text(
        text,
        color = if (indicator is SaveIndicator.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun SaveFailure.messageRes(): Int = when (this) {
    SaveFailure.SessionExpired -> R.string.save_failed_session
    SaveFailure.StorageFailure -> R.string.save_failed_storage
    SaveFailure.Unavailable -> R.string.save_failed_unavailable
    SaveFailure.RecoveryRequired -> R.string.state_recovery_body
    SaveFailure.Rejected -> R.string.save_failed_rejected
}
