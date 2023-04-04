package ru.yourok.torrserve.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import ru.yourok.torrserve.settings.Settings

class App : MultiDexApplication() {

    init {
        instance = this
    }

    val mActivityLifecycleCallbacks = ActivityCallbacks()

    companion object {
        private var instance: App? = null
        private lateinit var appContext: Context
        var inForeground: Boolean = false
        const val shortToastDuration: Int = 1200
        const val longToastDuration: Int = 3000
        private lateinit var wakeLock: PowerManager.WakeLock

        val context: Context
            get() {
                return appContext
            }

        private val lifecycleEventObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                inForeground = false
            } else if (event == Lifecycle.Event.ON_START) {
                inForeground = true
            }
        }

        fun currentActivity(): Activity? {
            return instance?.mActivityLifecycleCallbacks?.currentActivity
        }

        @SuppressLint("RestrictedApi")
        fun toast(txt: String, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val dur = if (long) longToastDuration else shortToastDuration
                // this view overlap the system navigation bar, better use android.R.id.content
                //val view = currentActivity()?.window?.decorView?.rootView ?: return@post
                val view: View = currentActivity()?.findViewById(android.R.id.content) ?: return@post
                AppToast
                    .make(view as ViewGroup, txt)
                    .setDuration(dur)
                    .show()
            }
        }

        @SuppressLint("RestrictedApi")
        fun toast(txt: Int, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val dur = if (long) longToastDuration else shortToastDuration
                // this view overlap the system navigation bar, better use android.R.id.content
                //val view = currentActivity()?.window?.decorView?.rootView ?: return@post
                val view: View = currentActivity()?.findViewById(android.R.id.content) ?: return@post
                AppToast
                    .make(view as ViewGroup, context.getString(txt))
                    .setDuration(dur)
                    .show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        ProcessLifecycleOwner
            .get().lifecycle
            .addObserver(lifecycleEventObserver)
        // Track activities
        registerActivityLifecycleCallbacks(mActivityLifecycleCallbacks)
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