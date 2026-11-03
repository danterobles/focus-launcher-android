package com.focuslauncher.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslauncher.R
import com.focuslauncher.domain.model.InstalledApp

/** Carga de iconos disponible para los Composables sin acceder a APIs de Android directamente. */
val LocalAppIconLoader = compositionLocalOf<AppIconLoader?> { null }

private val ICON_SIZE = 48.dp

@Composable
fun AppTile(app: InstalledApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val loader = LocalAppIconLoader.current
    val sizePx = with(LocalDensity.current) { ICON_SIZE.roundToPx() }
    // La carga ocurre en el dispositivo de IO del loader, nunca en la composición.
    val icon: ImageBitmap? by produceState<ImageBitmap?>(null, app.id, sizePx, loader) {
        value = loader?.load(app.id, sizePx)?.asImageBitmap()
    }
    val openLabel = stringResource(R.string.action_open_app, app.label)
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
            .padding(4.dp)
            .semantics(mergeDescendants = true) { contentDescription = app.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val bitmap = icon
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
        else Spacer(Modifier.size(ICON_SIZE))
        Text(
            text = app.label,
            // Con fuente grande las palabras largas se separan con guion en lugar de partirse a ciegas.
            style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
