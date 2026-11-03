package com.focuslauncher.presentation.onboarding

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

/** Aviso de alcance que se muestra antes de configurar nada (RF-01). Sigue disponible en Ayuda. */
@Composable
fun WelcomeScreen(
    saving: Boolean,
    error: Boolean,
    onAcceptAndUseAsHome: () -> Unit,
    onAcceptAndTry: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        ScopeNotice()
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) {
            Text(
                stringResource(R.string.welcome_save_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(onClick = onAcceptAndUseAsHome, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.welcome_accept_home))
        }
        OutlinedButton(onClick = onAcceptAndTry, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.welcome_accept_try))
        }
        TextButton(onClick = onOpenHelp, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_help)) }
    }
}

/** Texto del aviso de alcance (compartido por la Bienvenida y la Ayuda para que nunca diverjan). */
@Composable
fun ScopeNotice(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NoticeItem(R.string.notice_scope_what)
        NoticeItem(R.string.notice_scope_hiding)
        NoticeItem(R.string.notice_scope_new_apps)
        NoticeItem(R.string.notice_scope_pin)
        NoticeItem(R.string.notice_scope_profile)
        NoticeItem(R.string.notice_scope_not_parental)
        NoticeItem(R.string.notice_scope_offline)
    }
}

@Composable
private fun NoticeItem(res: Int) {
    Text("•  " + stringResource(res), style = MaterialTheme.typography.bodyLarge)
}
