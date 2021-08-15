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
    private val setspath = Settings.getTorrPath()
    private val logfile = File(setspath, "torrserver.log").path

    fun run() {
        if (!exists())
            return
        synchronized(lock) {
            Shell.Config.verboseLogging(BuildConfig.DEBUG)
            if (shell == null) {
                shell = if (Settings.isRootStart() && Shell.rootAccess())
                    Shell.su("$path -k -d $setspath -l $logfile 1>>$logfile 2>&1 &")
                else
                    Shell.sh("$path -k -d $setspath -l $logfile 1>>$logfile 2>&1 &")

                shell?.add("export GODEBUG=madvdontneed=1")
                if (shell?.exec()!!.isSuccess)
                    App.Toast(App.context.getString(R.string.server_started))
            }
        }
    }

    fun stop() {
        if (!exists())
            return
        synchronized(lock) {
            Shell.Config.verboseLogging(BuildConfig.DEBUG)
            if (Shell.rootAccess())
                Shell.su("killall -9 torrserver 1>>$logfile 2>&1 &").exec()
            else
                Shell.sh("killall -9 torrserver 1>>$logfile 2>&1 &").exec()
            shell = null
        }
    }

}