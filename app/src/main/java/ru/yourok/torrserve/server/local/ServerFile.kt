package ru.yourok.torrserve.server.local

import com.topjohnwu.superuser.Shell
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
            //Shell.Config.verboseLogging(BuildConfig.DEBUG)
            val setspath = Settings.getTorrPath()
            val logfile = File(setspath, "torrserver.log").path
            if (shell == null) {
                shell = if (Settings.isRootStart() && Shell.rootAccess())
                    Shell.su("$path -k -d $setspath -l $logfile &")
                else
                    Shell.sh("$path -k -d $setspath -l $logfile &")

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
            //Shell.Config.verboseLogging(BuildConfig.DEBUG)
            if (Shell.rootAccess())
                Shell.su("killall -9 torrserver &").exec()
            else
                Shell.sh("killall -9 torrserver &").exec()
            shell = null
        }
    }

}