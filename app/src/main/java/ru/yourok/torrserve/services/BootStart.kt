package ru.yourok.torrserve.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.yourok.torrserve.activitys.splash.SplashActivity
import ru.yourok.torrserve.preferences.Preferences

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Preferences.isAutoStart()) {
            val intent = Intent(context, SplashActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("silent", true)
            context.startActivity(intent)
//            val intent = Intent(context, ServerService::class.java)
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
//                Handler().post {
//                    try {
//                        context.startForegroundService(intent)
//                    } catch (e: Exception) {
//                        App.Toast(R.string.error_server_start)
//                    }
//                }
//            else
//                try {
//                    context.startService(intent)
//                } catch (e: Exception) {
//                    App.Toast(R.string.error_server_start)
//                }
        }
    }
}