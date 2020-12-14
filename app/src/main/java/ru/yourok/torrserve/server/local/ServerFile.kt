package ru.yourok.torrserve.server.local

import com.topjohnwu.superuser.Shell
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.settings.Settings
import java.io.File

class ServerFile : File(App.context.filesDir, "torrserver") {
    private var shell: Shell.Job? = null
    private val lock = Any()

    fun run() {
        if (!exists())
            return
        synchronized(lock) {
            Shell.Config.verboseLogging(true)
            val setspath = Settings.getTorrPath()
            if (shell == null) {
                if (Settings.isRootStart()) {
                    shell = Shell.su("${path} -k -d ${setspath} > ${File(setspath, "torrserver.log").path} 2>&1")
                } else {
                    val sh = Shell.newInstance("sh")
                    shell = sh.newJob()
                    shell?.add("${path} -k -d ${setspath} > ${File(setspath, "torrserver.log").path} 2>&1")
                }
                shell?.add("export GODEBUG=madvdontneed=1")
                shell?.submit()
            }
        }
    }

    fun stop(): Boolean {
        if (!exists())
            return false
        synchronized(lock) {
            Shell.Config.verboseLogging(true)
            val setspath = Settings.getTorrPath()
            val result: Boolean
            if (Settings.isRootStart())
                result = Shell.su("killall -9 torrserver > ${File(setspath, "torrserver.log").path} 2>&1").exec().isSuccess
            else
                result = Shell.sh("killall -9 torrserver > ${File(setspath, "torrserver.log").path} 2>&1").exec().isSuccess
            //TODO проверить
            shell = null
            return result
        }
    }
}