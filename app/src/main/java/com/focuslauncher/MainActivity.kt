package com.focuslauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
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
        // Un Home recibido al crear la Activity ya arranca en la pantalla principal.
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
