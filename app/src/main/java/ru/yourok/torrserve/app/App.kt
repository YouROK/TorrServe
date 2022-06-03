package ru.yourok.torrserve.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import ru.yourok.torrserve.settings.Settings

class App : MultiDexApplication() {

    companion object {

        private lateinit var appContext: Context
        var inForeground: Boolean = false
        private lateinit var wakeLock: PowerManager.WakeLock

        val context: Context
            get() {
                return appContext
            }

        private val lifecycleEventObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                //Log.e( "APP" , "in background" )
                inForeground = false
            } else if (event == Lifecycle.Event.ON_START) {
                //Log.e( "APP" , "in foreground" )
                inForeground = true
            }
        }

        fun toast(txt: String, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val show = if (long)
                    android.widget.Toast.LENGTH_LONG
                else
                    android.widget.Toast.LENGTH_SHORT
                android.widget.Toast.makeText(context, txt, show).show()
            }
        }

        fun toast(txt: Int, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val show = if (long)
                    android.widget.Toast.LENGTH_LONG
                else
                    android.widget.Toast.LENGTH_SHORT
                android.widget.Toast.makeText(context, txt, show).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        ProcessLifecycleOwner
            .get().lifecycle
            .addObserver(lifecycleEventObserver)

        // DayNight Auto ON/OFF
        when (Settings.getTheme()) {
            "dark", "black" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TorrServe:WakeLock")
    }

}