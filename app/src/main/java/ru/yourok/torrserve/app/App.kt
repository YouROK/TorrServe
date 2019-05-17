package ru.yourok.torrserve.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import ru.yourok.torrserve.R


class App : Application() {
    companion object {
        private lateinit var contextApp: Context
        private lateinit var wakeLock: PowerManager.WakeLock

        fun getContext(): Context {
            return contextApp
        }

        fun wakeLockStart() {
            wakeLock.acquire()
        }

        fun wakeLockStop() {
            wakeLock.release()
        }

        fun Toast(txt: String) {
            Handler(Looper.getMainLooper()).post {
                android.widget.Toast.makeText(contextApp, txt, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        fun Toast(txt: Int) {
            Handler(Looper.getMainLooper()).post {
                android.widget.Toast.makeText(contextApp, txt, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        contextApp = applicationContext

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TorrServe:WakeLock")

        ACR.get(this)
                .setEmailAddresses("8yourok8@gmail.com")
                .setEmailSubject(getString(R.string.app_name) + " Crash Report")
                .start()
    }
}
