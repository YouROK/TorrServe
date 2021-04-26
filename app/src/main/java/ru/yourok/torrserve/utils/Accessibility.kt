package ru.yourok.torrserve.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.local.services.GlobalTorrServeService

object AccessibilityUtils {
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        context.startActivity(intent)
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        context.startActivity(intent)
    }

    fun enableService(requireContext: Context, enable: Boolean) {
        if (Premissions.isPermissionGranted(requireContext, "android.permission.WRITE_SECURE_SETTINGS")) {
            val contentResolver = requireContext.contentResolver
            var enServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val myService = requireContext.packageName + "/" + GlobalTorrServeService::class.java.name
            if (enable) {
                enServices = if (enServices.isNullOrEmpty()) {
                    myService
                } else {
                    "$enServices:$myService"
                }
            } else {
                enServices = enServices.replace(myService, "")
            }
            try {
                Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enServices)
            } catch (e: Exception) {
                e.printStackTrace()
                e.message?.let { App.Toast(it) }
            }
        } else {
            if (requireContext.isPackageInstalled("com.android.settings")) {
                openAccessibilitySettings(requireContext)
            } else {
                openSettings(requireContext)
                Toast.makeText(requireContext, R.string.accessibility_manual, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun isEnabledService(requireContext: Context): Boolean {
        val contentResolver = requireContext.contentResolver
        val enServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val myService = requireContext.packageName + "/" + GlobalTorrServeService::class.java.name
        if (enServices?.contains(myService) == true) return true
        return false
    }

    fun Context.isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun Context.isPackageEnabled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

}