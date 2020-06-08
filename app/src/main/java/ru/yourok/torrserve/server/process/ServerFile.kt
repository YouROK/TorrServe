package ru.yourok.torrserve.serverloader

import com.topjohnwu.superuser.Shell
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.server.api.Api
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
                val path = Path.getAppPath()
                if (Preferences.isExecRootServer())
                    shell = Shell.su("${servPath.path} -k -d ${Path.getAppPath()} > ${path}/torrserver.log 2>&1")
                else {
                    val sh = Shell.newInstance("sh")
                    shell = sh.newJob()
                    shell?.add("${servPath.path} -k -d ${Path.getAppPath()} > ${path}/torrserver.log 2>&1")
                }
                shell?.submit()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (Api.serverIsLocal() && serverExists())
                Api.serverShutdown()
            shell = null
        }
    }
}
