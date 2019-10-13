package ru.yourok.torrserve.serverloader

import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.utils.Path
import java.io.File

object ServerFile {
    private val servPath = File(App.getContext().filesDir, "torrserver")
    private var shell: Shell.Job? = null
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
        stop()
        return servPath.delete()
    }

    fun run() {
        if (!serverExists())
            return
        synchronized(lock) {
            if (shell == null) {
                //TODO проверить
                if (Preferences.isExecRootServer())
                    shell = Shell.su("${servPath.path} -k -d ${Path.getAppPath()}")
                else
                    shell = Shell.sh("${servPath.path} -k -d ${Path.getAppPath()}")
                shell?.let {
                    val callbackList = object : CallbackList<String>() {
                        override fun onAddElement(e: String?) {
                            Log.i("GoLog", e)
                        }
                    }

                    it.to(callbackList).submit {
                        Log.i("GoLogErr", it.err.joinToString("\n"))
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            shell?.let {
                it.add("killall -9 ${servPath.path}").submit()
                shell = null
            } ?: let {
                if (Preferences.isExecRootServer())
                    Shell.su("killall -9 ${servPath.path}")
                else
                    Shell.sh("killall -9 ${servPath.path}")
            }
        }
    }
}