package ru.yourok.torrserve.ui.fragments.main.update.server

import android.net.Uri
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.app.Consts
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.utils.Http
import ru.yourok.torrserve.utils.Net
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object UpdaterServer {
    private var version: ServVersion? = null
    private var error: String = ""
    private val serverFile = ServerFile()

    suspend fun getLocalVersion(): String {
        var version: String
        if (TorrService.isLocal()) {
            if (!serverFile.exists()) {
                version = App.context.getString(R.string.not_installed)
            } else {
                TorrService.start()
                withContext(Dispatchers.IO) {
                    version = Api.echo()
                }
            }
        } else {
            version = App.context.getString(R.string.not_used)
        }
        return version
    }

    fun updateFromNet(onProgress: ((prc: Int) -> Unit)?) {
        val url = getLink()
        val http = Http(Uri.parse(url))
        http.connect()
        if (TorrService.isLocal()) {
            TorrService.stop()
        }
        http.getInputStream().also { content ->
            content ?: throw IOException("error connect server, url: $url")

            val serverFile = ServerFile()
            val contentLength = http.getSize()

            serverFile.delete()
            FileOutputStream(serverFile).use { fileOut ->
                if (onProgress == null)
                    content.copyTo(fileOut)
                else {
                    val buffer = ByteArray(65535)
                    val length = contentLength + 1
                    var offset: Long = 0
                    while (true) {
                        val readed = content.read(buffer)
                        offset += readed
                        val prc = (offset * 100 / length).toInt()
                        onProgress(prc)
                        if (readed <= 0)
                            break
                        fileOut.write(buffer, 0, readed)
                    }
                    fileOut.flush()
                }
                fileOut.flush()
                fileOut.close()
                if (!serverFile.setExecutable(true))
                    throw IOException("error set exec permission")
            }
        }
        if (TorrService.isLocal()) {
            TorrService.start()
        }
        downloadFFProbe()
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
        if (TorrService.isLocal()) {
            TorrService.start()
        }
    }

    @Suppress("DEPRECATION")
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
        return try {
            val body = Net.get(Consts.updateServerPath)
            val gson = Gson()
            version = gson.fromJson(body, ServVersion::class.java)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            error = e.message ?: ""
            false
        }
    }

    fun downloadFFProbe() {
        val fileZip = File(App.context.filesDir, "ffprobe.zip")
        val file = File(App.context.filesDir, "ffprobe")

        if (file.exists())
            return

        val arch = getArch()
        var link = ""
        when (arch) {
            "arm7" -> link = "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffprobe-4.4.1-linux-armhf-32.zip"
            "arm64" -> link = "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffprobe-4.4.1-linux-arm-64.zip"
            "386" -> link = "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffprobe-4.4.1-linux-32.zip"
            "amd64" -> link = "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffprobe-4.4.1-linux-64.zip"
        }

        val http = Http(Uri.parse(link))
        http.connect()
        http.getInputStream().also { content ->
            content ?: let {
                fileZip.delete()
                file.delete()
                throw IOException("error connect server, url: $link")
            }
            FileOutputStream(fileZip).use { fileOut ->
                content.copyTo(fileOut)
                fileOut.flush()
                fileOut.close()

                fileZip.unzip(App.context.filesDir)
//                unzip(fileZip, App.context.filesDir)

                fileZip.delete()
                if (!file.setExecutable(true)) {
                    file.delete()
                    throw IOException("error set exec permission")
                }
            }
        }
    }

    fun getRemoteVersion(): String {
        if (version == null)
            check()
        return version?.version ?: error
    }

    private fun getLink(): String {
        if (version == null)
            check()
        if (version == null)
            return ""
        version?.let { ver ->
            val arch = getArch()
            if (arch.isEmpty())
                throw IOException("error get arch")
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                ver.links["linux-$arch"] ?: ""
            else
                ver.links["android-$arch"] ?: ""
        }
        return ""
    }

    data class ZipIO(val entry: ZipEntry, val output: File)

    fun File.unzip(unzipLocationRoot: File? = null) {

        val rootFolder = unzipLocationRoot ?: File(parentFile.absolutePath + File.separator + nameWithoutExtension)
        if (!rootFolder.exists()) {
            rootFolder.mkdirs()
        }

        ZipFile(this).use { zip ->
            zip
                .entries()
                .asSequence()
                .map {
                    val outputFile = File(rootFolder.absolutePath + File.separator + it.name)
                    ZipIO(it, outputFile)
                }
                .map {
                    it.output.parentFile?.run {
                        if (!exists()) mkdirs()
                    }
                    it
                }
                .filter { !it.entry.isDirectory }
                .forEach { (entry, output) ->
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }

    }
}
