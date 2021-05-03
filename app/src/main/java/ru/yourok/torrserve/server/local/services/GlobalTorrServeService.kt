package ru.yourok.torrserve.server.local.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.server.local.TorrService


class GlobalTorrServeService : AccessibilityService() {
    private val notification = NotificationHelper()
    private val serverFile = ServerFile()

    override fun onServiceConnected() {
        if (serverFile.exists() && TorrService.isLocal() && Api.echo().isEmpty()) {
            serverFile.run()
            notification.doBindService(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        notification.doUnbindService(this)
        return true
    }

}