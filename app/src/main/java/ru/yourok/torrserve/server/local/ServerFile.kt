package ru.yourok.torrserve.server.local

import android.util.Log
import com.topjohnwu.superuser.Shell
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.settings.Settings.useLocalAuth
import java.io.ByteArrayInputStream
import java.io.File

class ServerFile : File(App.context.filesDir, "torrserver") {
    private val lock = Any()
    private var shellJob: Shell.Job? = null
    private val setspath = Settings.getTorrPath()
    private val logfile = File(setspath, "torrserver.log").path
    private val accsFile = File(Settings.getTorrPath(), "accs.db")

    fun run(auth: String = Settings.getServerAuth()) {
        if (!exists())
            return
        synchronized(lock) {
            var akey = ""
            if (useLocalAuth() && auth.isNotBlank() && storeAccs(auth))
                akey = "--httpauth"
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            if (shellJob == null) {
                val shell = if (Settings.isRootStart()) Shell.Builder.create()
                    .build()
                else Shell.Builder.create()
                    .setFlags(Shell.FLAG_NON_ROOT_SHELL)
                    .build()
                shellJob = shell.newJob()
                    .add("export GODEBUG=madvdontneed=1")
                    .add("$path -k --path $setspath --logpath $logfile $akey 1>>$logfile 2>&1 &")
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

    private fun storeAccs(auth: String): Boolean {
        if (auth.isNotBlank() && auth.split(":").size == 2) { // && !accsFile.exists()
            if (BuildConfig.DEBUG) Log.d("*****", "storeAccs() got auth \"$auth\"")
            try {
                // remove stale auth
                if (accsFile.exists() && accsFile.canWrite()) {
                    if (BuildConfig.DEBUG) Log.d("*****", "storeAccs() delete $accsFile")
                    accsFile.delete()
                }
                // create new accs.db
                val user = auth.split(":")[0].trim()
                val pass = auth.split(":")[1].trim()
                if (BuildConfig.DEBUG) Log.d("*****", "storeAccs() save auth \"$user:$pass\" to $accsFile")
                val content = "{\"$user\":\"$pass\"}"
                val inputStream = ByteArrayInputStream(content.toByteArray())
                if (accsFile.createNewFile())
                    inputStream.use { input ->
                        accsFile.outputStream().use { output ->
                            input.copyTo(output)
                            output.flush()
                            output.close()
                        }
                    }
            } catch (e: Exception) {
                return false
            }
            return true
//        } else {
//            if (BuildConfig.DEBUG) Log.d("*****", "storeAccs() empty|bad auth \"$auth\"")
        }
        return false
    }

//    fun md5(input: String): String {
//        val md = MessageDigest.getInstance("MD5")
//        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
//    }
}