package com.focuslauncher.presentation.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.presentation.common.AppsContent
import com.focuslauncher.presentation.common.AppsStateBlock
import com.focuslauncher.presentation.common.AppsSurface
import com.focuslauncher.presentation.common.CONSTRAINED_HEIGHT
import com.focuslauncher.presentation.common.GRID_COLUMNS
import com.focuslauncher.presentation.common.NoticeText
import com.focuslauncher.presentation.common.appItems

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
    val title: @Composable () -> Unit = {
        Text(stringResource(R.string.screen_drawer), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
    val search: @Composable () -> Unit = {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
        )
    }
    val back: @Composable () -> Unit = {
        Column {
            NoticeText(state.notice)
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back_home)) }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxHeight < CONSTRAINED_HEIGHT) {
            // Con poco alto (horizontal, fuente grande, teclado) todo se desplaza junto.
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { title() }
                item(span = { GridItemSpan(maxLineSpan) }) { search() }
                if (state.content is AppsContent.Apps) appItems(state.content, onAppClick)
                else item(span = { GridItemSpan(maxLineSpan) }) { AppsStateBlock(state.content) }
                item(span = { GridItemSpan(maxLineSpan) }) { back() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                title()
                search()
                AppsSurface(state.content, onAppClick, Modifier.weight(1f))
                back()
            }
        }
    }
}
