package com.focuslauncher.data.android

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import com.focuslauncher.core.di.DispatcherProvider
import com.focuslauncher.domain.model.AppId
import com.focuslauncher.domain.model.CatalogError
import com.focuslauncher.domain.model.CatalogState
import com.focuslauncher.domain.model.LaunchResult
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.domain.repository.AppCatalogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Catálogo de actividades launcher reales del perfil de ejecución.
 *
 * Solo consulta `Process.myUserHandle()`; no itera `LauncherApps.getProfiles()`. Un resultado vacío
 * no implica que se controlen todas las apps del teléfono.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class AndroidAppCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val iconLoader: AndroidAppIconLoader,
) : AppCatalogRepository {

    private val launcherApps: LauncherApps? get() = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager? get() = context.getSystemService(UserManager::class.java)
    private val refreshRequests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val catalog: Flow<CatalogState> = merge(
        flowOf(Unit),
        systemEvents().debounce(EVENT_DEBOUNCE_MS),
        refreshRequests,
    )
        .mapLatest { query() }
        .flowOn(dispatchers.io)
        .onStart { emit(CatalogState.Loading) }

    override suspend fun refresh() {
        refreshRequests.emit(Unit)
    }

    /**
     * Callbacks de paquetes + locale/configuración. Se registran al iniciar la colección y se
     * liberan al cancelarla. Cualquier evento invalida iconos y provoca una reconsulta agrupada.
     */
    private fun systemEvents(): Flow<Unit> = callbackFlow {
        val apps = launcherApps
        val callback = object : LauncherApps.Callback() {
            private fun changed() { iconLoader.invalidate(); trySend(Unit) }
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                iconLoader.invalidate()
                trySend(Unit)
            }
        }
        // La colección corre en el dispatcher IO (sin Looper): los handlers se fijan al hilo principal.
        val mainHandler = Handler(Looper.getMainLooper())
        apps?.registerCallback(callback, mainHandler)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_LOCALE_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            },
            null,
            mainHandler,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose {
            apps?.unregisterCallback(callback)
            context.unregisterReceiver(receiver)
        }
    }

    private fun currentSerial(): UserSerial? {
        val raw = userManager?.getSerialNumberForUser(Process.myUserHandle()) ?: return null
        return CatalogBuilder.validSerialOrNull(raw)
    }

    private fun query(): CatalogState {
        val serial = currentSerial() ?: return CatalogState.Error(CatalogError.InvalidUserSerial)
        val apps = launcherApps ?: return CatalogState.Error(CatalogError.QueryFailed)
        return try {
            val entries = apps.getActivityList(null, Process.myUserHandle()).map {
                LauncherEntry(it.componentName.packageName, it.componentName.className, it.label?.toString())
            }
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = queryLauncherActivities(launcherIntent)
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    ResolvedLauncherActivity(
                        packageName = activity.packageName,
                        className = activity.name,
                        enabled = activity.isEnabled && activity.applicationInfo?.enabled != false,
                        exported = activity.exported,
                    )
                }
            CatalogState.Ready(
                CatalogBuilder.build(entries, resolved, context.packageName, serial, Locale.getDefault()),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            CatalogState.Error(CatalogError.QueryFailed)
        } catch (_: RuntimeException) {
            CatalogState.Error(CatalogError.QueryFailed)
        }
    }

    /** `ResolveInfoFlags` existe desde API 33; en API 31/32 se usa la firma con enteros. */
    private fun queryLauncherActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }

    override suspend fun launch(id: AppId): LaunchResult = withContext(dispatchers.io) {
        val apps = launcherApps ?: return@withContext LaunchResult.Failure
        val serial = currentSerial()
        // Se resuelve de nuevo el handle y se exige que corresponda al perfil actual.
        if (serial == null || serial != id.userSerial) return@withContext LaunchResult.Unavailable
        val user = Process.myUserHandle()
        val component = ComponentName(id.packageName, id.activityName)
        try {
            if (!apps.isActivityEnabled(component, user)) return@withContext LaunchResult.Unavailable
            apps.startMainActivity(component, user, null, null)
            LaunchResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (_: ActivityNotFoundException) {
            LaunchResult.Unavailable
        } catch (_: IllegalArgumentException) {
            // Componente desconocido para el sistema (paquete retirado entre consulta y toque).
            LaunchResult.Unavailable
        } catch (_: SecurityException) {
            LaunchResult.Failure
        } catch (_: IllegalStateException) {
            // Usuario no disponible u otro estado inválido del perfil.
            LaunchResult.Unavailable
        }
    }

    private companion object {
        const val EVENT_DEBOUNCE_MS = 250L
    }
}
