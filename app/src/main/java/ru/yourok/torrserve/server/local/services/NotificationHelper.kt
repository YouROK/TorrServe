package ru.yourok.torrserve.server.local.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ru.yourok.torrserve.R
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.ui.activities.main.MainActivity

class NotificationHelper {
    private var mService: NotificationTS? = null

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mService = (service as NotificationTS.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mService = null
        }

    }

    fun doBindService(context: Context) {
        if (mService == null) {
            context.bindService(
                Intent(context, NotificationTS::class.java),
                mConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    fun doUnbindService(context: Context) {
        mService?.let {
            try {
                context.unbindService(mConnection)
            } catch (_: Exception) {
            }
        }
    }
}

class NotificationTS : Service() {
    private lateinit var notificationManager: NotificationManager
    private var builder: NotificationCompat.Builder? = null

    private val mBinder = LocalBinder()

    val notificationId = 42
    val channelId = "ru.yourok.torrserve"
    private val channelName = "ru.yourok.torrserve"
    private val lock = Any()

    inner class LocalBinder : Binder() {
        internal val service: NotificationTS
            get() = this@NotificationTS
    }

    override fun onCreate() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotification()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        synchronized(lock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else
                stopForeground(true)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    private fun createNotification() {
        synchronized(lock) {
            val exitIntent = Intent(this, TorrService::class.java)
            exitIntent.action = TorrService.ACTION_STOP
            exitIntent.putExtra("forceclose", true)
            val exitPendingIntent = if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M))
                PendingIntent.getService(this, 0, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            else
                PendingIntent.getService(this, 0, exitIntent, PendingIntent.FLAG_ONE_SHOT)

            val contentIntent = Intent(this, MainActivity::class.java)
            contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            val contentPendingIntent = if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M))
                PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE)
            else
                PendingIntent.getActivity(this, 0, contentIntent, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                this.getSystemService(NotificationManager::class.java)!!
                    .createNotificationChannel(channel)
            }

            if (builder == null)
                builder = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentText(getString(R.string.stat_running))
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setContentIntent(contentPendingIntent)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(""))
                    .addAction(
                        android.R.drawable.ic_delete,
                        this.getText(R.string.exit),
                        exitPendingIntent
                    )
            else
                builder?.setStyle(NotificationCompat.BigTextStyle().bigText(""))

            if (Utils.isAmazonTV)
                builder?.setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_notification))

            builder?.let {
                ServiceCompat.startForeground(this, notificationId, it.build(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        }
    }
}
