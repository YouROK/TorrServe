package ru.yourok.torrserve.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.settings.Settings
import kotlin.concurrent.thread

class TorrService : Service() {
    private var notification = NotificationHelper()
    private val serverFile = ServerFile()

    override fun onBind(p0: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        notification.doBindService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.action != null) {
                when (it.action) {
                    ActionStart -> {
                        startServer()
                        return START_STICKY
                    }
                    ActionStop -> {
                        stopServer()
                        return START_NOT_STICKY
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        thread {
            if (serverFile.exists() && isLocal() && Api.echo().isEmpty()) {
                serverFile.run()
            }
        }
    }

    private fun stopServer() {
        thread {
            if (isLocal() && Api.echo().isNotEmpty())
                Api.shutdown()

            serverFile.stop()
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
    }

    companion object {
        const val ActionStart = "ru.yourok.torrserve.server.action_start"
        const val ActionStop = "ru.yourok.torrserve.server.action_stop"

        fun start() {
            try {
                val intent = Intent(App.context, Service::class.java)
                intent.action = ActionStart
                App.context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop() {
            try {
                val intent = Intent(App.context, Service::class.java)
                intent.action = ActionStop
                App.context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun wait(timeout: Int = 30) {
            var count = 0
            if (timeout < 0)
                count = -30
            while (Api.echo() == "" || count > timeout) {
                Thread.sleep(1000)
                count++
            }
        }

        fun isLocal(): Boolean {
            val host = Settings.getHost()
            return host.contains("://127.0.0.1") || host.contains("://localhost")
        }
    }
}