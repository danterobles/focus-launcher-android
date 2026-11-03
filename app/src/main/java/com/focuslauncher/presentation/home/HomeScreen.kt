package com.focuslauncher.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.presentation.common.AppsSurface
import com.focuslauncher.presentation.common.NoticeText

/**
 * Inicio: primeras 8 entradas visibles y acceso PERMANENTE a Configuración y Ayuda, aunque no haya
 * apps, la carga falle o la política esté en recuperación.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    roleStatus: HomeRoleStatus,
    homeSettingsUnavailable: Boolean,
    onAppClick: (AppId) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onRequestHomeRole: () -> Unit,
    onNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            kotlinx.coroutines.delay(4_000)
            onNoticeShown()
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.screen_home), style = MaterialTheme.typography.headlineMedium)
        if (roleStatus != HomeRoleStatus.Held) {
            Text(
                stringResource(
                    if (roleStatus == HomeRoleStatus.NotHeld) R.string.role_status_not_held
                    else R.string.role_status_unavailable,
                ),
            )
            Button(onClick = onRequestHomeRole, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_request_home)) }
            if (homeSettingsUnavailable) Text(stringResource(R.string.home_settings_unavailable))
        }
        AppsSurface(state.content, onAppClick, Modifier.weight(1f))
        NoticeText(state.notice)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenDrawer, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_drawer)) }
            TextButton(onClick = onOpenDrawer, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_search)) }
            TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_settings)) }
            TextButton(onClick = onOpenHelp, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_help)) }
        }
    }
}

