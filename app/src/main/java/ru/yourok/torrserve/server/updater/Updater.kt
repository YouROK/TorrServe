package ru.yourok.torrserve.serverloader

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Environment
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.activitys.updater.UpdaterActivity
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.services.ServerService
import ru.yourok.torrserve.utils.Http
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

object Updater {
    private var serverJS: JSONObject = JSONObject()
    private var apkJS: JSONObject = JSONObject()
    private var currServerVersion: String = ""

    private var lastCheckServer: Long = 0
    private var lastCheckApk: Long = 0

    private val apkRelease = "https://raw.githubusercontent.com/YouROK/TorrServe/master/release.json"
    private val serverRelease = "https://raw.githubusercontent.com/YouROK/TorrServer/master/release.json"

    fun checkLocalVersion() {
        if (!ServerFile.serverExists())
            throw IOException(App.getContext().getString(R.string.server_not_exists))
        try {
            currServerVersion = Api.serverEcho()
        } catch (e: Exception) {
            ServerService.start()
            Thread.sleep(1000)
            currServerVersion = Api.serverEcho()
        }
    }

    fun getJson(server: Boolean): JSONObject {
        if (server) {
            return serverJS
        } else
            return apkJS
    }

    fun getCurrVersion(): String {
        return currServerVersion
    }

    fun getRemoteJS(url: String): JSONObject? {
        val strJS = Http(url).read()
        if (strJS.isNotEmpty()) {
            return JSONObject(strJS)
        }
        return null
    }

    fun checkRemoteServer(): Boolean {
        if (System.currentTimeMillis() - lastCheckServer < 60000)
            return true

        val url = serverRelease

        val js = getRemoteJS(url)
        js?.let {
            serverJS = it
            lastCheckServer = System.currentTimeMillis()
            return true
        }
        return false
    }

    fun checkRemoteApk(): Boolean {
        if (System.currentTimeMillis() - lastCheckApk < 60000)
            return true

        val url = apkRelease

        val js = getRemoteJS(url)
        js?.let {
            apkJS = it
            lastCheckApk = System.currentTimeMillis()
            return true
        }
        return false
    }

    fun updateApkRemote(onProgress: ((prc: Int) -> Unit)?, onFinish: ((file: File?) -> Unit)?) {
        if (!checkRemoteApk())
            throw IOException("error check remote version")

        val url = getJson(false).getString("Link")
        val http = Http(url)
        http.getEntity().apply {
            this ?: throw IOException("error get apk: $url")
            content ?: throw IOException("error get apk: $url")

            val apkFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "TorrServe.apk")

            apkFile.delete()

            FileOutputStream(apkFile).use { fileOut ->
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
                onFinish?.invoke(apkFile)
                return
            }
        }
        onFinish?.invoke(null)
    }

    fun updateServerRemote(onProgress: ((prc: Int) -> Unit)?) {
        val arch = getArch()
        if (arch.isEmpty())
            throw IOException("error get arch")

        if (!checkRemoteServer())
            throw IOException("error check remote version")

        val url: String
        url = serverJS.getJSONObject("Links").getString("android-${arch}")

        val http = Http(url)
        http.getEntity().apply {
            this ?: throw IOException("error get server: $url")
            content ?: throw IOException("error get server: $url")

            ServerFile.deleteServer()
            FileOutputStream(ServerFile.get()).use { fileOut ->
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
                if (!ServerFile.get().setExecutable(true))
                    throw IOException("error set exec permission")
            }
        }
        ServerService.start()
    }

    fun updateServerLocal(filePath: String) {
        val file = File(filePath)
        if (file.canRead()) {
            ServerFile.deleteServer()
            val input = FileInputStream(file)
            val output = FileOutputStream(ServerFile.get())
            input.copyTo(output)
            input.close()
            output.flush()
            output.close()
            if (!ServerFile.get().setExecutable(true))
                throw IOException("error set exec permission")
            ServerService.start()
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

    fun check(onFound: (apkJS: JSObject) -> Unit) {
        var found = false

        thread {
            try {
                val js = getRemoteJS(apkRelease)
                js?.let {
                    apkJS = it
                    found = true
                }
            } catch (e: Exception) {
            }
        }.join()
        if (found)
            onFound(JSObject(apkJS))
    }

    fun show(activity: Activity) {
        thread {
            Thread.sleep(5000)
            check { apkJS ->
                var isShow = false

                val remoteApk = apkJS.getString("Version", "")
                if (remoteApk.isNotEmpty() && BuildConfig.VERSION_NAME != remoteApk)
                    isShow = true

                if (isShow) {
                    val snackbar = Snackbar.make(activity.findViewById(R.id.content), R.string.found_new_version, Snackbar.LENGTH_LONG)
                    snackbar.setAction(android.R.string.ok) {
                        activity.startActivity(Intent(activity, UpdaterActivity::class.java))
                    }.show()
                }
            }
        }
    }
}