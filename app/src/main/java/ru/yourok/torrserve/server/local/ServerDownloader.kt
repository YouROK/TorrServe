package ru.yourok.torrserve.server.local

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Скачивание бинарника Silo с сайта по архитектуре устройства.
 * Файл кладётся в filesDir как `silo` и получает права на исполнение (как в старом клиенте).
 * Источник: SERVER_BASE_URL (сейчас yourok.ru/share, позже GitHub release).
 */
object ServerDownloader {

    /** Базовый адрес, откуда берётся бинарник. Меняется при переезде на GitHub. */
    const val SERVER_BASE_URL = "https://yourok.ru/share"

    /** Суффикс файла по архитектуре устройства. */
    fun archSuffix(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a", "armeabi" -> "arm7"
            "x86_64" -> "amd64"
            "x86" -> "386"
            else -> "arm7"
        }
    }

    /** Имя файла на сервере для текущей архитектуры. */
    fun remoteName(): String = "TorrServer-android-${archSuffix()}"

    /**
     * Скачивает бинарник в filesDir/silo. [onProgress] получает проценты 0..100.
     * Бросает исключение при ошибке; при успехе файл готов к запуску.
     */
    fun download(context: Context, onProgress: (Int) -> Unit = {}) {
        val url = URL("$SERVER_BASE_URL/${remoteName()}")
        val target = File(context.filesDir, ServerFile.BIN_NAME)
        val tmp = File(context.filesDir, "${ServerFile.BIN_NAME}.part")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IllegalStateException("cannot move downloaded file")
            if (!target.setExecutable(true, true)) throw IllegalStateException("cannot set exec permission")
            onProgress(100)
        } finally {
            conn.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}
