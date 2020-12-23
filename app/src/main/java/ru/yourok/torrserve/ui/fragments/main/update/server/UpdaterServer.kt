package ru.yourok.torrserve.ui.fragments.main.update.server

import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.app.Consts
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Net
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object UpdaterServer {
    private var versions: ServVersions? = null
    private var version: ServVersion? = null
    private var error: String = ""

    suspend fun getLocalVersion(): String {
        var version = ""
        var host = ""
        if (TorrService.isLocal()) {
            TorrService.start()
            withContext(Dispatchers.IO) {
                version = Api.echo()
                host = Settings.getHost()
            }
        }
        return "$host $version"
    }

    fun updateFromFile(filePath: String) {
        if (TorrService.isLocal()) {
            TorrService.stop()
        }
        val file = File(filePath)
        if (file.canRead()) {
            val serverFile = ServerFile()
            serverFile.delete()
            val input = FileInputStream(file)
            val output = FileOutputStream(serverFile)
            input.copyTo(output)
            input.close()
            output.flush()
            output.close()
            if (!serverFile.setExecutable(true))
                throw IOException("error set server exec permission")
        }
    }

    fun getArch(): String {
        val arch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            Build.SUPPORTED_ABIS[0]
        else
            Build.CPU_ABI

        when (arch) {
            "arm64-v8a" -> return "arm64"
            "armeabi-v7a" -> return "arm7"
            "x86_64" -> return "amd64"
            "x86" -> return "386"
        }
        return ""
    }

    fun check(): Boolean {
        try {
            val body = Net.get(Consts.updateServerPath)
            val gson = Gson()
            versions = gson.fromJson(body, ServVersions::class.java)
            versions?.let { vers ->
                vers
                    .filter { it.version.startsWith("1.2.") }
                    .sortedBy { it.version.substringAfter("1.2.").toIntOrNull() ?: Int.MAX_VALUE }
                    .also {
                        if (it.isNotEmpty()) {
                            if ((it.first().version.substringAfter("1.2.").toIntOrNull() ?: -1) > BuildConfig.VERSION_CODE) {
                                version = it.first()
                                return true
                            }
                        }
                    }
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            error = e.message ?: ""
            return false
        }
    }

    fun getRemoteVersion(): String {
        if (version == null)
            check()
        return version?.version ?: error
    }

    fun getLink(): String {
        if (version == null)
            check()
        return version?.version ?: ""
    }
}
