package ru.yourok.torrserve.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.preferences.Preferences

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Preferences.isAutoStart()) {
            val intent = Intent(context, ServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Handler().post {
                    try {
                        context.startForegroundService(intent)
                    } catch (e: Exception) {
                        App.Toast(R.string.error_server_start)
                    }
                }
            else
                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    App.Toast(R.string.error_server_start)
                }
        }
    }
}