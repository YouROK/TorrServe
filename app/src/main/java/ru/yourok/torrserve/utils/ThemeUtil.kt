package ru.yourok.torrserve.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.settings.Settings.getTheme

class ThemeUtil {
    private var currentTheme = 0
    fun onCreate(activity: AppCompatActivity) {
        currentTheme = selectedTheme
        activity.setTheme(currentTheme)
    }

    fun onResume(activity: AppCompatActivity) {
        if (currentTheme != selectedTheme) {
            activity.recreate()
        }
    }

    fun onConfigurationChanged(activity: AppCompatActivity, newConfig: Configuration) {
        val config = activity.resources.configuration
        val isNightModeActive =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                config.isNightModeActive
            } else {
                config.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
            }
        if (BuildConfig.DEBUG) Log.d("*****", "ThemeUtil onConfigurationChanged isNightModeActive = $isNightModeActive")
    }

    companion object {
        val selectedTheme: Int
            get() {
                return when (getTheme()) {
                    "light" -> R.style.Theme_TorrServe_Light
                    "dark" -> R.style.Theme_TorrServe_Dark
                    "black" -> R.style.Theme_TorrServe_Black
                    else -> R.style.Theme_TorrServe_DayNight
                }
            }

        @ColorInt
        fun getColorFromAttr(
            context: Context,
            @AttrRes attrColor: Int,
            typedValue: TypedValue = TypedValue(),
            resolveRefs: Boolean = true
        ): Int {
            context.theme.resolveAttribute(attrColor, typedValue, resolveRefs)
            return typedValue.data
        }

        fun isDarkMode(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.resources.configuration.isNightModeActive
            } else {
                val darkModeFlag = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                darkModeFlag == Configuration.UI_MODE_NIGHT_YES
            }
        }

        fun setNightMode() {
            when (getTheme()) {
                "dark", "black" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                else -> if (Utils.isTvBox()) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_TIME) else
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) // phones
            }
        }
    }
}