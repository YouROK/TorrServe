package ru.yourok.torrserve.server.local

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ad.ADManager
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.services.NotificationHelper
import ru.yourok.torrserve.server.local.services.NotificationTS
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.settings.Settings.isAccessibilityOn
import ru.yourok.torrserve.utils.Accessibility
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext

class TorrService : Service() {
    private val notification = NotificationHelper()
    private val serverFile = ServerFile()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val serviceScope = CoroutineScope(EmptyCoroutineContext)

    override fun onBind(p0: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                ADManager.get()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val needToMoveToForeground = intent.getBooleanExtra(NEED_FOREGROUND_KEY, false)
            if (it.action != null) {
                when (it.action) {
                    ACTION_START -> {
                        startServer(needToMoveToForeground)
                        return START_STICKY
                    }

                    ACTION_STOP -> {
                        stopServer(intent.hasExtra("forceclose"))
                        return START_NOT_STICKY
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startServer(nfg: Boolean = false) {
        serviceScope.launch {
            if (BuildConfig.DEBUG) Log.d("TorrService", "startServer(nfg:$nfg)")

            if (isLocal() && isAccessibilityOn() && !Accessibility.isEnabledService(App.context)) {
                if (BuildConfig.DEBUG) Log.d("TorrService", "Try to enable AssessibilityService")
                Accessibility.enableService(App.context, true)
            }

            if (serverFile.exists() && isLocal() && Api.echo().isEmpty()) {
                if (nfg) { // fix start local server on boot
                    val builder = NotificationCompat.Builder(this@TorrService, NotificationTS().channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText("Start Foreground")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    ServiceCompat.startForeground(this@TorrService, NotificationTS().notificationId, builder.build(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                }
                serverFile.run()
                notification.doBindService(this@TorrService)
            }

            Utils.updateAtvCards()
        }
    }

    private fun stopServer(forceClose: Boolean) {
        serviceScope.launch {
            if (BuildConfig.DEBUG) Log.d("TorrService", "stopServer(forceClose:$forceClose)")
            if (isLocal() && Api.echo().isNotEmpty())
                Api.shutdown()
            if (!isAccessibilityOn())
                serverFile.stop()
            notification.doUnbindService(this@TorrService)
            stopSelf()
            if (forceClose) {
                thread {
                    Thread.sleep(200)
                    Runtime.getRuntime().exit(0)
                }
            }
        }
    }

    companion object {
        const val ACTION_START = "ru.yourok.torrserve.server.action_start"
        const val ACTION_STOP = "ru.yourok.torrserve.server.action_stop"
        const val NEED_FOREGROUND_KEY = "need_foreground"


        fun start() {
            val context = App.context
            val intent = Intent(context, TorrService::class.java)
            intent.action = ACTION_START
            intent.putExtra(NEED_FOREGROUND_KEY, false)
            try {
                context.startService(intent)
            } catch (ex: IllegalStateException) {
                if (isLocal()) { // avoid ANR on remote
                    intent.putExtra(NEED_FOREGROUND_KEY, true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    }
                }
            }
        }

        fun stop() {
            val context = App.context
            val intent = Intent(context, TorrService::class.java)
            intent.action = ACTION_STOP
            try {
                context.startService(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        fun wait(timeout: Int = -1): Boolean {
            var count = 0
            if (timeout < 0)
                count = -20
            while (Api.echo() == "") {
                Thread.sleep(1000)
                count++
                if (count > timeout)
                    return false
            }
            return true
        }

        fun isLocal(): Boolean {
            val host = Settings.getHost()
            return host.contains("://127.0.0.1") || host.contains("://localhost")
        }

        fun isInstalled(): Boolean = ServerFile().exists()
    }
}