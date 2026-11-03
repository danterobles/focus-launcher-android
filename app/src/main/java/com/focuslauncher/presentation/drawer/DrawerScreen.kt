package com.focuslauncher.presentation.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.presentation.common.AppsSurface
import com.focuslauncher.presentation.common.NoticeText

/** Cajón: todas las entradas visibles y búsqueda sobre ese mismo conjunto. */
@Composable
fun DrawerScreen(
    state: DrawerUiState,
    onQueryChange: (String) -> Unit,
    onAppClick: (AppId) -> Unit,
    onBack: () -> Unit,
    onNoticeShown: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La consulta no sobrevive a la salida del cajón.
    DisposableEffect(Unit) { onDispose(onLeave) }
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            kotlinx.coroutines.delay(4_000)
            onNoticeShown()
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.screen_drawer), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
        )
        AppsSurface(state.content, onAppClick, Modifier.weight(1f))
        NoticeText(state.notice)
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back_home)) }
    }
}
