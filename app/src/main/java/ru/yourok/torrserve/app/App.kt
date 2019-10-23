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

        fun Toast(txt: String, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val show = if (long)
                    android.widget.Toast.LENGTH_LONG
                else
                    android.widget.Toast.LENGTH_SHORT
                android.widget.Toast.makeText(contextApp, txt, show).show()
            }
        }

        fun Toast(txt: Int, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val show = if (long)
                    android.widget.Toast.LENGTH_LONG
                else
                    android.widget.Toast.LENGTH_SHORT
                android.widget.Toast.makeText(contextApp, txt, show).show()
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
