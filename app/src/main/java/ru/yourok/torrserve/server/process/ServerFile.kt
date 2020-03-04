package ru.yourok.torrserve.serverloader

import com.topjohnwu.superuser.Shell
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.utils.Path
import java.io.File

object ServerFile {
    private val servPath = File(App.getContext().filesDir, "torrserver")
    private var shell: Shell.Job? = null
    private val lock = Any()
    private var error = ""

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
                if (Preferences.isExecRootServer())
                    shell = Shell.su("${servPath.path} -k -d ${Path.getAppPath()} > /sdcard/torrserver.log 2>&1")
                else {
                    val sh = Shell.newInstance("sh")
                    shell = sh.newJob()
                    shell?.add("${servPath.path} -k -d ${Path.getAppPath()} > /sdcard/torrserver.log 2>&1")
                }
                shell?.submit()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (Preferences.isExecRootServer())
                Shell.su("killall -9 ${servPath.path}")
            else
                Shell.sh("killall -9 ${servPath.path}")
        }
    }
}
