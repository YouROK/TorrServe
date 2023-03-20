package ru.yourok.torrserve.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import com.google.android.material.snackbar.Snackbar
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Format.dp2px
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

        private fun currentActivity(): Activity? {
            return instance?.mActivityLifecycleCallbacks?.currentActivity
        }

        @SuppressLint("RestrictedApi")
        fun toast(txt: String, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val dur = if (long)
                    Snackbar.LENGTH_LONG
                else
                    Snackbar.LENGTH_SHORT
                val view = currentActivity()?.window?.decorView?.rootView ?: return@post

                val snackbar = Snackbar
                    .make(view, txt, dur)

                val snackbarLayout: Snackbar.SnackbarLayout? = snackbar.view as Snackbar.SnackbarLayout?
                val themedContext = ContextThemeWrapper(appContext, ThemeUtil.selectedTheme)
                var bg = R.drawable.snackbar
                var tc = R.color.black
                if (ThemeUtil.selectedTheme == R.style.Theme_TorrServe_Light) {
                    bg = R.drawable.snackbar_dark
                    tc = R.color.tv_white
                }
                snackbarLayout?.background = AppCompatResources.getDrawable(appContext, bg)
                val textView = snackbarLayout?.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView?
                textView?.maxLines = 15
                textView?.textSize = 18.0f
                textView?.setTextColor(ContextCompat.getColor(appContext, tc))
                val img = ContextCompat.getDrawable(themedContext, R.drawable.ts_round)
                val padding = dp2px(10f)
                val imgSize = textView?.lineHeight ?: (padding * 2)
                img?.setBounds(0, 0, imgSize + dp2px(6f), imgSize + dp2px(6f))
                textView?.setCompoundDrawables(img, null, null, null)
                textView?.compoundDrawablePadding = padding

                val layoutParams = snackbarLayout?.layoutParams as ViewGroup.MarginLayoutParams
                val pad = dp2px(32.0f)
                layoutParams.setMargins(pad, pad, pad, pad)
                snackbarLayout.layoutParams = layoutParams

                snackbar.show()
            }
        }

        @SuppressLint("RestrictedApi")
        fun toast(txt: Int, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val dur = if (long)
                    Snackbar.LENGTH_LONG
                else
                    Snackbar.LENGTH_SHORT
                val view = currentActivity()?.window?.decorView?.rootView ?: return@post

                val snackbar = Snackbar
                    .make(view, txt, dur)

                val snackbarLayout: Snackbar.SnackbarLayout? = snackbar.view as Snackbar.SnackbarLayout?
                val themedContext = ContextThemeWrapper(appContext, ThemeUtil.selectedTheme)
                var bg = R.drawable.snackbar
                var tc = R.color.black
                if (ThemeUtil.selectedTheme == R.style.Theme_TorrServe_Light) {
                    bg = R.drawable.snackbar_dark
                    tc = R.color.tv_white
                }
                snackbarLayout?.background = AppCompatResources.getDrawable(appContext, bg)
                val textView = snackbarLayout?.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView?
                textView?.maxLines = 15
                textView?.textSize = 18.0f
                textView?.setTextColor(ContextCompat.getColor(appContext, tc))
                val img = ContextCompat.getDrawable(themedContext, R.drawable.ts_round)
                val padding = dp2px(10f)
                val imgSize = textView?.lineHeight ?: (padding * 2)
                img?.setBounds(0, 0, imgSize + dp2px(6f), imgSize + dp2px(6f))
                textView?.setCompoundDrawables(img, null, null, null)
                textView?.compoundDrawablePadding = padding

                val layoutParams = snackbarLayout?.layoutParams as ViewGroup.MarginLayoutParams
                val pad = dp2px(32.0f)
                layoutParams.setMargins(pad, pad, pad, pad)
                snackbarLayout.layoutParams = layoutParams

                snackbar.show()
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