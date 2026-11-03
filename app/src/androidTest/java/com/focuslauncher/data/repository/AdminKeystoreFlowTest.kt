package com.focuslauncher.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focuslauncher.core.di.MonotonicClock
import com.focuslauncher.core.security.AdminEnvironment
import com.focuslauncher.core.security.AdminSessionManager
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.core.security.Pbkdf2PinHasher
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.AuthenticationResult
import com.focuslauncher.domain.model.MutationResult
import com.focuslauncher.domain.model.UserSerial
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIN real (600000 it.) + Keystore real + DataStore real, con reinicio de «proceso». */
class AdminKeystoreFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "focus_state_test_" + UUID.randomUUID().toString().replace("-", "")
    private val dir = File(context.cacheDir, "adm-" + UUID.randomUUID())
    private var store: FocusStateStore? = null
    private var now = 1_000L
    private val clock = MonotonicClock { now }
    private val env = object : AdminEnvironment {
        override fun isScreenInteractive() = true
        override fun isDeviceLocked() = false
    }

    private fun boot(): AdminAccessCoordinator = runBlocking {
        store?.close()
        val s = FocusStateStore(
            StateLocation(File(dir, "focus_state.pb.enc")), KeystoreStateCipher(alias),
            CoroutineScope(SupervisorJob() + Dispatchers.IO), context.packageName,
        )
        store = s
        AdminAccessCoordinator(s, AdminSessionManager(clock, env), Pbkdf2PinHasher(Dispatchers.Default), clock)
    }

    @After fun tearDown() = runBlocking {
        store?.close(); dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { if (it.containsAlias(alias)) it.deleteEntry(alias) }
    }

    @Test fun pinAttemptsAndRulesSurviveARestartAndANewProcessHasNoSession() = runBlocking {
        var admin = boot()
        assertEquals(MutationResult.Success, admin.setPackageHidden(AppPackageKey("a.app", UserSerial(0)), true)) // sin PIN
        assertEquals(MutationResult.Success, admin.enablePin("012345".toCharArray(), "012345".toCharArray()))
        assertEquals(AuthenticationResult.IncorrectPin(4), admin.verifyPin("111111".toCharArray()))
        assertEquals(AuthenticationResult.IncorrectPin(3), admin.verifyPin("222222".toCharArray()))

        admin = boot() // reinicio: mismo archivo y misma clave
        assertFalse(admin.isAuthorized.value)
        assertEquals(MutationResult.AuthenticationRequired, admin.setPackageHidden(AppPackageKey("b.app", UserSerial(0)), true))
        assertEquals(AuthenticationResult.IncorrectPin(2), admin.verifyPin("333333".toCharArray())) // los intentos persistieron
        assertEquals(AuthenticationResult.Success, admin.verifyPin("012345".toCharArray())) // ceros iniciales
        assertTrue(admin.isAuthorized.value)
        assertEquals(MutationResult.Success, admin.setPackageHidden(AppPackageKey("b.app", UserSerial(0)), true))
    }

    @Test fun cooldownSurvivesARestartWithTheRealStack() = runBlocking {
        var admin = boot()
        admin.enablePin("123456".toCharArray(), "123456".toCharArray())
        repeat(4) { admin.verifyPin("000000".toCharArray()) }
        assertEquals(AuthenticationResult.Cooldown(30_000), admin.verifyPin("000000".toCharArray()))
        now = 99_999 // «otro proceso» con reloj distinto
        admin = boot()
        assertEquals(30_000, admin.cooldownRemainingMs())
        assertEquals(AuthenticationResult.Cooldown(30_000), admin.verifyPin("123456".toCharArray()))
    }
}
