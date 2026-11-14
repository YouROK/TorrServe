package ru.yourok.torrserve.server.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ru.yourok.torrserve.settings.Prefs
import java.io.File
import kotlin.concurrent.thread

/**
 * Foreground-сервис, держащий локальный сервер Silo.
 * Старт/стоп/перезапуск через Intent с действиями ACTION_START / ACTION_STOP / ACTION_RESTART.
 */
class TorrService : Service() {

    private lateinit var serverFile: ServerFile

    override fun onCreate() {
        super.onCreate()
        serverFile = ServerFile(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Перезапуск = старт: ServerFile.run() сам гасит предыдущий процесс сервера
            ACTION_START, ACTION_RESTART -> {
                // startForeground() обязан быть вызван сразу (иначе система убивает процесс),
                // а запуск бинарника через shell долгий - выносим его в фоновый поток
                startForegroundCompat()
                thread { serverFile.run(writeConfig()) }
                return START_STICKY
            }
            ACTION_STOP -> {
                // Остановку делаем в фоне, чтобы не блокировать главный поток
                thread { serverFile.stop() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Пишет config.yaml для Silo: порт, каталог данных, лог и пароль owner (если многопользовательский).
     * Возвращает файл конфига.
     */
    private fun writeConfig(): File {
        val dir = filesDir
        val dataDir = File(dir, "data").apply { mkdirs() }
        val config = File(dir, ServerFile.CONFIG_NAME)
        val password = Prefs(this).ownerPassword
        val authBlock = if (password.isNotEmpty())
            "auth:\n  owner_password: \"${password.replace("\"", "\\\"")}\"\n"
        else ""
        config.writeText(
            """
            server:
              host: 0.0.0.0
              port: $LOCAL_PORT
            storage:
              data_dir: ${dataDir.path}
            logging:
              level: info
              file: ${File(dir, ServerFile.LOG_NAME).path}
            """.trimIndent() + "\n" + authBlock
        )
        return config
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Silo")
            .setContentText("Локальный сервер запущен")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Silo", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val ACTION_START = "ru.yourok.torrserve.server.action_start"
        const val ACTION_STOP = "ru.yourok.torrserve.server.action_stop"
        const val ACTION_RESTART = "ru.yourok.torrserve.server.action_restart"
        private const val CHANNEL_ID = "silo_server"
        private const val NOTIFICATION_ID = 1
        /** Локальный сервер всегда на 8090: менять нельзя (см. AGENTS.md). Внешний сервер - любой порт. */
        const val LOCAL_PORT = 8090

        fun start(context: Context) {
            startForegroundCompat(context, Intent(context, TorrService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            val intent = Intent(context, TorrService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        /**
         * Перезапуск сервера одним интентом (например, после смены пароля владельца).
         * Отдельные стоп+старт давали гонку: сервис успевал остановиться между двумя
         * startForegroundService(), и система убивала процесс по таймауту.
         */
        fun restart(context: Context) {
            startForegroundCompat(context, Intent(context, TorrService::class.java).setAction(ACTION_RESTART))
        }

        /** startForegroundService() есть с API 26, на 25 запускаем обычным startService(). */
        private fun startForegroundCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
