package ru.yourok.torrserve.server.local

import com.topjohnwu.superuser.Shell
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
            val shell = if (Settings.isRootStart()) Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .build()
            else Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR or Shell.FLAG_NON_ROOT_SHELL)
                .build()
            if (shellJob == null) {
                App.closeShell()
                shellJob = shell.newJob()
                    .add("$path -k -d $setspath -l $logfile 1>>$logfile 2>&1 &")
                    .add("export GODEBUG=madvdontneed=1")
                shellJob?.run {
                    exec()
                }
                shell.close()
            }
        }
    }

    fun stop() {
        if (!exists())
            return
        synchronized(lock) {
            Shell.cmd("killall -9 torrserver 1>>$logfile 2>/dev/null &").exec()
            App.closeShell()
            shellJob = null
        }
    }
}