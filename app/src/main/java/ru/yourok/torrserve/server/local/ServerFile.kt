package ru.yourok.torrserve.server.local

import com.topjohnwu.superuser.Shell
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.settings.Settings
import java.io.File

class ServerFile : File(App.context.filesDir, "torrserver") {
    private var shellJob: Shell.Job? = null
    private val lock = Any()
    private val setspath = Settings.getTorrPath()
    private val logfile = File(setspath, "torrserver.log").path

    fun run() {
        if (!exists())
            return
        synchronized(lock) {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            if (shellJob == null) {
                val shell = if (Settings.isRootStart()) Shell.Builder.create()
                    .build()
                else Shell.Builder.create()
                    .setFlags(Shell.FLAG_NON_ROOT_SHELL)
                    .build()
                shellJob = shell.newJob()
                    .add("export GODEBUG=madvdontneed=1")
                    .add("$path -k -d $setspath -l $logfile 1>>$logfile 2>&1 &")
                shellJob?.exec()
            }
        }
    }

    fun stop() {
        if (!exists())
            return
        synchronized(lock) {
            // do nothing with -k startup switch?
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            if (Shell.rootAccess())
                Shell.su("killall -9 torrserver").exec()
            else
                Shell.sh("killall -9 torrserver").exec()
            shellJob = null
        }
    }

}