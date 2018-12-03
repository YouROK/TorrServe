package ru.yourok.torrserve.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.yourok.torrserve.preferences.Preferences

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Preferences.isAutoStart()) {
            val intent = Intent(context, ServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }
    }
}