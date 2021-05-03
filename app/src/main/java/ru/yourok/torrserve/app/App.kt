package ru.yourok.torrserve.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication

// https://medium.com/android-news/how-to-detect-android-application-open-and-close-background-and-foreground-events-1b4713784b57
class App : MultiDexApplication(), LifecycleObserver {

//    init {
//        instance = this
//    }
//    val lifeCycleHandler = AppLifecycleHandler()

    companion object {

        // private var instance: App? = null
        lateinit var context: Context
        var inForeground = false
        private lateinit var wakeLock: PowerManager.WakeLock

//        fun currentActivity(): Activity? {
//            return instance!!.lifeCycleHandler.currentActivity
//        }

        fun Toast(txt: String, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val show = if (long)
                    android.widget.Toast.LENGTH_LONG
                else
                    android.widget.Toast.LENGTH_SHORT
                android.widget.Toast.makeText(context, txt, show).show()
            }
        }

        fun Toast(txt: Int, long: Boolean = false) {
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
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        context = applicationContext

//        // DayNight Auto ON/OFF (useless?)
//        when (Settings.getTheme()) {
//            "dark", "black" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
//            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
//            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
//        }

//        registerActivityLifecycleCallbacks(lifeCycleHandler)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TorrServe:WakeLock")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        inForeground = false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        inForeground = true
    }

}