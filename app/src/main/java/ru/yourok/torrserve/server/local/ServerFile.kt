package ru.yourok.torrserve.server.local

import com.topjohnwu.superuser.Shell
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
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
            Shell.Config.verboseLogging(BuildConfig.DEBUG)
            val setspath = Settings.getTorrPath()
            val logfile = File(setspath, "torrserver.log").path
            if (shell == null) {
                if (Settings.isRootStart()) {
                    shell = Shell.su("$path -k -d ${setspath} 1>${logfile} 2>&1")
                } else {
                    val sh = Shell.newInstance("sh")
                    shell = sh.newJob()
                    shell?.add("$path -k -d ${setspath} 1>${logfile} 2>&1")
                }
                shell?.add("export GODEBUG=madvdontneed=1")
                if (shell?.exec()!!.isSuccess)
                    App.Toast(App.context.getString(R.string.server_started))
                //TODO проверить
                shell = null
            }
        }
    }

    fun stop() {
        //if (!exists())
        //    return
        synchronized(lock) {
            Shell.Config.verboseLogging(BuildConfig.DEBUG)
            val setspath = Settings.getTorrPath()
            val logfile = File(setspath, "torrserver.log").path
            val result: Shell.Result = if (Settings.isRootStart())
                Shell.su("killall -9 torrserver 1>${logfile} 2>&1").exec()
            else
                Shell.sh("killall -9 torrserver 1>${logfile} 2>&1").exec()
            if (result.isSuccess)
                App.Toast(App.context.getString(R.string.server_stoped))
            //TODO проверить
            shell = null
        }
    }

}