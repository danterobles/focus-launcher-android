package com.focuslauncher.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.core.home.HomeRoleStatus
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.BlockedReason
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.common.AppsStateBlock
import com.focuslauncher.presentation.common.AppsSurface
import com.focuslauncher.presentation.common.CONSTRAINED_HEIGHT
import com.focuslauncher.presentation.common.GRID_COLUMNS
import com.focuslauncher.presentation.common.NoticeText
import com.focuslauncher.presentation.common.appItems
import com.focuslauncher.presentation.navigation.UiMessage

/**
 * Inicio: primeras 8 entradas visibles y acceso PERMANENTE a Configuración y Ayuda, aunque no haya
 * apps, la carga falle o la política esté en recuperación. Con alto suficiente los accesos quedan
 * fijos abajo; con poco alto (horizontal, fuente grande) todo se desplaza junto para que ninguna
 * acción quede recortada ni la lista quede comprimida.
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
    onRetry: () -> Unit = {},
    message: UiMessage? = null,
    onMessageShown: () -> Unit = {},
) {
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            kotlinx.coroutines.delay(4_000)
            onNoticeShown()
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(5_000)
            onMessageShown()
        }
    }

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.screen_home), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            message?.let {
                Text(
                    stringResource(
                        when (it) {
                            UiMessage.PinEnabled -> R.string.message_pin_enabled
                            UiMessage.PinChanged -> R.string.message_pin_changed
                            UiMessage.PinDisabled -> R.string.message_pin_disabled
                        },
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (roleStatus != HomeRoleStatus.Held) {
                Text(
                    stringResource(
                        if (roleStatus == HomeRoleStatus.NotHeld) R.string.role_status_not_held else R.string.role_status_unavailable,
                    ),
                )
                Button(onClick = onRequestHomeRole, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_request_home)) }
                if (homeSettingsUnavailable) Text(stringResource(R.string.home_settings_unavailable))
            }
        }
    }
    val footer: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Un error temporal (no el de recuperación) se puede reintentar; recuperar no se arregla reintentando.
            val blocked = (state.content as? AppsContent.Blocked)?.reason
            if (blocked is BlockedReason.SettingsReadError || blocked is BlockedReason.CatalogFailure) {
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
            }
            NoticeText(state.notice)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenDrawer, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_drawer)) }
                TextButton(onClick = onOpenDrawer, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_search)) }
                TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_settings)) }
                TextButton(onClick = onOpenHelp, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_help)) }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxHeight < CONSTRAINED_HEIGHT) {
            // Pantalla entera desplazable: encabezado, apps y accesos en una sola cuadrícula.
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
                if (state.content is AppsContent.Apps) appItems(state.content, onAppClick)
                else item(span = { GridItemSpan(maxLineSpan) }) { AppsStateBlock(state.content) }
                item(span = { GridItemSpan(maxLineSpan) }) { footer() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header()
                AppsSurface(state.content, onAppClick, Modifier.weight(1f))
                footer()
            }
        }
    }
}
