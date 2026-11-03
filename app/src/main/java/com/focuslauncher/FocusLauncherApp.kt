package com.focuslauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.focuslauncher.core.security.AdminSessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FocusLauncherApp : Application() {

    @Inject lateinit var adminSessions: AdminSessionManager

    override fun onCreate() {
        super.onCreate()
        // Receiver DINÁMICO y no exportado (no es un receiver de comandos): al apagarse o
        // bloquearse la pantalla se revoca la sesión administrativa de inmediato.
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = adminSessions.revoke()
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
