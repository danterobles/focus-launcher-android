package com.focuslauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.focuslauncher.core.ui.AppIconLoader
import com.focuslauncher.core.ui.LocalAppIconLoader
import com.focuslauncher.presentation.navigation.FocusApp
import com.focuslauncher.presentation.navigation.MainEvent
import com.focuslauncher.presentation.navigation.MainViewModel
import com.focuslauncher.presentation.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var iconLoader: AppIconLoader

    // El resultado aislado no prueba nada: se vuelve a consultar el rol, sea cual sea el resultado.
    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshRoleStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalAppIconLoader provides iconLoader) {
                    FocusApp(
                        state = state,
                        onNavigate = viewModel::navigateTo,
                        onBack = viewModel::navigateBack,
                        onRequestHomeRole = viewModel::onRequestHomeRole,
                    )
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
        // FLAG_SECURE en pantallas administrativas (capturas y miniatura de Recientes).
        lifecycleScope.launch {
            viewModel.state.map { it.screen }.distinctUntilChanged().collect { applySecureFlag(it) }
        }
        // Un Home recibido al crear la Activity ya arranca en la pantalla principal.
    }

    /**
     * Se activa al entrar en una pantalla administrativa y solo se retira cuando el contenido ya fue
     * reemplazado Y la Activity vuelve a estar visible: así la miniatura de Recientes, que se toma al
     * pasar a segundo plano, nunca incluye contenido sensible.
     */
    private fun applySecureFlag(
        screen: Screen,
        // En API 29+ el estado STARTED se notifica DESPUÉS de onStart(): por eso onStart pasa `true`.
        visible: Boolean = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
    ) {
        if (screen.administrative) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else if (visible) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        applySecureFlag(viewModel.state.value.screen, visible = true)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onActivityStopped()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshRoleStatus()
        viewModel.refreshCatalog()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Solo la categoría HOME tiene efecto; los extras del Intent se ignoran por completo.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.onHomeIntent()
    }

    private fun handleEvent(event: MainEvent) {
        when (event) {
            is MainEvent.LaunchRoleRequest -> try {
                roleRequest.launch(event.intent)
            } catch (_: ActivityNotFoundException) {
                openHomeSettings()
            }
            MainEvent.OpenHomeSettings -> openHomeSettings()
        }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            viewModel.onHomeSettingsOpened()
        } catch (_: ActivityNotFoundException) {
            viewModel.onHomeSettingsUnavailable()
        }
    }
}
