package ru.yourok.torrserve.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ru.yourok.torrserve.R
import ru.yourok.torrserve.activitys.main.MainActivity
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils.isAmazonDev
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.torrent.Torrent
import ru.yourok.torrserve.utils.ByteFmt
import kotlin.concurrent.thread


class Notification : Service() {
    private var mNM: NotificationManager? = null
    private var builder: NotificationCompat.Builder? = null

    private val NOTIFICATION = 51
    private val mBinder = LocalBinder()

    private val channelId = "ru.yourok.torrserve"
    private val channelName = "ru.yourok.torrserve"
    private var hash = ""
    private var update = false
    private val lock = Any()

    inner class LocalBinder : Binder() {
        internal val service: Notification
            get() = this@Notification
    }

    override fun onCreate() {
        mNM = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground()
//        showNotification("")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        synchronized(lock) {
            update = false
//            mNM?.cancel(NOTIFICATION)
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    private fun startForeground() {
        synchronized(lock) {
            val restartIntent = Intent(this, ServerService::class.java)
            restartIntent.setAction("ru.yourok.torrserve.server.action_restart")
            val restartPendingIntent = PendingIntent.getService(this, 0, restartIntent, 0)

            val exitIntent = Intent(this, ServerService::class.java)
            exitIntent.setAction("ru.yourok.torrserve.server.action_exit")
            val exitPendingIntent = PendingIntent.getService(this, 0, exitIntent, 0)

            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                this.getSystemService<NotificationManager>(NotificationManager::class.java)!!.createNotificationChannel(channel)
            }

            if (builder == null)
                builder = NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        //.setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.stat_server_is_running))
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setContentIntent(pendingIntent)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(""))
                        .addAction(android.R.drawable.stat_notify_sync, this.getText(R.string.restart_server), restartPendingIntent)
                        .addAction(android.R.drawable.ic_delete, this.getText(R.string.exit), exitPendingIntent)
            else
                builder?.setStyle(NotificationCompat.BigTextStyle().bigText(""))
            if (isAmazonDev()) // only for Amazon
                builder?.setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_notify))
            builder?.let {
                startForeground(NOTIFICATION, it.build())
            }
        }
    }

    private fun showNotification(msg: String) {
        synchronized(lock) {
            val restartIntent = Intent(this, ServerService::class.java)
            restartIntent.setAction("ru.yourok.torrserve.server.action_restart")
            val restartPendingIntent = PendingIntent.getService(this, 0, restartIntent, 0)

            val exitIntent = Intent(this, ServerService::class.java)
            exitIntent.setAction("ru.yourok.torrserve.server.action_exit")
            val exitPendingIntent = PendingIntent.getService(this, 0, exitIntent, 0)

            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                this.getSystemService<NotificationManager>(NotificationManager::class.java)!!.createNotificationChannel(channel)
            }

            if (builder == null)
                builder = NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        //.setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.stat_server_is_running))
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setContentIntent(pendingIntent)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                        .addAction(android.R.drawable.stat_notify_sync, this.getText(R.string.restart_server), restartPendingIntent)
                        .addAction(android.R.drawable.ic_delete, this.getText(R.string.exit), exitPendingIntent)
            else
                builder?.setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            if (isAmazonDev()) // only for Amazon
                builder?.setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_notify))
            builder?.let {
                mNM?.notify(NOTIFICATION, it.build())
            }
        }
    }

    fun setHash(hash: String) {
        this.hash = hash
        startUpdate()
    }

    private fun startUpdate() {
        synchronized(update) {
            if (update)
                return
            update = true
        }

        thread {
            App.wakeLockStart()
            while (update) {
                try {
                    if (this.hash.isEmpty())
                        break
                    val stat = Api.torrentStat(this.hash)
                    val torrStatus = stat.getInt("TorrentStatus", 0)
                    val preloadedBytes = stat.getLong("PreloadedBytes", 0L)
                    val preloadSize = stat.getLong("PreloadSize", 0L)
                    val activePeers = stat.getInt("ActivePeers", 0)
                    val totalPeers = stat.getInt("TotalPeers", 0)
                    val connectedSeeders = stat.getInt("ConnectedSeeders", 0)
                    val downloadSpeed = stat.getDouble("DownloadSpeed", 0.0)
                    val uploadSpeed = stat.getDouble("UploadSpeed", 0.0)

                    var msg = getString(R.string.peers) + ": [" + connectedSeeders.toString() + "] " + activePeers.toString() + " / " + totalPeers.toString() + "\n" +
                            getString(R.string.download_speed) + ": " + ByteFmt.byteFmt(downloadSpeed)
                    if (uploadSpeed > 0)
                        msg += "\n" + getString(R.string.upload_speed) + ": " + ByteFmt.byteFmt(uploadSpeed)

                    if (torrStatus == Torrent.TorrentSTPreload && preloadSize > 0)
                        msg += "\n" + getString(R.string.buffer) + ": " + (preloadedBytes * 100 / preloadSize).toString() + "% " + ByteFmt.byteFmt(preloadedBytes) + "/" + ByteFmt.byteFmt(preloadSize)
                    showNotification(msg)
                } catch (e: Exception) {
                    showNotification(getText(R.string.stat_server_is_running).toString())
                    break
                }
                Thread.sleep(1000)
            }
            showNotification(getText(R.string.stat_server_is_running).toString())
            update = false
            App.wakeLockStop()
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
        if (context.bindService(Intent(context, Notification::class.java), mConnection, Context.BIND_AUTO_CREATE)) {
            mShouldUnbind = true
        }
    }

    fun doUnbindService(context: Context) {
        if (mShouldUnbind) {
            context.unbindService(mConnection)
            mShouldUnbind = false
        }
    }

    fun setHash(hash: String) {
        mBoundService?.setHash(hash)
    }
}