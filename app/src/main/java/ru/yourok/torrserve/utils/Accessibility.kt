package ru.yourok.torrserve.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.topjohnwu.superuser.Shell
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.local.services.GlobalTorrService


object Accessibility {
    private fun openAccessibilitySettings(context: Context): Boolean {
        val isOk: Boolean
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        isOk = try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            //e.printStackTrace()
            //e.message?.let { App.toast(it) }
            false
        }
        return isOk
    }

    private fun openTvAccessibilitySettings(context: Context): Boolean {
        var isOk: Boolean
        val tvintent = Intent("android.intent.action.MAIN")
        tvintent.addCategory("android.intent.category.LAUNCHER")
        tvintent.setClassName("com.android.tv.settings", "com.android.tv.settings.system.AccessibilityActivity")
        tvintent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        try {
            context.startActivity(tvintent)
            isOk = true
        } catch (e: ActivityNotFoundException) {
            tvintent.setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings")
            // com.android.tv.settings.accessibility.AccessibilityFragment
            //tvintent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, "com.android.tv.settings.device.DevicePrefFragment")
            //tvintent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, "accessibility") // extras
            isOk = try {
                context.startActivity(tvintent)
                true
            } catch (e: Exception) {
                //e.printStackTrace()
                false
            }
        } catch (e: Exception) {
            //e.printStackTrace()
            isOk = false
        }
        return isOk
    }

    private fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            e.message?.let { App.toast(it) }
        }
    }

    private fun showAccessibilitySettings(context: Context) {
        if (isPackageInstalled(context, "com.android.tv.settings")) {
            openTvAccessibilitySettings(context)
        } else if (isPackageInstalled(context, "com.android.settings")) {
            openAccessibilitySettings(context)
        } else {
            openSettings(context)
        }
    }

    fun enableService(requireContext: Context, enable: Boolean) {
        val permission = "android.permission.WRITE_SECURE_SETTINGS"
        if (!Permission.isPermissionGranted(requireContext, permission) && Shell.rootAccess()) {
            Shell.su("pm grant ${requireContext.packageName} $permission").exec()
        }
        if (Permission.isPermissionGranted(requireContext, permission)) {
            val contentResolver = requireContext.contentResolver
            var enServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val myService = requireContext.packageName + "/" + GlobalTorrService::class.java.name
            enServices = if (enable) {
                if (enServices.isNullOrEmpty()) {
                    myService
                } else {
                    "$enServices:$myService"
                }
            } else {
                enServices.replace(myService, "")
            }
            try {
                Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enServices)
                if (enable) Settings.Secure.putString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            } catch (e: Exception) {
                e.message?.let { App.toast(it) }
                showAccessibilitySettings(requireContext)
            }
        } else {
            if (enable)
                App.toast(R.string.accessibility_manual_on, true)
            else
                App.toast(R.string.accessibility_manual_off, true)
            showAccessibilitySettings(requireContext)
        }
    }

    fun isEnabledService(requireContext: Context): Boolean {
        val contentResolver = requireContext.contentResolver
        val enServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val myService = requireContext.packageName + "/" + GlobalTorrService::class.java.name
        if (enServices?.contains(myService) == true) return true
        return false
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isPackageEnabled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

}