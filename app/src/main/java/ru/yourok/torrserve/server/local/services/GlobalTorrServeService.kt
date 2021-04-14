package ru.yourok.torrserve.server.local.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.services.NotificationHelper
import ru.yourok.torrserve.services.TorrService


class GlobalTorrServeService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
    }

    override fun onInterrupt() {
    }

    private var notification = NotificationHelper()
    private val serverFile = ServerFile()

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (serverFile.exists() && TorrService.isLocal() && Api.echo().isEmpty()) {
            Log.d("TorrService", "startServer()")
            serverFile.run()
            notification.doBindService(this)
        }
    }
}