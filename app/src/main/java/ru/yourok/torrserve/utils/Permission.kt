package ru.yourok.torrserve.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import ru.yourok.torrserve.R
import kotlin.concurrent.thread


object Permission {
    const val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    const val requestCode = 101

    fun requestPermissionWithRationale(activity: Activity, permission: String) {
        thread {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                Snackbar.make(activity.findViewById(android.R.id.content) ?: return@thread, R.string.permission_storage_msg, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.permission_btn) {
                        ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
                    }
                    .show()
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
            }
        }
        /*thread {
            // Disable power save
            try {
                val intent = Intent()
                val packageName: String = activity.packageName
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!(activity.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) {
                        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        intent.data = Uri.parse("package:$packageName")
                        activity.startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }*/
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}