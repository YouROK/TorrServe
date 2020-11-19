package ru.yourok.torrserve.utils

import android.Manifest
import android.app.Activity
import android.view.View
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar
import ru.yourok.torrserve.R
import kotlin.concurrent.thread

object Premissions {
    fun requestPermissionWithRationale(activity: Activity) {
        thread {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Snackbar.make(activity.findViewById<View>(R.id.content), R.string.permission_storage_msg, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.permission_btn, {
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                    })
                    .show()
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }
    }
}