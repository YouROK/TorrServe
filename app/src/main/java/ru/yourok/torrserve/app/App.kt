package ru.yourok.torrserve.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import ru.yourok.torrserve.utils.ThemeUtil


class App : MultiDexApplication() {

    init {
        instance = this
    }

    val mActivityLifecycleCallbacks = ActivityCallbacks()

    companion object {
        private var instance: App? = null
        private lateinit var appContext: Context
        var inForeground: Boolean = false
        const val SHORT_TOAST_DURATION: Int = 1200
        const val LONG_TOAST_DURATION: Int = 3000
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

        val currentActivity: Activity?
            get() {
                return instance?.mActivityLifecycleCallbacks?.currentActivity
            }

        @SuppressLint("RestrictedApi")
        fun toast(txt: String, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val dur = if (long) LONG_TOAST_DURATION else SHORT_TOAST_DURATION
                val view: View = currentActivity?.findViewById(android.R.id.content) ?: return@post
                AppToast
                    .make(view as ViewGroup, txt)
                    .setDuration(dur)
                    .show()
            }
        }

        @SuppressLint("RestrictedApi")
        fun toast(txt: Int, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val dur = if (long) LONG_TOAST_DURATION else SHORT_TOAST_DURATION
                val view: View = currentActivity?.findViewById(android.R.id.content) ?: return@post
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
        ThemeUtil.setNightMode()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TorrServe:WakeLock")
    }

}