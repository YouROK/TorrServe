package ru.yourok.torrserve.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.serverloader.ServerFile
import kotlin.concurrent.thread

class ServerService : Service() {
    private var notification = NotificationHelper()

    override fun onBind(p0: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        notification.doBindService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 0 - start
        // 1 - exit
        // 2 - restart
        // 3 - set hash

        var cmd = 0

        intent?.let {
            if (it.action != null) {
                when (it.action) {
                    "ru.yourok.torrserve.server.action_exit" -> {
                        cmd = 1
                    }
                    "ru.yourok.torrserve.server.action_restart" -> {
                        cmd = 2
                    }
                    "ru.yourok.torrserve.server.action_set_hash" -> {
                        cmd = 3
                    }
                }
            }
        }

        when (cmd) {
            1 -> {
                stopServer()
                return START_NOT_STICKY
            }
            2 -> {
                restartServer()
                return START_STICKY
            }
            3 -> {
                intent?.let {
                    if (it.hasExtra("hash")) {
                        setHash(it.getStringExtra("hash"))
                    }
                }
                return START_STICKY
            }
            else -> {
                startServer()
                return START_STICKY
            }
        }
    }

    private fun setHash(hash: String) {
        notification.setHash(hash)
    }

    private fun startServer() {
        notification.setHash("")
        thread {
            if (ServerFile.serverExists() && Api.serverIsLocal() && Api.serverEcho().isEmpty()) {
                ServerFile.run()
            }
        }
    }

    private fun stopServer() {
        if (Api.serverIsLocal() && Api.serverEcho().isNotEmpty()) {
            Api.serverShutdown()
        }

        ServerFile.stop()
        Handler(this.getMainLooper()).post {
            App.Toast(getString(R.string.server_stoped))
        }
        notification.doUnbindService(this)
        thread {
            Thread.sleep(1000)
            System.exit(0)
        }
        stopSelf()
    }

    private fun restartServer() {
        thread {
            if (ServerFile.serverExists() && Api.serverIsLocal()) {
                ServerFile.stop()
                ServerFile.run()
                Handler(this.getMainLooper()).post {
                    App.Toast(getString(R.string.stat_server_is_running))
                }
            }
        }
    }

    companion object {
        fun notificationSetHash(hash: String) {
            try {
                val intent = Intent(App.getContext(), ServerService::class.java)
                intent.action = "ru.yourok.torrserve.server.action_set_hash"
                intent.putExtra("hash", hash)
                App.getContext().startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun start() {
            try {
                val intent = Intent(App.getContext(), ServerService::class.java)
                intent.action = "ru.yourok.torrserve.server.action_start"
                App.getContext().startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun exit() {
            try {
                val intent = Intent(App.getContext(), ServerService::class.java)
                intent.action = "ru.yourok.torrserve.server.action_exit"
                App.getContext().startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun wait(timeout: Int) {
            var count = 0
            if (timeout < 0)
                count = -3600
            while (Api.serverEcho() == "") {
                Thread.sleep(1000)
                count++
                if (count > timeout)
                    break
            }
        }
    }
}