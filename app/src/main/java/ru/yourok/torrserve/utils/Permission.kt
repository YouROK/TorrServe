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
import ru.yourok.torrserve.atv.Utils.isChangHong
import kotlin.concurrent.thread


object Permission {
    const val PERM = Manifest.permission.WRITE_EXTERNAL_STORAGE
    const val CODE = 101

    fun requestPermissionWithRationale(activity: Activity, permission: String = PERM) {
        if (!isChangHong) // TODO
            thread {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    Snackbar.make(activity.findViewById(android.R.id.content) ?: return@thread, R.string.permission_storage_msg, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.permission_btn) {
                            ActivityCompat.requestPermissions(activity, arrayOf(permission), CODE)
                        }
                        .show()
                } else {
                    ActivityCompat.requestPermissions(activity, arrayOf(permission), CODE)
                }
            }
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}