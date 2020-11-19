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
            if (shell == null) {
                if (Settings.isRootStart()) {
                    shell = Shell.su("${path} -k -d ${Settings.getTorrPath()} > ${path}.log 2>&1")
                } else {
                    val sh = Shell.newInstance("sh")
                    shell = sh.newJob()
                    shell?.add("${path} -k -d ${Settings.getTorrPath()} > ${path}.log 2>&1")
                }
                shell?.add("export GODEBUG=madvdontneed=1")
                shell?.submit()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (Settings.isRootStart())
                Shell.su("killall -9 torrserver")
            else
                Shell.sh("killall -9 torrserver")
            //TODO проверить
            shell = null
        }
    }
}