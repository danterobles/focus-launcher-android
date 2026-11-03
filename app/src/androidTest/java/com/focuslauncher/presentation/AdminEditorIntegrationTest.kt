package com.focuslauncher.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focuslauncher.TestDispatchersInstr
import com.focuslauncher.core.di.MonotonicClock
import com.focuslauncher.core.security.AdminEnvironment
import com.focuslauncher.core.security.AdminSessionManager
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.core.security.Pbkdf2PinHasher
import com.focuslauncher.data.android.AndroidAppCatalogRepository
import com.focuslauncher.data.android.AndroidAppIconLoader
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.data.repository.AdminAccessCoordinator
import com.focuslauncher.data.repository.FocusStateRepositoryImpl
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.AuthenticationResult
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.model.VisibleAppsState
import com.focuslauncher.domain.usecase.ObserveManageablePackages
import com.focuslauncher.domain.usecase.ObserveVisibleApps
import com.focuslauncher.domain.usecase.OwnPackage
import com.focuslauncher.presentation.settings.EditorContent
import com.focuslauncher.presentation.settings.SettingsUiState
import com.focuslauncher.presentation.settings.SettingsViewModel
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editor real: catálogo real + fixtures + Keystore + coordinador con PIN real, de extremo a extremo. */
class AdminEditorIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "focus_state_test_" + UUID.randomUUID().toString().replace("-", "")
    private val dir = File(context.cacheDir, "edit-" + UUID.randomUUID())
    private val fixturePackage = "com.focuslauncher.test"
    private val clock = MonotonicClock { System.nanoTime() / 1_000_000 }
    private val env = object : AdminEnvironment { override fun isScreenInteractive() = true; override fun isDeviceLocked() = false }
    private val dispatchers = TestDispatchersInstr()
    private val store = FocusStateStore(StateLocation(File(dir, "focus_state.pb.enc")), KeystoreStateCipher(alias), CoroutineScope(SupervisorJob() + Dispatchers.IO), context.packageName)
    private val settings = FocusStateRepositoryImpl(store)
    private val catalog = AndroidAppCatalogRepository(context, dispatchers, AndroidAppIconLoader(context, dispatchers))
    private val sessions = AdminSessionManager(clock, env)
    private val admin = AdminAccessCoordinator(store, sessions, Pbkdf2PinHasher(Dispatchers.Default), clock)
    private val manageable = ObserveManageablePackages(catalog, settings, OwnPackage(context.packageName))
    private val visible = ObserveVisibleApps(catalog, settings)

    @After fun tearDown() = runBlocking {
        store.close(); dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { if (it.containsAlias(alias)) it.deleteEntry(alias) }
    }

    private suspend fun SettingsViewModel.await(p: (SettingsUiState) -> Boolean) = withTimeout(20_000) { state.first(p) }
    private fun SettingsUiState.fixture() = (content as EditorContent.Ready).packages.first { it.key.packageName == fixturePackage }

    @Test fun hideWithoutPinThenProtectWithAPinEndToEnd() = runBlocking {
        val vm = SettingsViewModel(manageable, admin)
        // 1) Sin PIN el editor está abierto y agrupa las dos actividades del fixture en una fila.
        val row = vm.await { it.content is EditorContent.Ready }.fixture()
        assertEquals(2, row.activityLabels.size); assertEquals(false, row.hidden)

        // 2) Ocultar guarda al instante y quita ambas actividades de las superficies de apps.
        vm.onSetVisible(row.key, visible = false)
        assertTrue(vm.await { (it.content as? EditorContent.Ready)?.packages?.firstOrNull { p -> p.key == row.key }?.hidden == true }.fixture().hidden)
        val apps = withTimeout(20_000) { visible().first { it is VisibleAppsState.Ready && it.apps.none { a -> a.id.packageName == fixturePackage } } }
        assertTrue(apps is VisibleAppsState.Ready)

        // 3) Activar un PIN (KDF real) bloquea la administración; el editor ya no muestra etiquetas.
        assertEquals(MutationResult.Success, admin.enablePin("246810".toCharArray(), "246810".toCharArray()))
        assertEquals(EditorContent.Locked, vm.await { it.content is EditorContent.Locked }.content)
        assertEquals(MutationResult.AuthenticationRequired, admin.setPackageHidden(row.key, false)) // acción rechazada

        // 4) Con el PIN correcto vuelve el editor y se puede mostrar de nuevo.
        assertTrue(admin.verifyPin("000000".toCharArray()) is AuthenticationResult.IncorrectPin)
        assertEquals(AuthenticationResult.Success, admin.verifyPin("246810".toCharArray()))
        vm.await { it.content is EditorContent.Ready }
        vm.onSetVisible(row.key, visible = true)
        assertEquals(false, vm.await { (it.content as? EditorContent.Ready)?.packages?.firstOrNull { p -> p.key == row.key }?.hidden == false }.fixture().hidden)
        withTimeout(20_000) { visible().first { it is VisibleAppsState.Ready && it.apps.count { a -> a.id.packageName == fixturePackage } == 2 } }

        // 5) Caducar/revocar la sesión vacía el editor al instante.
        admin.lock()
        vm.await { it.content is EditorContent.Locked }
        Unit
    }

    @Test fun anUninstalledHiddenPackageStaysAsATombstoneAndCanBeRemoved() = runBlocking {
        val ghost = AppPackageKey("gone.pkg", com.focuslauncher.domain.model.UserSerial(0))
        assertEquals(MutationResult.Success, admin.setPackageHidden(ghost, true))
        val vm = SettingsViewModel(manageable, admin)
        val tomb = vm.await { (it.content as? EditorContent.Ready)?.packages?.any { p -> p.key == ghost } == true }
            .let { (it.content as EditorContent.Ready).packages.first { p -> p.key == ghost } }
        assertEquals(false, tomb.installed); assertEquals(true, tomb.hidden)
        vm.onSetVisible(ghost, visible = true)
        vm.await { (it.content as? EditorContent.Ready)?.packages?.none { p -> p.key == ghost } == true }
        Unit
    }
}
