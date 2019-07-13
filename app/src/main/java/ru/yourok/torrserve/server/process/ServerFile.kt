package ru.yourok.torrserve.serverloader

import android.util.Log
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.utils.Path
import java.io.File

object ServerFile {
    private val servPath = File(App.getContext().filesDir, "torrserver")
    private var process: Process? = null
    private val lock = Any()

    fun get(): File {
        return servPath
    }

    fun serverExists(): Boolean {
        return servPath.exists()
    }

    fun deleteServer(): Boolean {
        if (!serverExists())
            return true
        process?.stop()
        return servPath.delete()
    }

    fun run() {
        if (!serverExists())
            return
        synchronized(lock) {
            if (process == null || !process!!.isRunning()) {
                process = Process(servPath.path, "-k", "-d", Path.getAppPath())
                process?.onOutput {
                    Log.i("GoLog", it)
                }

                process?.onError {
                    Log.i("GoLogErr", it)
                }
                try {
                    process?.exec()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            process?.stop()
            process = null
        }
    }
}