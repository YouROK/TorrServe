package ru.yourok.torrserve.server.local.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.main.MainActivity

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Settings.isBootStart()) {
            val mintent = Intent(context, MainActivity::class.java)
            mintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(mintent)
        }
    }
}