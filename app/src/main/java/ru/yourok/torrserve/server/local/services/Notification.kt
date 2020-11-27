package ru.yourok.torrserve.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ui.activities.main.MainActivity


class Notification : Service() {
    private var mNM: NotificationManager? = null
    private var builder: NotificationCompat.Builder? = null

    private val NOTIFICATION = 42
    private val mBinder = LocalBinder()

    private val channelId = "ru.yourok.torrserve"
    private val channelName = "ru.yourok.torrserve"
    private val lock = Any()

    inner class LocalBinder : Binder() {
        internal val service: Notification
            get() = this@Notification
    }

    override fun onCreate() {
        mNM = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        synchronized(lock) {
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    private fun startForeground() {
        synchronized(lock) {
            val exitIntent = Intent(this, TorrService::class.java)
            exitIntent.action = TorrService.ActionStop
            exitIntent.putExtra("forceclose", true)
            val exitPendingIntent = PendingIntent.getService(this, 0, exitIntent, 0)

            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                this.getSystemService<NotificationManager>(NotificationManager::class.java)!!
                    .createNotificationChannel(channel)
            }

            if (builder == null)
                builder = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentText(getString(R.string.stat_server_is_running))
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(""))
                    .addAction(
                        android.R.drawable.ic_delete,
                        this.getText(R.string.exit),
                        exitPendingIntent
                    )
            else
                builder?.setStyle(NotificationCompat.BigTextStyle().bigText(""))
            builder?.let {
                startForeground(NOTIFICATION, it.build())
            }
        }
    }
}

class NotificationHelper {
    private var mShouldUnbind: Boolean = false
    private var mBoundService: Notification? = null

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mBoundService = (service as Notification.LocalBinder).service
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mBoundService = null
        }
    }

    fun doBindService(context: Context) {
        if (context.bindService(
                Intent(context, Notification::class.java),
                mConnection,
                Context.BIND_AUTO_CREATE
            )
        ) {
            mShouldUnbind = true
        }
    }

    fun doUnbindService(context: Context) {
        if (mShouldUnbind) {
            context.unbindService(mConnection)
            mShouldUnbind = false
        }
    }
}