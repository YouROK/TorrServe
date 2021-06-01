package ru.yourok.torrserve.ui.fragments.main.update.apk

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.Spanned
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.app.Consts
import ru.yourok.torrserve.utils.Http
import ru.yourok.torrserve.utils.Net
import java.io.File
import java.io.FileOutputStream

object UpdaterApk {
    private var versions: ApkVersions? = null
    private var newVersion: ApkVersion? = null

    fun check(): Boolean {
        try {
            val body = Net.get(Consts.updateApkPath)
            val gson = Gson()
            versions = gson.fromJson(body, ApkVersions::class.java)
            versions?.let {
                it.forEach { ver ->
                    if (ver.versionInt > BuildConfig.VERSION_CODE) {
                        newVersion = ver
                        return true
                    }
                }
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getVersion(): String {
        if (newVersion == null)
            check()
        return newVersion?.version ?: ""
    }

    fun getOverview(): Spanned {
        var ret = ""

        versions?.forEach { ver ->
            if (ver.versionInt > BuildConfig.VERSION_CODE) {
                ret += "<font color='white'><b>${ver.version}</b></font> <br>"
                ret += "<i>${ver.desc.replace("\n", "<br>")}</i><br><br>"
            } else {
                ret += "${ver.version}<br>"
                ret += "<i>${ver.desc.replace("\n", "<br>")}</i><br><br>"
            }
        }
        return HtmlCompat.fromHtml(ret.trim(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private val download = Any()

    private fun downloadApk(file: File, onProgress: ((prc: Int) -> Unit)?) {
        synchronized(download) {
            newVersion?.let { ver ->
                try {
                    if (file.exists())
                        file.delete()
                    val conn = Http(Uri.parse(ver.link))
                    conn.connect()
                    conn.getInputStream().use { input ->
                        FileOutputStream(file).use { fileOut ->
                            val contentLength = conn.getSize()
                            if (onProgress == null)
                                input?.copyTo(fileOut)
                            else {
                                val buffer = ByteArray(65535)
                                val length = contentLength + 1
                                var offset: Long = 0
                                while (true) {
                                    val readed = input?.read(buffer) ?: 0
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
                        }
                    }
                    conn.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun installNewVersion(onProgress: ((prc: Int) -> Unit)?) {
        if (newVersion == null && !check())
            return

        newVersion?.let {
            val destination = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TorrServe.apk"
            ).apply {
                mkdirs()
                deleteOnExit()
            }
            downloadApk(destination, onProgress)
            if (destination.exists()) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                    val uri = Uri.fromFile(destination)
                    val install = Intent(Intent.ACTION_VIEW)
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    install.setDataAndType(uri, "application/vnd.android.package-archive")
                    if (install.resolveActivity(App.context.packageManager) != null)
                        App.context.startActivity(install)
                    else
                        App.Toast(R.string.error_app_not_found)
                } else {
                    val fileUri =
                        FileProvider.getUriForFile(
                            App.context,
                            BuildConfig.APPLICATION_ID + ".provider",
                            destination
                        )
                    val install = Intent(Intent.ACTION_VIEW, fileUri)
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (install.resolveActivity(App.context.packageManager) != null)
                        App.context.startActivity(install)
                    else
                        App.Toast(R.string.error_app_not_found)
                }
            } else {
                App.Toast(R.string.error_retrieve_data)
            }
        }
    }
}