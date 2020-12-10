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

    fun stop() : Boolean {
        if (!exists())
            return false
        synchronized(lock) {
            Shell.Config.verboseLogging(true)
            var result: Boolean
            if (Settings.isRootStart())
                result = Shell.su("killall -9 torrserver > ${path}.log 2>&1").exec().isSuccess
            else
                result = Shell.sh("killall -9 torrserver > ${path}.log 2>&1").exec().isSuccess
            //TODO проверить
            shell = null
            return result
        }
    }

    fun version(): String {
        if (exists()) {
            val sh = Shell.newInstance("sh")
            sh.newJob().also { shell ->
                shell.add("${path} --version")
                shell.exec().also { res ->
                    if (res.isSuccess || res.out.isNotEmpty())
                        return res.out.first()
                }
            }
        }
        return ""
    }
}