package ru.yourok.torrserve.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import ru.yourok.torrserve.R
import ru.yourok.torrserve.activitys.main.MainActivity
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.torrent.Torrent
import ru.yourok.torrserve.utils.ByteFmt
import kotlin.concurrent.thread

object NotificationServer {

    private val channelId = "ru.yourok.torrserve"
    private val channelName = "ru.yourok.torrserve"
    private var update = false
    private var hash = ""
    private var builder: NotificationCompat.Builder? = null
    private val lock = Any()

    private fun build(context: Context, msg: String) {
        synchronized(lock) {
            val restartIntent = Intent(context, ServerService::class.java)
            restartIntent.setAction("ru.yourok.torrserve.server.action_restart")
            val restartPendingIntent = PendingIntent.getService(context, 0, restartIntent, 0)

            val exitIntent = Intent(context, ServerService::class.java)
            exitIntent.setAction("ru.yourok.torrserve.server.action_exit")
            val exitPendingIntent = PendingIntent.getService(context, 0, exitIntent, 0)

            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                context.getSystemService<NotificationManager>(NotificationManager::class.java)!!.createNotificationChannel(channel)
            }

            if (builder == null)
                builder = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(context.getString(R.string.app_name))
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setContentIntent(pendingIntent)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                        .addAction(android.R.drawable.stat_notify_sync, context.getText(R.string.restart_server), restartPendingIntent)
                        .addAction(android.R.drawable.ic_delete, context.getText(R.string.exit), exitPendingIntent)
            else
                builder?.setStyle(NotificationCompat.BigTextStyle().bigText(msg))

            builder?.let {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(0, it.build())
            }
        }
    }

    fun Show(context: Context, hash: String) {
        this.hash = hash
        synchronized(update) {
            if (update)
                return
            update = true
        }

        thread {
            var isShow = false
            while (update) {
                try {
                    val stat = Api.torrentStat(this.hash)
                    val torrStatus = stat.getInt("TorrentStatus", 0)
                    val preloadedBytes = stat.getLong("PreloadedBytes", 0L)
                    val preloadSize = stat.getLong("PreloadSize", 0L)
                    val activePeers = stat.getInt("ActivePeers", 0)
                    val totalPeers = stat.getInt("TotalPeers", 0)
                    val connectedSeeders = stat.getInt("ConnectedSeeders", 0)
                    val downloadSpeed = stat.getDouble("DownloadSpeed", 0.0)
                    val uploadSpeed = stat.getDouble("UploadSpeed", 0.0)

                    var msg = context.getString(R.string.peers) + ": [" + connectedSeeders.toString() + "] " + activePeers.toString() + " / " + totalPeers.toString() + "\n" +
                            context.getString(R.string.download_speed) + ": " + ByteFmt.byteFmt(downloadSpeed)
                    if (uploadSpeed > 0)
                        msg += "\n" + context.getString(R.string.upload_speed) + ": " + ByteFmt.byteFmt(uploadSpeed)

                    if (torrStatus == Torrent.TorrentSTPreload && !isShow && preloadSize > 0)
                        msg += "\n" + context.getString(R.string.buffer) + ": " + (preloadedBytes * 100 / preloadSize).toString() + "% " + ByteFmt.byteFmt(preloadedBytes) + "/" + ByteFmt.byteFmt(preloadSize)
                    build(context, msg)
                } catch (e: Exception) {
                    build(context, context.getText(R.string.stat_server_is_running).toString())
                    break
                }
                Thread.sleep(1000)
            }
            build(context, context.getText(R.string.stat_server_is_running).toString())
            update = false
        }
    }

    fun Close(context: Context) {
        synchronized(lock) {
            update = false
            builder = null
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(0)
        }
    }
}