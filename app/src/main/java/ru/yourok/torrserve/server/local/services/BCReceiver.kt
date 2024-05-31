package ru.yourok.torrserve.server.local.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Settings

class BCReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent!!.action) {
            if (Settings.isBootStart()) {
                TorrService.start()
            }
            Utils.updateAtvCards()
        }
    }
}