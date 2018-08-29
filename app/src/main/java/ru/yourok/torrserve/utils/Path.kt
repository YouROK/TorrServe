package ru.yourok.torrserve.utils

import android.os.Environment
import ru.yourok.torrserve.app.App
import java.io.File

object Path {
    fun getAppPath(): String {
        val state = Environment.getExternalStorageState()
        var filesDir: File?
        if (Environment.MEDIA_MOUNTED.equals(state))
            filesDir = App.getContext().getExternalFilesDir(null)
        else
            filesDir = App.getContext().getFilesDir()

        if (filesDir == null)
            filesDir = App.getContext().cacheDir

        if (!filesDir.exists())
            filesDir.mkdirs()
        return filesDir.path
    }
}