package ru.yourok.torrserve.serverloader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.design.widget.Snackbar
import org.json.JSONObject
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.activitys.updater.UpdaterActivity
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.utils.Http
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

object Updater {
    private var serverJS: JSONObject = JSONObject()
    private var apkJS: JSONObject = JSONObject()
    private var testJS: JSONObject = JSONObject()
    private var currServerVersion: String = ""

    private var lastCheckServer: Long = 0
    private var lastCheckApk: Long = 0

    private val apkRelease = "https://raw.githubusercontent.com/YouROK/TorrServe/master/release.json"
    private val serverRelease = "https://raw.githubusercontent.com/YouROK/TorrServer/master/release.json"
    private val serverTest = "https://raw.githubusercontent.com/YouROK/TorrServer/master/test.json"

    fun checkLocalVersion() {
        if (!ServerFile.serverExists())
            throw IOException(App.getContext().getString(R.string.server_not_exists))
        try {
            currServerVersion = Api.serverEcho()
        } catch (e: Exception) {
            ServerFile.run()
            Thread.sleep(1000)
            currServerVersion = Api.serverEcho()
        }
    }

    fun getJson(server: Boolean, test: Boolean = false): JSONObject {
        if (server) {
            if (!test)
                return serverJS
            else
                return testJS
        } else
            return apkJS
    }

    fun getCurrVersion(): String {
        return currServerVersion
    }

    fun getRemoteJS(url: String): JSONObject? {
        val http = Http(Uri.parse(url))
        http.connect()
        val strJS = http.getInputStream()?.bufferedReader()?.readText() ?: ""
        http.close()
        if (strJS.isNotEmpty()) {
            return JSONObject(strJS)
        }
        return null
    }

    fun checkRemoteServer(test: Boolean = false): Boolean {
        if (System.currentTimeMillis() - lastCheckServer < 60000)
            return true

        var url = serverRelease
        if (test)
            url = serverTest

        val js = getRemoteJS(url)
        js?.let {
            if (!test)
                serverJS = it
            else
                testJS = it
            lastCheckServer = System.currentTimeMillis()
            return true
        }
        return false
    }

    fun checkRemoteApk(): Boolean {
        if (System.currentTimeMillis() - lastCheckApk < 60000)
            return true

        var url = apkRelease

        val js = getRemoteJS(url)
        js?.let {
            apkJS = it
            lastCheckApk = System.currentTimeMillis()
            return true
        }
        return false
    }

    fun updateServerRemote(test: Boolean = false, onProgress: ((prc: Int) -> Unit)?) {
        val arch = getArch()
        if (arch.isEmpty())
            throw IOException("error get arch")

        if (!checkRemoteServer(test))
            throw IOException("error check remote version")

        val url: String
        if (!test)
            url = serverJS.getJSONObject("Links").getString("android-${arch}")
        else
            url = testJS.getJSONObject("Links").getString("android-${arch}")

        val http = Http(Uri.parse(url))
        http.connect()
        http.getInputStream().use { input ->
            input ?: let { throw IOException("error get server: $url") }
            ServerFile.deleteServer()
            FileOutputStream(ServerFile.get()).use { fileOut ->
                if (onProgress == null)
                    input.copyTo(fileOut)
                else {
                    val buffer = ByteArray(65535)
                    val length = http.getSize()
                    var offset: Long = 0
                    while (true) {
                        val readed = input.read(buffer)
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
        ServerFile.run()
    }

    fun updateServerLocal(filePath: String) {
        val file = File(filePath)
        if (file.canRead()) {
            ServerFile.deleteServer()
            val input = FileInputStream(file)
            input.copyTo(FileOutputStream(ServerFile.get()))
            input.close()
            if (!ServerFile.get().setExecutable(true))
                throw IOException("error set exec permission")
            ServerFile.run()
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

    fun check(onFound: (apkJS: JSObject, serverJS: JSObject) -> Unit) {
        var found = false

        val th1 = thread {
            try {
                val js = getRemoteJS(apkRelease)
                js?.let {
                    apkJS = it
                    found = true
                }
            } catch (e: Exception) {
            }
        }

        val th2 = thread {
            try {
                val js = getRemoteJS(serverRelease)
                js?.let {
                    serverJS = it
                    found = true
                }
            } catch (e: Exception) {
            }
        }

        th1.join()
        th2.join()
        if (found)
            onFound(JSObject(apkJS), JSObject(serverJS))
    }

    fun show(activity: Activity) {
        thread {
            Thread.sleep(5000)
            check { apkJS, serverJS ->
                var isShow = false

                val remoteApk = apkJS.getString("Version", "")
                if (remoteApk.isNotEmpty() && BuildConfig.VERSION_NAME != remoteApk)
                    isShow = true

                if (Api.serverIsLocal()) {
                    var remoteServer = serverJS.getString("Version", "")
                    if (remoteServer.isNotEmpty()) {
                        try {
                            checkLocalVersion()
                        } catch (e: Exception) {
                        }
                        if (currServerVersion != remoteServer)
                            isShow = true
                    }
                }

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