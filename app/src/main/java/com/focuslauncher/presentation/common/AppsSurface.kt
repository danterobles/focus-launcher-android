package com.focuslauncher.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.core.ui.AppTile
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.BlockedReason

const val GRID_COLUMNS = 4

/** Cuadrícula fija de 4 columnas o, si no hay apps que mostrar, un estado explícito. */
@Composable
fun AppsSurface(
    content: AppsContent,
    onAppClick: (AppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content) {
        AppsContent.Loading -> Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is AppsContent.Apps -> LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(content.apps, key = { "${it.id.userSerial.value}/${it.id.packageName}/${it.id.activityName}" }) { app ->
                AppTile(app, onClick = { onAppClick(app.id) })
            }
        }
        AppsContent.Empty -> StateMessage(R.string.state_empty_title, R.string.state_empty_body, modifier)
        AppsContent.NoResults -> StateMessage(R.string.state_no_results_title, R.string.state_no_results_body, modifier)
        is AppsContent.Blocked -> when (val reason = content.reason) {
            is BlockedReason.SettingsReadError -> StateMessage(R.string.state_read_error_title, R.string.state_read_error_body, modifier)
            is BlockedReason.RecoveryRequired -> StateMessage(R.string.state_recovery_title, R.string.state_recovery_body, modifier)
            is BlockedReason.CatalogFailure -> StateMessage(R.string.state_catalog_error_title, R.string.state_catalog_error_body, modifier)
        }
    }
}

@Composable
fun StateMessage(title: Int, body: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun NoticeText(notice: Notice?, modifier: Modifier = Modifier) {
    if (notice == null) return
    Text(
        stringResource(
            when (notice) {
                Notice.AppUnavailable -> R.string.notice_app_unavailable
                Notice.LaunchFailed -> R.string.notice_launch_failed
                Notice.SettingsUnavailable -> R.string.notice_settings_unavailable
            },
        ),
        modifier = modifier.padding(8.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
