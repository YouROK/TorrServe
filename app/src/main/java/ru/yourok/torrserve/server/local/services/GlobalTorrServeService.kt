package ru.yourok.torrserve.server.local.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.server.local.TorrService


class GlobalTorrServeService : AccessibilityService() {
    private val notification = NotificationHelper()
    private val serverFile = ServerFile()
    private val TAG = javaClass.simpleName.take(21)

    override fun onServiceConnected() {
        // Api.echo() is always empty
        if (serverFile.exists() && TorrService.isLocal()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "onServiceConnected()")
            Api.shutdown()
            serverFile.run()
            notification.doBindService(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        notification.doUnbindService(this)
        return false
    }

}