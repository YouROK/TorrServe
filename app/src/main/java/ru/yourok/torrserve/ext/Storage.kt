package ru.yourok.torrserve.ext

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager

// https://stackoverflow.com/a/70878956
fun Context.getInternalStorageDirectoryPath(): String? {
    val storageDirectoryPath: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        storageManager.primaryStorageVolume.directory!!.absolutePath
    } else {
        Environment.getExternalStorageDirectory().absolutePath
    }
    return storageDirectoryPath
}